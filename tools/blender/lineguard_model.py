"""LineGuard Robot / Dock 三维模型（Blender bpy，单位：米，Z 轴向上）。

行走机构用「上下 V 槽轮对夹」，不靠自重压线：
牵引力设计上来自预压弹簧产生的法向力；摩擦、坡度与覆冰的影响仍需实机验证。
此处只建立结构示意，不计算接触力、驱动效率或防脱安全裕量。

横向构型（决定这张图能不能一眼看懂，改动前请先想清楚）：
    上夹持轮  轴心 z = +0.049（V 槽底压在线上方）
    地线      轴心 z =  0
    下夹持轮  轴心 z = -0.049（V 槽底压在线的下方，由弹簧顶紧）
    夹持侧板  y = ±0.078，把上下轮与地线一起框住，同时承担夹持反力，兼作防脱
    承载壳体  顶面低于夹持框架，靠脊柱往下挂
轮槽宽度必须略大于地线直径，否则两侧锥台会啃到线。
"""

import math
import os
import sys

import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lineguard_dimensions import BODY_TOP, DIM, ROLLER_Z  # noqa: E402

# 每个构件在创建时顺手记一条"可复现描述"，tools/blender/export_parts.py 用它把同一套几何
# 导给演示三维场景，避免在 TypeScript 里再抄一份摆位代码（那正是会悄悄漂移的地方）。
PARTS: list[dict] = []

# 允许出现的旋转：整机所有构件都只绕单轴转 90°，别的一律视为写错了
_QUARTER = 1.5707963


def reset_parts():
    PARTS.clear()


def _emit(kind, name, mat, location, rotation, **params):
    rotation = tuple(float(value) for value in rotation)
    # 方块导出的是完整四元数，任意角度都能表达；圆柱/锥台/圆环只导出"轴向"，
    # 所以它们必须轴对齐，斜着的构件会导错方向，这里直接拦下来。
    if kind != "box" and any(
            abs(value) > 1e-6 and abs(abs(value) - _QUARTER) > 1e-6 for value in rotation):
        raise ValueError(f"{name} 的旋转 {rotation} 不是轴对齐 90°，导出的演示模型会与插图不一致")
    PARTS.append({
        "kind": kind,
        "name": name,
        "material": mat.name if mat else None,
        "location": [float(value) for value in location],
        "rotation": list(rotation),
        **params,
    })


def reset_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.lights, bpy.data.cameras):
        for item in list(block):
            if item.users == 0:
                block.remove(item)


def material(name, color, metallic=0.0, roughness=0.5, alpha=1.0):
    existing = bpy.data.materials.get(name)
    if existing:
        return existing
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = mat.node_tree.nodes["Principled BSDF"]
    shader.inputs["Base Color"].default_value = (*color, alpha)
    shader.inputs["Metallic"].default_value = metallic
    shader.inputs["Roughness"].default_value = roughness
    if alpha < 1.0:
        shader.inputs["Alpha"].default_value = alpha
        mat.blend_method = "BLEND"
    return mat


def materials():
    """基色按线性空间给值，配合 Standard 视图变换渲染出目标 sRGB。"""
    return {
        "shell": material("Shell", (0.027, 0.205, 0.254), 0.28, 0.46),
        "shell_light": material("ShellLight", (0.235, 0.278, 0.302), 0.30, 0.55),
        "alloy": material("Alloy", (0.39, 0.46, 0.50), 0.90, 0.30),
        "alloy_dark": material("AlloyDark", (0.075, 0.098, 0.110), 0.85, 0.36),
        "rubber": material("Rubber", (0.018, 0.022, 0.026), 0.02, 0.90),
        "brass": material("Brass", (0.50, 0.24, 0.030), 0.95, 0.25),
        "glass": material("Glass", (0.016, 0.040, 0.052), 0.25, 0.22),
        "panel": material("Panel", (0.015, 0.032, 0.042), 0.45, 0.42),
        "amber": material("Amber", (0.85, 0.36, 0.036), 0.20, 0.40),
        "accent": material("Accent", (0.030, 0.230, 0.270), 0.40, 0.40),
        "wire": material("Wire", (0.32, 0.37, 0.39), 0.92, 0.22),
    }


