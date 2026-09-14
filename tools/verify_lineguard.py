"""复用文档技能的逐页渲染流程，检查本任务 Word 导出的页面。"""

from pathlib import Path
import importlib.util
import json
import re
from zipfile import ZipFile

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / ".lineguard_qa"
DOCX = ROOT / "LineGuard_项目计划书_V1.1.docx"
PDF = QA / "LineGuard_项目计划书_V1.1.pdf"
RENDERER_ROOT = Path("C:/Users/16638/.codex/plugins/cache/openai-primary-runtime/documents")
SKILL_RENDERER = sorted(RENDERER_ROOT.glob("*/skills/documents/render_docx.py"))[-1]

# Windows 运行时未提供 LibreOffice，保留技能的 PNG 生成流程，
# 仅将 DOCX 转换步骤替换为同一文档的 Word 原生导出结果。
spec = importlib.util.spec_from_file_location("lineguard_skill_renderer", SKILL_RENDERER)
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


def from_word(*args, **kwargs):
    if not PDF.is_file():
        raise FileNotFoundError(PDF)
    return str(PDF), "PDF exported with Microsoft Word in a dedicated hidden instance."


renderer.convert_to_pdf = from_word
paths = renderer.rasterize(str(DOCX), str(QA / "pages"), 120, False, False)
reader = PdfReader(PDF)
summaries = []
for number, page in enumerate(reader.pages, 1):
    text = page.extract_text() or ""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    summaries.append({
        "page": number,
        "characters": len(text),
        "first_lines": lines[:3],
        "last_lines": lines[-3:],
    })

source = (ROOT / "LineGuard_项目计划书_V1.0.md").read_text(encoding="utf-8")
main = source.split("# 1 项目摘要", 1)[1].split("# 附录 A", 1)[0]
abstract = main.split("# 2 项目问题与目标", 1)[0]
one_sentence = source.split("# 附录 A 项目一句话介绍", 1)[1].split("# 附录 B", 1)[0].strip()
demo = source.split("# 附录 B 核心闭环演示脚本", 1)[1].split("# 附录 C", 1)[0].strip()
with ZipFile(DOCX) as archive:
    xml = archive.read("word/document.xml").decode("utf-8")
    rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")
    checks = {
        "main_chinese_characters": len(re.findall(r"[\u4e00-\u9fff]", main)),
        "abstract_chinese_characters": len(re.findall(r"[\u4e00-\u9fff]", abstract)),
        "one_sentence_characters": len(one_sentence),
        "demo_characters": len(demo),
        "main_chapters": len(re.findall(r"^# \d+ ", source, re.M)),
        "tables": xml.count("<w:tbl>"),
        "pages": len(reader.pages),
        "page_pngs": len(paths),
        "external_links": rels.count('TargetMode="External"'),
        "has_internal_citation_tokens": "" in source or "" in xml,
        "has_mermaid_source": "```mermaid" in source,
    }

(QA / "verification.json").write_text(json.dumps({"checks": checks, "pages": summaries}, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(checks, ensure_ascii=False))
for item in summaries:
    print(json.dumps(item, ensure_ascii=False))
