"""生成与课程源码一致的架构、状态示意图；中文使用本机已安装字体。"""
from pathlib import Path
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "paper/assets"
OUT.mkdir(parents=True, exist_ok=True)
font_path = os.environ.get("LINEGUARD_CJK_FONT", "/System/Library/Fonts/STHeiti Medium.ttc")
font = lambda size: ImageFont.truetype(font_path, size)
INK, EDGE, FILL = "#172F3D", "#557280", "#F3F6F8"

def new(w, h):
    im = Image.new("RGB", (w, h), "white")
    return im, ImageDraw.Draw(im)

def box(d, bounds, lines, fill=FILL, size=31):
    d.rounded_rectangle(bounds, radius=10, fill=fill, outline=EDGE, width=2)
    x1, y1, x2, y2 = bounds
    step = size + 12
    top = (y1+y2-step*len(lines))/2
    for i, text in enumerate(lines):
        d.text(((x1+x2)/2, top+i*step), text, font=font(size), fill=INK, anchor="mt")

def arrow(d, start, end, color=EDGE):
    import math
    d.line([start,end], fill=color, width=3)
    a=math.atan2(end[1]-start[1],end[0]-start[0])
    d.polygon([end,(end[0]-13*math.cos(a-.45),end[1]-13*math.sin(a-.45)),
               (end[0]-13*math.cos(a+.45),end[1]-13*math.sin(a+.45))],fill=color)

im,d = new(1500,680)
box(d,(15,8,1485,86),["Vue 界面  ·  任务控制台 / 历史记录 / 设备状态"],size=33)
arrow(d,(390,88),(390,142)); arrow(d,(1170,142),(1170,88))
d.text((410,95),"HTTP 请求",font=font(28),fill=INK)
d.text((880,95),"SSE 变化通知",font=font(28),fill=INK)
box(d,(15,148,1485,233),["API 接口层  →  MissionApplicationService 编排 / MissionQueryService 查询"],size=31)
arrow(d,(460,234),(460,280)); arrow(d,(1190,234),(1190,280))
box(d,(15,284,900,398),["MissionStateService 业务状态与事务", "TaskStateMachine 白名单校验"],size=31)
box(d,(948,284,1485,398),["DeviceGateway 启停接口", "SimulationEngine 定时调度"],size=30)
arrow(d,(947,345),(902,345))
arrow(d,(460,399),(460,445))
box(d,(15,449,900,526),["Repository → H2 数据  /  InspectionImageStorage → 图像文件"],size=28)
box(d,(948,449,1485,526),["RealtimeEventService → 浏览器"],size=29)
arrow(d,(900,389),(948,459))
d.line([(15,565),(1485,565)],fill=EDGE,width=2)
d.text((750,590),"后续真机接入：指令与回执协议 / 遥测接收 / 本地运动与安全控制",font=font(31),fill=INK,anchor="mt")
d.text((750,636),"当前运动、图像与对接均由同进程仿真实现",font=font(29),fill=EDGE,anchor="mt")
im.save(OUT/"course-architecture.png")

im,d = new(1500,490)
labels=["待派发","预检","离站","前往检测点","定点检测", "数据上传","等待复核","安全返航","对接驻留","任务完成"]
coords=[]
for i,label in enumerate(labels):
    col=i if i<5 else 9-i
    row=0 if i<5 else 1
    x,y=15+col*300,12+row*142
    coords.append((x,y,x+267,y+86))
    box(d,coords[-1],[label],size=33)
for i in range(9):
    a,b=coords[i],coords[i+1]
    if i<4: arrow(d,(a[2],a[1]+43),(b[0],b[1]+43))
    elif i==4: arrow(d,(a[0]+133,a[3]),(b[0]+133,b[1]))
    else: arrow(d,(a[0],a[1]+43),(b[2],b[1]+43))
d.line([(15,275),(1485,275)], fill=EDGE,width=2)
for j,(a,b) in enumerate([
    ("预检失败 → 修正后重派", "模糊图 → 原位重采一次"),
    ("断联 / 程序异常 → 等待人工", "障碍 / 对接超次 → 安全驻停"),
    ("终止：站内结束，线上驻停", "恢复：先确认条件，再下达返航"),
]):
    d.text((30,301+j*62),a,font=font(31),fill=INK)
    d.text((780,301+j*62),b,font=font(31),fill=INK)
im.save(OUT/"course-states.png")

raw=ROOT/".lineguard_qa/assets/course-model-raw.png"
if raw.exists():
    source=Image.open(raw).convert("RGBA")
    canvas=Image.new("RGB",source.size,"white")
    canvas.paste(source,mask=source.getchannel("A"))
    d=ImageDraw.Draw(canvas)
    d.text((420,645),"挂线机器人",font=font(38),fill=INK,anchor="mt")
    d.text((1240,645),"驻留站点 Dock",font=font(38),fill=INK,anchor="mt")
    canvas.save(OUT/"course-model.png")
print("课程配图已生成", OUT)