def _finish(obj, mat, bevel=0.0, smooth=False):
    if bevel:
        mod = obj.modifiers.new("Bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 3
        mod.limit_method = "ANGLE"
    if smooth:
        bpy.context.view_layer.objects.active = obj
        try:
            bpy.ops.object.shade_auto_smooth(angle=0.6)
        except Exception:
            bpy.ops.object.shade_smooth()
    if mat:
        obj.data.materials.append(mat)
    return obj


def box(name, size, location, mat=None, rotation=(0, 0, 0), bevel=0.012):
    bpy.ops.mesh.primitive_cube_add(size=1, location=location, rotation=rotation)
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    _emit("box", name, mat, location, rotation, size=[float(value) for value in size])
    return _finish(obj, mat, bevel)


def cylinder(name, radius, depth, location, mat=None, rotation=(0, 0, 0), vertices=32):
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=depth, vertices=vertices,
                                        location=location, rotation=rotation)
    obj = bpy.context.active_object
    obj.name = name
    _emit("cylinder", name, mat, location, rotation, radius=float(radius), depth=float(depth))
    return _finish(obj, mat, 0.0, smooth=True)


def cone(name, radius1, radius2, depth, location, mat=None, rotation=(0, 0, 0), vertices=32):
    """锥台。Blender 的 radius1 在局部 -Z 端、radius2 在 +Z 端，导出时靠这条约定定朝向。"""
    bpy.ops.mesh.primitive_cone_add(vertices=vertices, radius1=radius1, radius2=radius2,
                                    depth=depth, location=location, rotation=rotation)
    obj = bpy.context.active_object
    obj.name = name
    _emit("cone", name, mat, location, rotation,
          radius1=float(radius1), radius2=float(radius2), depth=float(depth))
    return _finish(obj, mat, 0.0, smooth=True)


def torus(name, major, minor, location, mat=None, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor,
                                     major_segments=36, minor_segments=12,
                                     location=location, rotation=rotation)
    obj = bpy.context.active_object
    obj.name = name
    _emit("torus", name, mat, location, rotation, radius=float(major), tube=float(minor))
    return _finish(obj, mat, 0.0, smooth=True)


def sphere(name, radius, location, mat=None, segments=20, rings=12):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, segments=segments, ring_count=rings,
                                         location=location)
    obj = bpy.context.active_object
    obj.name = name
    _emit("sphere", name, mat, location, (0, 0, 0), radius=float(radius))
    return _finish(obj, mat, 0.0, smooth=True)


def build_clamp_roller(name, location, mats):
    """V 槽夹持轮：两侧锥台夹出轮槽，槽底是圆柱面，中间是轮毂。轮轴沿 y。

    锥台小端必须朝内，否则轮槽会变成朝外张开的凸棱，夹不住线。
    旋转 Rx(90°) 把锥体默认的 +Z 轴映射到世界 -Y，所以 radius2 落在 -y 一侧。
    """
    radius = DIM["roller_radius"]
    width = DIM["roller_width"]
    groove = DIM["groove_width"]
    bottom = radius - DIM["groove_depth"]
    flange = (width - groove) / 2
    parts = [cylinder(f"{name}_Bottom", bottom, groove, location, mats["rubber"],
                      rotation=(1.5707963, 0, 0), vertices=32)]
    for side in (-1, 1):
        radius1, radius2 = (radius, bottom) if side > 0 else (bottom, radius)
        parts.append(cone(
            f"{name}_Flange{side}", radius1, radius2, flange,
            (location[0], location[1] + side * (groove + flange) / 2, location[2]),
            mats["alloy"], rotation=(1.5707963, 0, 0), vertices=32))
    parts.append(cylinder(f"{name}_Hub", radius * 0.30, width * 1.06, location,
                          mats["alloy_dark"], rotation=(1.5707963, 0, 0), vertices=20))
    return parts


