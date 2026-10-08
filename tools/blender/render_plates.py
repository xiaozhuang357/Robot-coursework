"""用 Blender 渲染文档插图的底版（不含文字），并把锚点/包围盒投影成像素坐标写出 JSON。

用法：blender -b -P tools/blender/render_plates.py -- [figure ...]

产物：
  .lineguard_qa/plates/<figure>.png    底版图
  .lineguard_qa/plates/<figure>.json   尺寸 + 锚点像素坐标 + 主体包围盒

文字与引线由 tools/compose_figures.mjs 叠加，中文字形走系统字体，避免 Blender 内置字体缺字。
"""

import json
import os
import sys
import time

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PLATES = os.path.join(ROOT, ".lineguard_qa", "plates")
IGNORE = ("Backdrop", "GroundWire")

sys.path.insert(0, HERE)
import lineguard_model as M  # noqa: E402


# ---------------------------------------------------------------- 场景搭建

def setup_render(width, height, samples=64):
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = width
    scene.render.resolution_y = height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.film_transparent = False

    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"

    eevee = scene.eevee
    for attr, value in (("taa_render_samples", samples),
                        ("use_raytracing", True),
                        ("use_shadows", True)):
        if hasattr(eevee, attr):
            setattr(eevee, attr, value)
    if hasattr(eevee, "ray_tracing_options") and hasattr(eevee.ray_tracing_options, "use_denoise"):
        eevee.ray_tracing_options.use_denoise = True
    if hasattr(scene, "use_gtao"):
        scene.use_gtao = True
    return scene


def setup_world(color=(0.58, 0.65, 0.72), strength=0.50):
    world = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    bpy.context.scene.world = world
    world.use_nodes = True
    nodes = world.node_tree.nodes
    nodes.clear()
    background = nodes.new("ShaderNodeBackground")
    background.inputs["Color"].default_value = (*color, 1.0)
    background.inputs["Strength"].default_value = strength
    output = nodes.new("ShaderNodeOutputWorld")
    world.node_tree.links.new(background.outputs["Background"], output.inputs["Surface"])
    return world


def add_backdrop(camera_location, target=(0, 0, 0), size=52.0, distance=9.0):
    """沿视线置于主体之后的背景板，用生成坐标做纵向渐变，与相机无关地铺满画面。"""
    view_dir = (Vector(target) - Vector(camera_location)).normalized()
    bpy.ops.mesh.primitive_plane_add(size=size, location=Vector(target) + view_dir * distance)
    plane = bpy.context.active_object
    plane.name = "Backdrop"
    plane.rotation_euler = (-view_dir).to_track_quat("Z", "Y").to_euler()

    mat = bpy.data.materials.new("Backdrop")
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links

    coordinates = nodes.new("ShaderNodeTexCoord")
    separate = nodes.new("ShaderNodeSeparateXYZ")
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.02
    ramp.color_ramp.elements[0].color = (0.930, 0.945, 0.960, 1.0)
    ramp.color_ramp.elements[1].position = 0.98
    ramp.color_ramp.elements[1].color = (0.735, 0.788, 0.842, 1.0)
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Strength"].default_value = 0.94
    output = nodes.new("ShaderNodeOutputMaterial")

    links.new(coordinates.outputs["Generated"], separate.inputs["Vector"])
    links.new(separate.outputs["Y"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], emission.inputs["Color"])
    links.new(emission.outputs["Emission"], output.inputs["Surface"])
    plane.data.materials.append(mat)
    return plane


def add_lights(scale=1.0, target=(0, 0, -0.12)):
    specs = [
        ("Key", (-2.30, -2.60, 2.60), 200.0 * scale, 2.60, (1.00, 0.98, 0.94)),
        ("Fill", (2.70, -1.90, 0.70), 75.0 * scale, 3.20, (0.86, 0.92, 1.00)),
        ("Rim", (0.90, 2.80, 1.80), 150.0 * scale, 2.20, (0.92, 0.96, 1.00)),
        ("Top", (0.10, 0.35, 3.20), 55.0 * scale, 2.60, (1.00, 1.00, 1.00)),
    ]
    lights = []
    for name, location, energy, size, color in specs:
        data = bpy.data.lights.new(name, "AREA")
        data.energy = energy
        data.size = size
        data.color = color
        obj = bpy.data.objects.new(name, data)
        obj.location = location
        bpy.context.collection.objects.link(obj)
        _aim(obj, target)
        lights.append(obj)
    return lights


