import bpy, json
from pathlib import Path
root=Path.cwd()
blend_meshes=[o for o in bpy.context.scene.objects if o.type=='MESH']
assert blend_meshes and all(len(o.data.vertices)>0 for o in blend_meshes)
assert bpy.context.scene.unit_settings.system=='METRIC'
result={'blendOpened':True,'meshObjects':len(blend_meshes),'units':'meter','blenderVersion':bpy.app.version_string}
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(root/'model/LineGuard.glb'))
glb_meshes=[o for o in bpy.context.scene.objects if o.type=='MESH']
assert len(glb_meshes)==len(blend_meshes)
assert all(len(o.data.vertices)>0 for o in glb_meshes)
result.update({'glbImported':True,'glbMeshObjects':len(glb_meshes),'passed':True})
(root/'.lineguard_qa/course/model-verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