def build_robot():
    """返回 (created, anchors)；锚点为世界坐标，供标注投影使用。"""
    mats = materials()
    created = []
    anchors = {}
    length = DIM["body_length"]
    width = DIM["body_width"]
    height = DIM["body_height"]
    station_x = DIM["station_offset"]
    plate_y = DIM["plate_offset"]

    # 承载壳体：从夹持框架的脊柱往下挂（顶面 = -0.21）
    created.append(box("Chassis", (length, width, height), (0, 0, -0.34), mats["shell"], bevel=0.026))
    created.append(box("TopCover", (length * 0.94, width * 0.90, 0.028), (0, 0, -0.205),
                       mats["shell_light"], bevel=0.010))
    for side in (-1, 1):
        for lift in (0.0, 0.07):
            created.append(box(f"Groove{side}_{lift}", (length * 0.68, 0.013, 0.016),
                               (0, side * (width / 2 - 0.002), -0.35 + lift), mats["panel"]))
    anchors["承载单元"] = Vector((0, -width / 2 - 0.01, -0.35))
    anchors["电池与配重"] = Vector((0, -0.17, -0.50))

    # 脊柱：把两个夹持站点连起来，并把载荷传给壳体
    created.append(box("Spine", (length * 0.88, 0.11, 0.05), (0, 0, -0.19),
                       mats["alloy_dark"], bevel=0.012))

    # 夹持站点 ×2：上下 V 槽轮对夹，侧立柱承力并兼作防脱
    # 侧向构件做窄条而不是整片立板：立板会把轮子完全挡在后面，等于把要说明的机构藏起来
    for station in (-station_x, station_x):
        for side in (-1, 1):
            created.append(box(f"ClampPost{station}_{side}", (0.045, 0.024, 0.255),
                               (station, side * plate_y, -0.028), mats["alloy"], bevel=0.008))
        created.append(box(f"ClampCap{station}", (0.05, plate_y * 2 + 0.016, 0.014),
                           (station, 0, 0.108), mats["alloy"], bevel=0.006))

        created.extend(build_clamp_roller(f"Drive{station}", (station, 0, ROLLER_Z), mats))
        created.extend(build_clamp_roller(f"Pinch{station}", (station, 0, -ROLLER_Z), mats))
        created.append(cylinder(f"AxleTop{station}", 0.013, 0.24, (station, 0, ROLLER_Z),
                                mats["alloy"], rotation=(1.5707963, 0, 0), vertices=20))
        created.append(cylinder(f"AxleBottom{station}", 0.013, 0.24, (station, 0, -ROLLER_Z),
                                mats["alloy_dark"], rotation=(1.5707963, 0, 0), vertices=20))

        # 下轮装在滑块上，由弹簧向上顶紧——夹持法向力来自弹簧，不来自机体重量
        created.append(box(f"Slider{station}", (0.05, plate_y * 2 + 0.02, 0.055),
                           (station, 0, -ROLLER_Z), mats["alloy"], bevel=0.010))
        created.append(cylinder(f"GuideRod{station}", 0.008, 0.09, (station, 0, -0.105),
                                mats["alloy"], vertices=14))
        for index, spring_z in enumerate((-0.085, -0.106, -0.127)):
            created.append(torus(f"Spring{station}_{index}", 0.030, 0.006, (station, 0, spring_z),
                                 mats["alloy_dark"]))
        created.append(box(f"SpringSeat{station}", (0.07, plate_y * 2 + 0.02, 0.022),
                           (station, 0, -0.145), mats["alloy_dark"], bevel=0.006))

        # 驱动：一台电机经锥齿轮同时带动上下两轮，两轮对线同向推、扭矩不互相抵消
        created.append(box(f"GearCover{station}", (0.085, 0.036, 0.128), (station, -0.127, 0.0),
                           mats["panel"], bevel=0.010))
        created.append(cylinder(f"Motor{station}", 0.028, 0.055, (station, -0.172, ROLLER_Z),
                                mats["alloy_dark"], rotation=(1.5707963, 0, 0), vertices=24))

    anchors["驱动夹持轮"] = Vector((-station_x, -0.024, ROLLER_Z + 0.052))
    anchors["压紧轮与弹簧"] = Vector((station_x, 0, -0.106))
    anchors["夹持框架"] = Vector((station_x, -plate_y, 0.055))

    # 电池包与接线端子
    created.append(box("Battery", (0.48, 0.34, 0.10), (0, 0, -0.50), mats["alloy_dark"], bevel=0.018))
    created.append(box("BatteryRail", (0.54, 0.38, 0.022), (0, 0, -0.443), mats["alloy"], bevel=0.006))
    for side in (-1, 1):
        created.append(cylinder(f"Terminal{side}", 0.013, 0.05, (-0.26, side * 0.09, -0.50),
                                mats["brass"], rotation=(0, 1.5707963, 0), vertices=16))

    # 检测云台：从机体前端伸出，镜头朝前
    gimbal_x = -length / 2 - 0.02
    created.append(cylinder("PanBase", 0.050, 0.055, (gimbal_x, 0, -0.30), mats["alloy"],
                            rotation=(0, 1.5707963, 0), vertices=28))
    created.append(torus("PanRing", 0.052, 0.010, (gimbal_x - 0.03, 0, -0.30), mats["alloy_dark"],
                         rotation=(0, 1.5707963, 0)))
    created.append(cylinder("Barrel", 0.042, 0.105, (gimbal_x - 0.095, 0, -0.30),
                            mats["alloy_dark"], rotation=(0, 1.5707963, 0), vertices=28))
    created.append(torus("LensRing", 0.044, 0.008, (gimbal_x - 0.148, 0, -0.30), mats["alloy"],
                         rotation=(0, 1.5707963, 0)))
    created.append(cylinder("Lens", 0.032, 0.012, (gimbal_x - 0.155, 0, -0.30), mats["glass"],
                            rotation=(0, 1.5707963, 0), vertices=28))
    for side in (-1, 1):
        created.append(sphere(f"Emitter{side}", 0.011, (gimbal_x - 0.16, side * 0.048, -0.30),
                              mats["amber"], segments=16, rings=10))
    anchors["检测云台"] = Vector((gimbal_x - 0.16, 0, -0.30))

    # 控制盒：架在脊柱上、两个夹持站点之间
    created.append(box("ControlBox", (0.20, 0.26, 0.075), (0, 0, -0.155), mats["panel"], bevel=0.014))
    created.append(box("Display", (0.10, 0.15, 0.014), (0, 0, -0.112), mats["glass"], bevel=0.003))
    for side in (-1, 1):
        created.append(cylinder(f"Button{side}", 0.011, 0.012, (0, side * 0.085, -0.113),
                                mats["amber"], vertices=16))

    # 通信天线
    created.append(cylinder("Mast", 0.010, 0.28, (0.40, -0.20, -0.07), mats["alloy_dark"], vertices=14))
    created.append(sphere("AntennaTip", 0.018, (0.40, -0.20, 0.08), mats["amber"], segments=18))
    anchors["通信天线"] = Vector((0.40, -0.20, 0.09))

    # 线缆接头与顶盖螺栓
    for index, position in enumerate([(-0.44, -0.20, -0.212), (-0.44, 0.20, -0.212),
                                      (0.44, -0.20, -0.212), (0.44, 0.20, -0.212)]):
        created.append(cylinder(f"Gland{index}", 0.013, 0.028, position, mats["alloy_dark"], vertices=14))
    for x in (-length / 2 + 0.09, length / 2 - 0.09):
        for y in (-width / 2 + 0.07, width / 2 - 0.07):
            created.append(cylinder("Bolt", 0.010, 0.022, (x, y, -0.20), mats["alloy"], vertices=12))

    return created, anchors


