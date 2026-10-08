"""LineGuard Robot 与 Dock 的外形参数（纯 Python，不依赖 bpy，单位：米，Z 轴向上）。

这是几何尺寸的唯一真源：
- 文档插图：tools/blender/lineguard_model.py 读它建模，再经 render_plates.py 出图；
- 演示三维场景：tools/export_dimensions.py 由它生成 frontend/src/model/robot-dimensions.json。
改尺寸只改这里，两处会一起变；不要在任何一侧另抄一份数字。

坐标系约定：x 沿地线（行进方向），y 横向，z 竖直向上，地线轴心在 z = 0。
演示场景是 three.js 的 Y 轴向上，导出时会做 (x, y, z) -> (x, z, -y) 的映射，保持右手系。
"""

DIM = {
    # 承载壳体
    "body_length": 1.02,
    "body_width": 0.50,
    "body_height": 0.26,
    # 地线与夹持轮
    "wire_radius": 0.010,
    "roller_radius": 0.052,
    "roller_width": 0.048,
    "groove_width": 0.026,
    # 槽深必须小于地线半径：槽一深，上下轮的法兰就会越过线的中心把线整个吞掉，
    # 图上就完全看不出"夹"这个动作了。浅槽＝线坐在槽底、法兰只做导向。
    "groove_depth": 0.008,
    # 夹持站点与侧向承力构件
    "station_offset": 0.30,
    "plate_offset": 0.105,
}

# 壳体顶面高度：夹持框架的脊柱就压在这个面上
BODY_TOP = -0.21
# 轮槽底半径 = 轮半径 - 槽深；要让槽底正好贴住地线表面，轴心就落在这个高度
ROLLER_Z = DIM["wire_radius"] + DIM["roller_radius"] - DIM["groove_depth"]


def derived():
    """给导出脚本用的派生值，避免各处重复算。

    外形包络不在这里给：三视图上的尺寸是渲染时按真实网格顶点算出来的，
    手写一份只会变成第二个会漂移的真相。
    """
    return {**DIM, "body_top": BODY_TOP, "roller_z": ROLLER_Z}
