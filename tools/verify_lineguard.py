"""校验《LineGuard 项目计划书》的结构与内容指标。

docx 与 md 是必读输入，只依赖标准库即可运行；PDF 仅用于页数预算与逐页文本，
存在时才检查。默认只对仓库卫生类指标强制通过，内容类指标以 --strict 强制。
"""

from pathlib import Path
import hashlib
import json
import re
import sys
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / ".lineguard_qa"
PAGES = QA / "pages"
PAGE_BUDGET = 15
MODEL_DIR = ROOT / "tools" / "blender"
PARTS_JSON = ROOT / "lineguard-system" / "frontend" / "src" / "model" / "lineguard-parts.json"


def parts_fresh() -> bool:
    """演示三维场景的构件表是否与建模源码同步。

    模型改了却没重跑 tools/blender/export_parts.py 时，文档插图会变、演示还是旧的，
    这正是最容易漏掉的一类不一致。这里用源码指纹比对，不需要为了校验再装 Blender。
    """
    if not PARTS_JSON.is_file():
        return False
    recorded = json.loads(PARTS_JSON.read_text(encoding="utf-8")).get("_source_hash")
    digest = hashlib.sha256()
    for name in ("lineguard_dimensions.py", "lineguard_model.py"):
        digest.update((MODEL_DIR / name).read_bytes())
    return recorded == digest.hexdigest()[:16]

# 防御性边界句式：密度过高会让文档读起来像安全对齐反射，压低项目分量。
DEFENSIVE_PATTERNS = [
    r"不替代", r"不追求", r"不作为", r"不开放", r"不进入", r"不包含",
    r"不改变", r"不影响", r"不接受", r"不承担", r"不与", r"不在第一阶段",
]


def newest(pattern: str) -> Path:
    matches = sorted(ROOT.glob(pattern))
    if not matches:
        raise SystemExit(f"未找到匹配 {pattern} 的文件")
    return matches[-1]


