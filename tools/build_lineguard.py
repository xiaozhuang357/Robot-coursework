"""从计划书正文生成可编辑 Word 文档与架构图。"""

from pathlib import Path
import re

from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "LineGuard_项目计划书_V1.0.md"
DEST = ROOT / "LineGuard_项目计划书_V1.1.docx"
ASSETS = ROOT / ".lineguard_qa" / "assets"
ASSETS.mkdir(parents=True, exist_ok=True)
BODY_FONT = "宋体"
HEADING_FONT = "微软雅黑"
def set_font(run, name=BODY_FONT, size=11, bold=False, color="000000"):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = False
    run.font.color.rgb = RGBColor.from_string(color)
    fonts = run._element.get_or_add_rPr().get_or_add_rFonts()
    for slot in ("ascii", "hAnsi", "eastAsia", "cs"):
        fonts.set(qn(f"w:{slot}"), name)


def link(paragraph, label, url=None, anchor=None, size=11):
    node = OxmlElement("w:hyperlink")
    if url:
        node.set(qn("r:id"), paragraph.part.relate_to(url, RT.HYPERLINK, is_external=True))
    else:
        node.set(qn("w:anchor"), anchor)
    run = OxmlElement("w:r")
    props = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "24546B" if url else "000000")
    props.append(color)
    fonts = OxmlElement("w:rFonts")
    for slot in ("ascii", "hAnsi", "eastAsia", "cs"):
        fonts.set(qn(f"w:{slot}"), BODY_FONT)
    props.append(fonts)
    sz = OxmlElement("w:sz")
    sz.set(qn("w:val"), str(round(size * 2)))
    props.append(sz)
    run.append(props)
    text_node = OxmlElement("w:t")
    text_node.text = label
    run.append(text_node)
    node.append(run)
    paragraph._p.append(node)


def inline(paragraph, text, size=11, bold=False, color="000000"):
    pattern = r"(`[^`]+`|\[[^\]]+\]\(https?://[^)]+\))"
    for part in re.split(pattern, text):
        if not part:
            continue
        if part.startswith("`") and part.endswith("`"):
            run = paragraph.add_run(part[1:-1])
            set_font(run, "Consolas", size - 1, bold, color)
        elif part.startswith("[") and "](" in part:
            matched = re.match(r"\[([^\]]+)\]\(([^)]+)\)", part)
            link(paragraph, matched[1], matched[2], size=size)
        else:
            run = paragraph.add_run(part)
            set_font(run, size=size, bold=bold, color=color)


def bookmark(paragraph, identifier, name):
    start = OxmlElement("w:bookmarkStart")
    start.set(qn("w:id"), str(identifier))
    start.set(qn("w:name"), name)
    end = OxmlElement("w:bookmarkEnd")
    end.set(qn("w:id"), str(identifier))
    paragraph._p.insert(0, start)
    paragraph._p.append(end)


