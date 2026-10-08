"""复用同一套机器人参数，导出课程展示用的 Blender、GLB 与论文插图。"""
from pathlib import Path
import sys
import math
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import lineguard_model as model

OUT = ROOT / "model"
OUT.mkdir(exist_ok=True)
model.reset_scene()
robot, _ = model.build_robot()
for obj in robot:
    obj.location.x -= 1.0
model.build_dock(lock_closed=False, canopy=False)
model.build_wire(length=3.3, location=(-0.45, 0, 0))
scene = bpy.context.scene
scene.unit_settings.system = "METRIC"
scene.unit_settings.scale_length = 1.0
scene.render.engine = "BLENDER_EEVEE"
scene.render.resolution_x = 1800
scene.render.resolution_y = 720
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.view_settings.view_transform = "AgX"
world = bpy.data.worlds.new("Course studio")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.82, 0.88, 0.93, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.7
scene.world = world
scene.render.film_transparent = True

def aim(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()

target = (-0.4, 0, -0.16)
for name, location, power, size in [
    ("Key", (-2, -3, 4), 450, 4),
    ("Fill", (2, -1, 2), 240, 3),
    ("Rim", (0, 3, 3), 500, 3),
]:
    light = bpy.data.lights.new(name, "AREA")
    light.energy, light.shape, light.size = power, "DISK", size
    obj = bpy.data.objects.new(name, light)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    aim(obj, target)
camera = bpy.data.cameras.new("展示相机")
camera.type, camera.ortho_scale = "ORTHO", 3.8
cam = bpy.data.objects.new("展示相机", camera)
bpy.context.collection.objects.link(cam)
cam.location = (-2.0, -5.0, 2.6)
aim(cam, target)
scene.camera = cam
for area in bpy.context.screen.areas if bpy.context.screen else []:
    if area.type == "VIEW_3D":
        area.spaces.active.region_3d.view_perspective = "CAMERA"
scene["用途"] = "课程软件架构说明用概念模型，未进行强度、摩擦或电气安全验证"
scene["作者"] = "张立烁 2024303524"

# GLB 只导出模型；灯光与相机留在可编辑的 Blender 文件。
bpy.ops.object.select_all(action="DESELECT")
for obj in scene.objects:
    if obj.type == "MESH":
        obj.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(OUT / "LineGuard.glb"), export_format="GLB", use_selection=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "LineGuard.blend"))
render_path = ROOT / ".lineguard_qa/assets/course-model-raw.png"
render_path.parent.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(render_path)
bpy.ops.render.render(write_still=True)
print("课程模型导出完成", OUT)