def read_source(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def rasterize(pages_dir: Path, pdf: Path) -> int:
    """PDF 存在时重绘逐页 PNG；每次先清空，避免陈旧页面残留。"""
    if not pdf.is_file():
        return 0
    try:
        from pdf2image import convert_from_path
    except ImportError:
        return 0
    for stale in pages_dir.glob("page-*.png"):
        stale.unlink()
    pages_dir.mkdir(parents=True, exist_ok=True)
    images = convert_from_path(str(pdf), dpi=120)
    for number, image in enumerate(images, 1):
        image.save(pages_dir / f"page-{number}.png")
    return len(images)


def page_summaries(pdf: Path) -> list[dict]:
    if not pdf.is_file():
        return []
    try:
        from pypdf import PdfReader
    except ImportError:
        return []
    summaries = []
    for number, page in enumerate(PdfReader(str(pdf)).pages, 1):
        text = page.extract_text() or ""
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        summaries.append({
            "page": number,
            "characters": len(text),
            "first_lines": lines[:3],
            "last_lines": lines[-3:],
        })
    return summaries


def estimate_pages(docx_path: Path) -> int:
    """按构建脚本的排版参数累计高度并折算页数，用于守住 15 页上限。

    无 Office 环境无法取得真实分页，这里复算版式：断行、避免孤行以及
    表格整行不跨页造成的留白用一个经验系数吸收，系数由已知 15 页的
    历史版本校准（该版本估算结果应为 15）。
    """
    from docx import Document
    from docx.oxml.ns import qn
    from docx.table import Table
    from docx.text.paragraph import Paragraph

    doc = Document(str(docx_path))
    usable_height = (11 - 0.75 - 0.7) * 72
    usable_width = (8.5 - 0.85 * 2) * 72
    chars_per_line = usable_width / 11
    calibration = 1.12

    def effective(text: str) -> float:
        cjk = len(re.findall(r"[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]", text))
        return cjk + (len(text) - cjk) * 0.5

    def paragraph_height(paragraph) -> float:
        style = paragraph.style.name if paragraph.style else "Normal"
        if style == "Heading 1":
            return 15 + 17 * 1.15 + 7
        if style == "Heading 2":
            return 11 + 13 * 1.15 + 7
        if style in ("Title", "Subtitle"):
            return 34
        if style == "Caption":
            return 9 * 1.35 + 10
        lines = max(1, -(-int(effective(paragraph.text)) // int(chars_per_line)))
        return lines * 11 * 1.35 + 6

    def table_height(table) -> float:
        total = 0.0
        for row in table.rows:
            cell_lines = 1
            for cell in row.cells:
                width = cell.width.inches * 72 if cell.width else usable_width / len(row.cells)
                per_line = max(6.0, width / 10)
                cell_lines = max(cell_lines, -(-int(effective(cell.text)) // int(per_line)))
            total += cell_lines * 10 * 1.18 + 9.5
        return total + 4

    def image_height(paragraph) -> float:
        extents = paragraph._p.findall(".//" + qn("wp:extent"))
        if not extents:
            return 0.0
        return max(int(node.get("cy")) for node in extents) / 914400 * 72

    content, breaks = 0.0, 0
    for child in doc.element.body.iterchildren():
        if child.tag == qn("w:p"):
            paragraph = Paragraph(child, doc)
            if child.findall(".//" + qn("w:br") + "[@" + qn("w:type") + "='page']"):
                breaks += 1
                continue
            content += image_height(paragraph)
            if paragraph.text.strip():
                content += paragraph_height(paragraph)
        elif child.tag == qn("w:tbl"):
            content += table_height(Table(child, doc))
    pages = breaks + content / usable_height * calibration
    return int(-(-pages // 1))


def main() -> int:
    strict = "--strict" in sys.argv
    source_path = newest("LineGuard_项目计划书_*.md")
    docx_path = newest("LineGuard_项目计划书_*.docx")
    pdf_path = QA / f"{docx_path.stem}.pdf"

    source = read_source(source_path)
    with ZipFile(docx_path) as archive:
        xml = archive.read("word/document.xml").decode("utf-8")
        rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")

    summaries = page_summaries(pdf_path)
    rendered = rasterize(PAGES, pdf_path)
    defensive = sum(len(re.findall(pattern, source)) for pattern in DEFENSIVE_PATTERNS)
    total_pages = estimate_pages(docx_path)
    # 前三项与页数是交付底线，默认强制；其余是文档内容目标，随 --strict 强制。
    checks = [
        ("占位与引用标记", len(re.findall(r"[\ue000-\uf8ff]", source + xml)), "==", 0, True),
        ("mermaid 图源", "```mermaid" in source, "==", True, True),
        ("正文汉字数", len(re.findall(r"[\u4e00-\u9fff]", source)), ">=", 5000, True),
        ("有效内容页数", total_pages - 2, "<=", PAGE_BUDGET, True),
        ("总页数（含封面目录）", total_pages, "<=", PAGE_BUDGET + 2, True),
        ("docx 表格数", xml.count("<w:tbl>"), "<=", 8, strict),
        ("docx 插图数", xml.count("<w:drawing>"), ">=", 6, strict),
        ("外部引用数", rels.count('TargetMode="External"'), ">=", 2, strict),
        # 防的是"堆砌式"边界表述回潮，不是禁止一切边界声明。当前 7 处分别落在：
        # 范围界定（不在第一阶段范围内）、角色界定（不替代灾后巡视与专业检修）、
        # 运行包线（不进入雷暴强风覆冰）、数据口径（不作为全国统计口径）、
        # 阶段原则（不改变已验证闭环）、免责声明（不作为采购或投资依据）——
        # 都是有信息量的必要声明，所以这个数不会再低，别为压数字把它们删掉。
        ("防御性句式密度", defensive, "<=", 8, strict),
        ("演示模型与建模源码同源", parts_fresh(), "==", True, True),
    ]
    if summaries:
        checks.append(("导出页数", len(summaries), "<=", PAGE_BUDGET, strict))

    failures = []
    print(f"源文件 {source_path.name} / 成品 {docx_path.name}")
    for name, value, operator, threshold, enforced in checks:
        ok = {
            ">=": value >= threshold,
            "<=": value <= threshold,
            "==": value == threshold,
        }[operator]
        mark = "PASS" if ok else ("FAIL" if enforced else "WARN")
        if not ok and enforced:
            failures.append(name)
        print(f"  [{mark}] {name}: {value} (目标 {operator} {threshold})")
    if rendered:
        print(f"  [PASS] 逐页 PNG: {rendered} 张")
    elif pdf_path.is_file():
        print("  [WARN] 逐页 PNG 未生成：需要 pdf2image 与 poppler")
    else:
        print(f"  [WARN] 页数预算未检查：缺少 {pdf_path.name}")

    (QA / "verification.json").write_text(
        json.dumps({
            "source": source_path.name,
            "document": docx_path.name,
            "checks": {name: value for name, value, *_ in checks},
            "failed": failures,
            "pages": summaries,
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("结果：" + ("通过" if not failures else "未通过 " + "、".join(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