def draw_architecture():
    image = Image.new("RGB", (2100, 1790), "white")
    draw = ImageDraw.Draw(image)
    regular_path = "C:/Windows/Fonts/msyh.ttc"
    bold_path = "C:/Windows/Fonts/msyhbd.ttc"
    font = ImageFont.truetype(regular_path, 34)
    small = ImageFont.truetype(regular_path, 29)
    title_font = ImageFont.truetype(bold_path, 37)
    ink = "#172B36"
    border = "#49636F"

    def box(coords, lines, fill="#F4F7F8", heading=False):
        draw.rounded_rectangle(coords, radius=14, fill=fill, outline=border, width=3)
        selected = title_font if heading else font
        lines = lines.split("\n")
        height = len(lines) * 49
        x0, y0, x1, y1 = coords
        y = (y0 + y1 - height) / 2
        for line in lines:
            width = draw.textlength(line, font=selected)
            draw.text(((x0 + x1 - width) / 2, y), line, font=selected, fill=ink)
            y += 49

    def arrow(points, label=None, label_at=None, both=False):
        draw.line(points, fill=border, width=4, joint="curve")
        import math

        def head(a, b):
            angle = math.atan2(b[1] - a[1], b[0] - a[0])
            rear = (b[0] - 19 * math.cos(angle), b[1] - 19 * math.sin(angle))
            dx, dy = 9 * math.sin(angle), 9 * math.cos(angle)
            draw.polygon([b, (rear[0] + dx, rear[1] - dy), (rear[0] - dx, rear[1] + dy)], fill=border)

        head(points[-2], points[-1])
        if both:
            head(points[1], points[0])
        if label:
            x, y = label_at
            bounds = draw.textbbox((x, y), label, font=small)
            draw.rectangle((bounds[0] - 6, bounds[1] - 4, bounds[2] + 6, bounds[3] + 4), fill="white")
            draw.text((x, y), label, font=small, fill=ink)

    box((110, 30, 1010, 165), "运维人员\n任务创建与结果复核")
    box((1230, 30, 1980, 165), "外部告警\n在线监测与无人机")
    arrow([(560, 165), (560, 265)], both=True)
    arrow([(1605, 165), (1605, 265)])

    draw.rounded_rectangle((65, 265, 2035, 880), radius=18, fill="white", outline=border, width=3)
    draw.text((110, 290), "Java Spring Boot 模块化管理平台", font=title_font, fill="black")
    box((110, 375, 705, 500), "设备与驻留状态")
    box((752, 375, 1347, 500), "任务编排与调度")
    box((1395, 375, 1990, 500), "告警接入与复核")
    box((110, 560, 705, 685), "检测数据与质量")
    box((752, 560, 1347, 685), "结果判读与人工确认")
    box((1395, 560, 1990, 685), "日志与任务档案")
    box((110, 740, 970, 845), "资产数据与图像对象存储", fill="#E9F0F4")
    box((1070, 740, 1990, 845), "设备接入网关  DeviceGateway", fill="#E9F0F4")
    arrow([(1395, 437), (1347, 437)])
    arrow([(705, 437), (752, 437)])
    arrow([(705, 622), (752, 622)])
    arrow([(1693, 560), (1693, 540), (1050, 540), (1050, 500)])
    arrow([(1347, 622), (1368, 622), (1368, 520), (1693, 520), (1693, 500)])
    arrow([(407, 685), (407, 740)])

    box((110, 1020, 1160, 1160), "机器人边缘程序或设备模拟器\n任务执行  采集  缓存", fill="#E9F0F4")
    box((1395, 1020, 1990, 1160), "Dock 控制器\n驻留  补能  通信")
    arrow([(1365, 845), (1365, 945), (635, 945), (635, 1020)], both=True)
    arrow([(1750, 845), (1750, 1020)], both=True)
    arrow([(1160, 1090), (1395, 1090)], both=True)
    draw.text((1190, 1125), "对接握手", font=small, fill=ink)

    box((110, 1310, 1160, 1450), "本地运动与安全控制器\n保护逻辑独立于网络连接", fill="#F4F7F8")
    box((1395, 1310, 1990, 1450), "摄像头与状态传感器\n检测数据与设备状态")
    arrow([(635, 1160), (635, 1310)])
    arrow([(1395, 1380), (1260, 1380), (1260, 1220), (1100, 1220), (1100, 1160)])
    box((110, 1580, 1160, 1720), "轮组  制动  防脱机构", fill="#E9F0F4")
    arrow([(635, 1450), (635, 1580)])
    draw.text((1400, 1600), "云端下发任务级指令", font=small, fill=ink)
    draw.text((1400, 1660), "安全动作由本地控制", font=small, fill=ink)
    out = ASSETS / "architecture.png"
    image.save(out)
    return out


