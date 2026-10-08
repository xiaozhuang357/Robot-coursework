"""把 Robot 与 Dock 的构件表导成 frontend/src/model/lineguard-parts.json，供演示三维场景直接渲染。

几何只有一份：文档插图与演示场景都来自 lineguard_model.py 的同一套参数。
这里只做坐标映射与材质提取，不重新描述任何摆位——摆位再写一遍正是会悄悄漂移的地方。

用法：blender -b -P tools/blender/export_parts.py [-- --check]
"""

import hashlib
import json
import os
import sys

import bpy
from mathutils import Euler, Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TARGET = os.path.join(ROOT, "lineguard-system", "frontend", "src", "model", "lineguard-parts.json")

sys.path.insert(0, HERE)
import lineguard_dimensions as D  # noqa: E402
import lineguard_model as M  # noqa: E402

# Blender（Z 轴向上）-> three.js（Y 轴向上）：(x, y, z) -> (x, z, -y)。
# 该矩阵正交且行列式为 +1，不会把模型镜像掉。
MAPPING = Matrix(((1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, -1.0, 0.0)))
AXES = ("x", "y", "z")


def to_three(vector):
    mapped = MAPPING @ Vector(vector)
    return [round(mapped.x, 6), round(mapped.y, 6), round(mapped.z, 6)]


def _round(values):
    return [round(float(value), 6) for value in values]


def orientation(rotation):
    """把只绕单轴转 90° 的旋转折算成「构件轴向」在 three 空间里的正方向。

    返回 (轴字母, 正负号)：正号表示 Blender 的 radius2（局部 +Z 端）落在该轴的正方向。
    """
    matrix = Euler(tuple(rotation), "XYZ").to_matrix()
    direction = to_three(matrix @ Vector((0.0, 0.0, 1.0)))
    index = max(range(3), key=lambda slot: abs(direction[slot]))
    return AXES[index], (1 if direction[index] > 0 else -1)


def quaternion(rotation):
    matrix = Euler(tuple(rotation), "XYZ").to_matrix()
    mapped = MAPPING @ matrix @ MAPPING.inverted()
    value = mapped.to_quaternion()
    return [round(value.x, 6), round(value.y, 6), round(value.z, 6), round(value.w, 6)]


def material_payload(names):
    payload = {}
    for name in sorted(names):
        material = bpy.data.materials.get(name)
        if material is None or not material.use_nodes:
            continue
        shader = material.node_tree.nodes.get("Principled BSDF")
        if shader is None:
            continue
        color = shader.inputs["Base Color"].default_value
        payload[name] = {
            "color": _round(color[:3]),
            "metalness": round(float(shader.inputs["Metallic"].default_value), 4),
            "roughness": round(float(shader.inputs["Roughness"].default_value), 4),
        }
    return payload


def convert(part):
    kind = part["kind"]
    entry = {
        "kind": kind,
        "name": part["name"],
        "material": part["material"],
        "pos": to_three(part["location"]),
    }
    if kind == "box":
        size = part["size"]
        entry["size"] = [round(size[0], 6), round(size[2], 6), round(size[1], 6)]
        entry["quat"] = quaternion(part["rotation"])
        return entry
    axis, sign = orientation(part["rotation"])
    entry["axis"] = axis
    if kind == "sphere":
        entry["radius"] = part["radius"]
        return entry
    if kind == "torus":
        entry["radius"] = round(part["radius"], 6)
        entry["tube"] = round(part["tube"], 6)
        return entry
    entry["depth"] = round(part["depth"], 6)
    if kind == "cylinder":
        entry["rPos"] = entry["rNeg"] = round(part["radius"], 6)
        return entry
    radius1, radius2 = round(part["radius1"], 6), round(part["radius2"], 6)
    entry["rPos"] = radius2 if sign > 0 else radius1
    entry["rNeg"] = radius1 if sign > 0 else radius2
    return entry


def source_hash():
    """模型源码指纹。只覆盖尺寸与建模两个文件：模型改了却没重跑导出时，
    verify_lineguard.py 能靠它发现，不需要为了校验再装一遍 Blender。"""
    digest = hashlib.sha256()
    for name in ("lineguard_dimensions.py", "lineguard_model.py"):
        with open(os.path.join(HERE, name), "rb") as handle:
            digest.update(handle.read())
    return digest.hexdigest()[:16]


def collect(builder):
    """跑一次建模、把构件表转成 three.js 空间的描述，并收集用到的材质名。"""
    M.reset_parts()
    builder()
    parts = [convert(part) for part in M.PARTS]
    names = {part["material"] for part in M.PARTS if part["material"]}
    return parts, names


def payload():
    M.reset_scene()
    robot, robot_materials = collect(M.build_robot)
    # 驻留坞导出"泊位空闲、无顶棚"状态：锁止压板缩回，因为场景里机器人多数时候在线上；
    # 顶棚不导出——它正好盖在停车位上方，会把机器人整个挡住，文档里的对接插图同理省略。
    dock, dock_materials = collect(lambda: M.build_dock(lock_closed=False, canopy=False))
    return {
        "_generated_by": "tools/blender/export_parts.py（改几何请改 lineguard_model.py 后重跑）",
        "_axes": "米；已映射到 three.js：x 沿地线，y 竖直向上，z 横向；地线轴心在 y = 0",
        "_source_hash": source_hash(),
        "wireRadius": D.DIM["wire_radius"],
        "robot": robot,
        "dock": dock,
        "materials": material_payload(robot_materials | dock_materials),
    }


def main():
    arguments = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    text = json.dumps(payload(), ensure_ascii=False, indent=1) + "\n"
    if "--check" in arguments:
        current = open(TARGET, encoding="utf-8").read() if os.path.isfile(TARGET) else ""
        if current != text:
            print(f"[parts] 过期：{os.path.relpath(TARGET, ROOT)} 与模型不一致，请重跑本脚本")
            return 1
        print("[parts] 一致")
        return 0
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8") as handle:
        handle.write(text)
    data = payload()
    print(f"[parts] 已写入 {os.path.relpath(TARGET, ROOT)}："
          f"Robot {len(data['robot'])} 个构件、Dock {len(data['dock'])} 个构件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