def _aim(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


# ---------------------------------------------------------------- 相机与投影

def add_camera(location, target=(0, 0, 0), ortho_scale=None, lens=62.0, rotation=None):
    data = bpy.data.cameras.new("Camera")
    if ortho_scale is not None:
        data.type = "ORTHO"
        data.ortho_scale = ortho_scale
    else:
        data.lens = lens
    obj = bpy.data.objects.new("Camera", data)
    obj.location = location
    bpy.context.collection.objects.link(obj)
    if rotation is not None:
        obj.rotation_euler = rotation
    else:
        _aim(obj, target)
    bpy.context.scene.camera = obj
    return obj


def _project(scene, point):
    from bpy_extras.object_utils import world_to_camera_view
    co = world_to_camera_view(scene, scene.camera, Vector(point))
    return [round(co.x * scene.render.resolution_x, 2),
            round((1.0 - co.y) * scene.render.resolution_y, 2)]


def _visible_bbox(scene):
    """把主体网格顶点投到像素空间取包围盒，作为尺寸标注的精确基准（不含背景与地线）。

    同时给出世界坐标包围盒，尺寸标注上的数字要用它，像素盒只用来定位引线端点。
    """
    min_x = min_y = float("inf")
    max_x = max_y = float("-inf")
    world_min = [float("inf")] * 3
    world_max = [float("-inf")] * 3
    for obj in scene.objects:
        if obj.type != "MESH" or obj.name.startswith(IGNORE):
            continue
        for corner in obj.bound_box:
            world = obj.matrix_world @ Vector(corner)
            for axis in range(3):
                world_min[axis] = min(world_min[axis], world[axis])
                world_max[axis] = max(world_max[axis], world[axis])
            px, py = _project(scene, world)
            min_x, min_y = min(min_x, px), min(min_y, py)
            max_x, max_y = max(max_x, px), max(max_y, py)
    return {
        "min": [round(min_x, 2), round(min_y, 2)],
        "max": [round(max_x, 2), round(max_y, 2)],
        "world_min": [round(value, 4) for value in world_min],
        "world_max": [round(value, 4) for value in world_max],
    }


def render(name, scene, anchors=None, note=""):
    path = os.path.join(PLATES, f"{name}.png")
    scene.render.filepath = path
    started = time.time()
    bpy.ops.render.render(write_still=True)

    payload = {
        "figure": name,
        "plate": f"{name}.png",
        "width": scene.render.resolution_x,
        "height": scene.render.resolution_y,
        "anchors": {key: _project(scene, value) for key, value in (anchors or {}).items()},
        "bbox": _visible_bbox(scene),
        "note": note,
    }
    with open(os.path.join(PLATES, f"{name}.json"), "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
    print(f"[figure] {name} -> {os.path.basename(path)}  {round(time.time() - started, 1)}s")
    return payload


# ---------------------------------------------------------------- 三张插图

def figure_cross_section():
    """挂线断面：一台 Robot 骑在地线上，前上左视角，标注七个功能单元。"""
    scene = setup_render(1680, 1050, samples=128)
    setup_world()
    camera_location = (-1.75, -2.28, 0.98)
    target = (-0.05, 0, -0.20)
    add_backdrop(camera_location, target)
    add_lights(target=target)
    add_camera(camera_location, target, lens=62.0)
    M.build_wire(length=2.0)
    _, anchors = M.build_robot()
    return render("cross-section", scene, anchors, "Robot 挂线断面，含地线剖面")


DOCK_STEPS = [
    ("docking-approach", "① 接近对位", -0.70, False),
    ("docking-locked", "② 锁止充电", 0.05, True),
]


def figure_docking():
    """对接分步：每步单独出图，两图同机位同焦距，拼起来就能看出 Robot 沿地线驶入。

    合成一张 2000px 以上的长条会让单个 Robot 只剩 2 cm 宽，读不出机构，所以拆开渲染。
    """
    outputs = []
    for name, label, robot_dx, locked in DOCK_STEPS:
        scene = setup_render(880, 560, samples=96)
        setup_world()
        camera_location = (-1.75, -4.00, 2.55)
        target = (-0.35, 0, -0.20)
        add_backdrop(camera_location, target, size=24.0, distance=5.0)
        add_lights(scale=0.85, target=target)
        add_camera(camera_location, target, lens=72.0)

        dock_parts, dock_anchors = M.build_dock(lock_closed=locked, canopy=False)
        robot_parts, robot_anchors = M.build_robot()
        for obj in robot_parts:
            obj.location.x += robot_dx
        M.build_wire(length=2.5, location=(-0.35, 0, 0))

        anchors = {key: value for key, value in dock_anchors.items()}
        for key, value in robot_anchors.items():
            anchors[key] = Vector((value.x + robot_dx, value.y, value.z))
        payload = render(name, scene, anchors, label)
        payload["label"] = label
        outputs.append(payload)

    with open(os.path.join(PLATES, "docking.json"), "w", encoding="utf-8") as handle:
        json.dump({"steps": outputs}, handle, ensure_ascii=False, indent=2)
    return outputs


VIEWS = [
    ("front", "主视图", (0, -6.0, -0.20), (1.5707963, 0, 0), "全长 L × 高 H"),
    ("top", "俯视图", (0, 0, 6.0), (0, 0, 0), "全长 L × 宽 W"),
    ("side", "侧视图", (6.0, 0, -0.20), (1.5707963, 0, 1.5707963), "宽 W × 高 H"),
]


def figure_three_views():
    """三视图：正交相机逐个出图，最后合成一张。"""
    outputs = []
    for key, label, location, rotation, dims in VIEWS:
        scene = setup_render(900, 760, samples=64)
        setup_world()
        add_backdrop(location, (0, 0, -0.20), size=8.0, distance=5.0)
        add_lights(target=(0, 0, -0.20))
        add_camera(location, ortho_scale=1.60, rotation=rotation)
        M.build_wire(length=1.30)
        M.build_robot()
        payload = render(f"three-views-{key}", scene, {}, f"{label} {dims}")
        payload["label"] = label
        payload["dims"] = dims
        outputs.append(payload)
    with open(os.path.join(PLATES, "three-views.json"), "w", encoding="utf-8") as handle:
        json.dump({"views": outputs}, handle, ensure_ascii=False, indent=2)
    return outputs


BUILDERS = {
    "cross-section": figure_cross_section,
    "docking": figure_docking,
    "three-views": figure_three_views,
}


def main():
    selected = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    os.makedirs(PLATES, exist_ok=True)
    os.chdir(ROOT)
    for name, builder in BUILDERS.items():
        if selected and name not in selected:
            continue
        M.reset_scene()
        builder()
    print("[done] plates written to", PLATES)


if __name__ == "__main__":
    main()