def build_dock(lock_closed=False, canopy=True):
    """驻留坞：卡在地线上的一套门架，Robot 从 -x 侧驶入、尾部先进、停在中心 x = 0.04。

    侧向导向做成低矮导轨（顶面 z = -0.295）而不是整片坞壁，否则从斜上方看会把机体整个挡住；
    也刻意不做底板与横拉，避免和机腹电池打架。
    """
    mats = materials()
    created = []
    anchors = {}

    # 侧向导向导轨、入口喇叭口、侧面充电触点
    # 导轨要用金属色：一旦和机体同色，读图时会被当成 Robot 的一部分
    for side in (-1, 1):
        created.append(box(f"DockRail{side}", (1.20, 0.030, 0.10), (0.10, side * 0.345, -0.375),
                           mats["alloy_dark"], bevel=0.012))
        created.append(box(f"Horn{side}", (0.26, 0.028, 0.16), (-0.56, side * 0.305, -0.350),
                           mats["alloy"], rotation=(0, 0, -side * 0.30), bevel=0.010))
        created.append(box(f"Contact{side}", (0.10, 0.080, 0.07), (0.20, side * 0.292, -0.31),
                           mats["brass"], bevel=0.006))
    anchors["充电触点"] = Vector((0.20, -0.292, -0.31))

    # 远端止挡、立柱与顶梁：高度只比 Robot 最高点略高，做高了会变成压住整台的脚手架
    created.append(box("StopBlock", (0.07, 0.68, 0.52), (0.62, 0, -0.26), mats["panel"], bevel=0.016))
    for side in (-1, 1):
        created.append(box(f"BeamPost{side}", (0.05, 0.032, 0.24), (0.62, side * 0.345, 0.12),
                           mats["alloy_dark"], bevel=0.008))
    created.append(box("CrossBeam", (0.09, 0.76, 0.040), (0.62, 0, 0.26), mats["alloy"], bevel=0.010))

    # 锁止压板：锁定时从止挡面伸出、压住机壳顶盖；未锁定时缩回贴住止挡面。
    # 固定在止挡面上，不会像外翻抱箍那样绕轴一转就脱开支架、看着像悬空零件。
    reach = 0.13 if lock_closed else 0.02
    for side in (-1, 1):
        created.append(box(f"LockPawl{side}", (reach, 0.05, 0.022),
                           (0.585 - reach / 2, side * 0.11, -0.178),
                           mats["alloy_dark"], bevel=0.006))
    anchors["锁止机构"] = Vector((0.585 - reach / 2, -0.11, -0.178))

    # 到位传感器与状态灯
    created.append(cylinder("DockSensor", 0.016, 0.032, (0.56, -0.16, -0.10), mats["accent"], vertices=18))
    anchors["到位检测"] = Vector((0.55, -0.16, -0.09))
    created.append(sphere("Lamp", 0.024, (0.62, 0.38, 0.24), mats["amber"], segments=18))

    if canopy:
        created.append(box("Solar", (1.16, 0.88, 0.05), (0.10, 0.52, 0.36), mats["panel"],
                           rotation=(-0.15, 0, 0), bevel=0.014))
        for index in range(3):
            created.append(box(f"SolarFrame{index}", (1.12, 0.016, 0.052),
                               (0.10, 0.18 + index * 0.34, 0.365 + index * 0.006),
                               mats["alloy"], bevel=0.005))
        anchors["光伏板"] = Vector((0.10, 0.52, 0.42))

    anchors["驻留坞体"] = Vector((0.10, -0.345, -0.36))
    return created, anchors


def build_wire(length=4.2, location=(0, 0, 0)):
    mats = materials()
    bpy.ops.mesh.primitive_cylinder_add(radius=DIM["wire_radius"], depth=length,
                                        vertices=32, location=location,
                                        rotation=(0, 1.5707963, 0))
    obj = bpy.context.active_object
    obj.name = "GroundWire"
    return [_finish(obj, mats["wire"], 0.0, smooth=True)]