def add_table(doc, rows):
    count = len(rows[0])
    proportions = {
        3: [0.19, 0.40, 0.41],
        4: [0.16, 0.28, 0.28, 0.28],
        8: [0.15, 0.10, 0.105, 0.105, 0.115, 0.095, 0.115, 0.215],
        2: [0.24, 0.76],
    }[count]
    # 按字段长度进一步调整，避免说明列挤占短字段。
    if rows[0][0] in ("触发情况", "检查项", "风险"):
        proportions = [0.21, 0.39, 0.40]
    table = doc.add_table(rows=0, cols=count)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    total = 6.8
    for col, fraction in zip(table.columns, proportions):
        col.width = Inches(total * fraction)
    props = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for side in ("top", "left", "bottom", "right", "insideH", "insideV"):
        node = OxmlElement(f"w:{side}")
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), "5")
        node.set(qn("w:color"), "D9D9D9")
        borders.append(node)
    props.append(borders)
    margins = OxmlElement("w:tblCellMar")
    for side, value in [("top", 95), ("bottom", 95), ("left", 90), ("right", 90)]:
        node = OxmlElement(f"w:{side}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        margins.append(node)
    props.append(margins)
    for index, cells in enumerate(rows):
        row = table.add_row()
        trpr = row._tr.get_or_add_trPr()
        trpr.append(OxmlElement("w:cantSplit"))
        if index == 0:
            trpr.append(OxmlElement("w:tblHeader"))
        for j, value in enumerate(cells):
            cell = row.cells[j]
            cell.width = Inches(total * proportions[j])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            shading = OxmlElement("w:shd")
            shading.set(qn("w:fill"), "DCE8EF" if index == 0 else ("F6F8FA" if index % 2 == 0 else "FFFFFF"))
            cell._tc.get_or_add_tcPr().append(shading)
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.line_spacing = 1.18
            p.paragraph_format.keep_with_next = index <= 1
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if index == 0 or (count == 8 and j < 7) else WD_ALIGN_PARAGRAPH.LEFT
            inline(p, value, size=9.5 if count == 8 else 10, bold=index == 0)
    after = doc.add_paragraph()
    after.paragraph_format.space_after = Pt(1)
    after.paragraph_format.space_before = Pt(0)
    after.paragraph_format.line_spacing = Pt(4)
    set_font(after.add_run(""), size=3)


def main():
    content = SOURCE.read_text(encoding="utf-8")
    diagram = draw_architecture()
    mermaid = re.search(r"```mermaid\n(.*?)\n```", content, re.S)[1]
    (ASSETS / "LineGuard_系统架构.mmd").write_text(mermaid + "\n", encoding="utf-8")
    doc = Document()
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Inches(8.5), Inches(11)
    sec.left_margin = sec.right_margin = Inches(0.85)
    sec.top_margin, sec.bottom_margin = Inches(0.75), Inches(0.7)
    sec.header_distance = sec.footer_distance = Inches(0.3)
    sec.different_first_page_header_footer = True
    for name in ("Normal", "Title", "Subtitle", "Heading 1", "Heading 2", "Heading 3", "Caption"):
        style = doc.styles[name]
        style.font.name = BODY_FONT
        style.font.italic = False
        style.font.color.rgb = RGBColor(0, 0, 0)
        style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), BODY_FONT)
    normal = doc.styles["Normal"]
    normal.font.size = Pt(11)
    normal.paragraph_format.line_spacing = 1.35
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.widow_control = True
    for level, size in [(1, 17), (2, 13), (3, 11.5)]:
        style = doc.styles[f"Heading {level}"]
        style.font.name = HEADING_FONT
        style._element.rPr.rFonts.set(qn("w:eastAsia"), HEADING_FONT)
        style.font.size = Pt(size)
        style.font.bold = True
        style.paragraph_format.space_before = Pt(15 if level == 1 else 11)
        style.paragraph_format.space_after = Pt(7)
        style.paragraph_format.line_spacing = 1.15
        style.paragraph_format.keep_with_next = True
    doc.core_properties.title = "LineGuard 项目计划书 V1.1"
    doc.core_properties.subject = "偏远难达地区架空输电线路常驻式智能运维机器人系统"
    doc.core_properties.author = "LineGuard 项目组"
    doc.core_properties.keywords = "工业机器人,输电运维,OPGW,Java,智能巡检"
    doc.core_properties.comments = ""
    header = sec.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    set_font(header.add_run("LineGuard  项目计划书 V1.1"), HEADING_FONT, 8)
    footer = sec.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(footer.add_run("第 "), size=9)
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), "PAGE")
    footer._p.append(field)
    set_font(footer.add_run(" 页"), size=9)

    # 封面保留正式标题和版本背景。
    p = doc.add_paragraph(style="Title")
    p.paragraph_format.space_before = Pt(78)
    p.paragraph_format.space_after = Pt(22)
    set_font(p.add_run("LineGuard"), HEADING_FONT, 36, True)
    p = doc.add_paragraph(style="Title")
    p.paragraph_format.space_after = Pt(22)
    set_font(p.add_run("项目计划书 V1.1"), HEADING_FONT, 25, True)
    p = doc.add_paragraph(style="Subtitle")
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(30)
    set_font(p.add_run("偏远难达地区架空输电线路\n常驻式智能运维机器人系统"), HEADING_FONT, 18)
    for text in ["项目定位  架空输电线路常驻式近距巡检系统", "产品阶段  单档距核心闭环验证", "版本日期  2026 年 9 月 10 日"]:
        p = doc.add_paragraph()
        inline(p, text, 11)
    cover_before_body = content.split("\n\n# 1", 1)[0]
    intro_match = re.search(r"\n\n(LineGuard 面向.*?)(?=\n\n# 1)", content, re.S)
    intro = intro_match[1]
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(30)
    inline(p, intro, 11)
    doc.add_page_break()

    body = "# 1" + content.split("\n# 1", 1)[1]
    headings = re.findall(r"^# (.+)$", body, re.M)
    p = doc.add_paragraph("目录", style="Heading 1")
    p.paragraph_format.space_before = Pt(0)
    for index, title in enumerate(headings, 1):
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.0
        link(p, title, anchor=f"section_{index}", size=10.5)
    doc.add_page_break()

    lines = body.splitlines()
    i, hidx = 0, 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith("```mermaid"):
            while i < len(lines) and lines[i].strip() != "```":
                i += 1
            p = doc.add_paragraph()
            p.paragraph_format.keep_with_next = True
            p.add_run().add_picture(str(diagram), width=Inches(6.7))
            for shape in p._p.xpath(".//wp:docPr"):
                shape.set("descr", "运维人员和外部告警接入 Java 管理平台，经网关连接机器人与 Dock；本地安全控制器控制轮组、制动和防脱机构。")
            p = doc.add_paragraph("图 1  LineGuard 软件系统逻辑架构", style="Caption")
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_after = Pt(10)
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                row = [item.strip() for item in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", item) for item in row):
                    rows.append(row)
                i += 1
            add_table(doc, rows)
            continue
        if line.startswith("# "):
            title = line[2:]
            if title == "附录 B 核心闭环演示脚本":
                doc.add_page_break()
            hidx += 1
            p = doc.add_paragraph(title, style="Heading 1")
            bookmark(p, hidx, f"section_{hidx}")
        elif line.startswith("## "):
            doc.add_paragraph(line[3:], style="Heading 2")
        else:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY if not re.match(r"^\d+\.", line) else WD_ALIGN_PARAGRAPH.LEFT
            if re.match(r"^\d+\.", line):
                p.paragraph_format.left_indent = Inches(0.15)
                p.paragraph_format.first_line_indent = Inches(-0.15)
            inline(p, line)
        i += 1
    # 清除内置标题样式的边框，避免 Word 默认蓝色横线残留。
    for root in (doc.styles.element, doc.element):
        for border_node in list(root.xpath(".//w:pBdr")):
            border_node.getparent().remove(border_node)
    doc.save(DEST)
    main_part = body.split("# 附录 A", 1)[0]
    main_han = len(re.findall(r"[\u4e00-\u9fff]", main_part))
    print(f"DOCX: {DEST}")
    print(f"Main Chinese characters: {main_han}")
    print(f"Sections: {len(headings)}; tables: {len(doc.tables)}")


if __name__ == "__main__":
    main()
