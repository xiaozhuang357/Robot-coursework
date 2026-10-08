<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { CSS2DObject, CSS2DRenderer } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
import robotModel from '../model/lineguard-parts.json'
import type { InspectionPoint } from '../types'

const props = defineProps<{
  lengthMeters: number
  points: InspectionPoint[]
  robotPosition: number
  targetPointId?: string
  robotOnline: boolean
}>()

// 世界单位 = 米。与二维工程视图共用同一套悬链线参数，两个视图里程一致。
const SPAN = 1000
const WIRE_HEIGHT = 60
const SAG = 16
const ARM_LEVELS = [
  { y: 44, inner: 3.4, tip: 7, sag: 30 },
  { y: 34, inner: 4.4, tip: 8, sag: 34 },
  { y: 24, inner: 5.4, tip: 9, sag: 40 },
]

// 这段档距有一千米，按真实比例画的话地线只有 20 mm 粗、远看就是一条断线，
// 所以把细小构件统一放大：地线按 WIRE_RADIUS_SHOWN 显示，机体按同一倍率放大，
// 这样"轮槽夹住地线"的比例关系仍然是真的，只有与杆塔的相对大小被夸张了。
const WIRE_RADIUS_SHOWN = 0.035
const ROBOT_SCALE = WIRE_RADIUS_SHOWN / (robotModel.wireRadius as number)
// 机体吊在地线下约 0.77 m，视点跟着下沉同样的量，画面才不会被上半部空掉
const TARGET_DROP = 0.42

type Part = {
  kind: 'box' | 'cylinder' | 'cone' | 'torus' | 'sphere'
  name: string
  material: string | null
  pos: [number, number, number]
  size?: [number, number, number]
  quat?: [number, number, number, number]
  axis?: 'x' | 'y' | 'z'
  radius?: number
  tube?: number
  rPos?: number
  rNeg?: number
  depth?: number
}

const AXIS_VECTOR = {
  x: new THREE.Vector3(1, 0, 0),
  y: new THREE.Vector3(0, 1, 0),
  z: new THREE.Vector3(0, 0, 1),
}
// three.js 里圆柱的轴向是局部 +Y、圆环的轴向是局部 +Z，先把标准轴向对到构件轴向，
// 半径的正负端由导出时的 rPos / rNeg 决定，不需要在这里判断朝向。
const CANONICAL_AXIS = {
  cylinder: new THREE.Vector3(0, 1, 0),
  cone: new THREE.Vector3(0, 1, 0),
  torus: new THREE.Vector3(0, 0, 1),
}

const host = ref<HTMLDivElement | null>(null)
const renderer = shallowRef<THREE.WebGLRenderer | null>(null)
const robotGroup = shallowRef<THREE.Group | null>(null)
const failed = ref(false)

function ratio(position: number) {
  return Math.max(0, Math.min(1, position / Math.max(props.lengthMeters, 1)))
}

function xAt(position: number) {
  return ratio(position) * SPAN
}

// 与二维视图同一条曲线：y(t) = WIRE_HEIGHT - 4·SAG·t·(1-t)
function yAt(position: number) {
  const t = ratio(position)
  return WIRE_HEIGHT - 4 * SAG * t * (1 - t)
}

function wireCurve(fromX: number, toX: number, baseY: number, sag: number) {
  return new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(fromX, baseY, 0),
    new THREE.Vector3((fromX + toX) / 2, baseY - 2 * sag, 0),
    new THREE.Vector3(toX, baseY, 0),
  )
}

/** 用同一根单位立方体拼装所有钢构件，避免为每根杆件新建几何体。 */
function buildMember(unit: THREE.BoxGeometry, material: THREE.Material,
                     from: THREE.Vector3, to: THREE.Vector3, thickness: number) {
  const direction = new THREE.Vector3().subVectors(to, from)
  const length = direction.length()
  const mesh = new THREE.Mesh(unit, material)
  mesh.scale.set(thickness, length, thickness)
  mesh.position.copy(from).addScaledVector(direction, 0.5)
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.normalize())
  return mesh
}

function buildTower(unit: THREE.BoxGeometry, steel: THREE.Material, x: number) {
  const tower = new THREE.Group()
  tower.position.x = x
  const levels = [
    { y: 0, halfX: 7, halfZ: 4 },
    { y: 24, halfX: 5.4, halfZ: 3.1 },
    { y: 34, halfX: 4.4, halfZ: 2.6 },
    { y: 44, halfX: 3.4, halfZ: 2.1 },
    { y: 54, halfX: 2.6, halfZ: 1.7 },
    { y: WIRE_HEIGHT, halfX: 2.2, halfZ: 1.5 },
  ]
  const ring = (level: { halfX: number; halfZ: number }, y: number) => [
    new THREE.Vector3(-level.halfX, y, -level.halfZ),
    new THREE.Vector3(level.halfX, y, -level.halfZ),
    new THREE.Vector3(level.halfX, y, level.halfZ),
    new THREE.Vector3(-level.halfX, y, level.halfZ),
  ]
  for (let corner = 0; corner < 4; corner += 1) {
    tower.add(buildMember(unit, steel,
      ring(levels[0], levels[0].y)[corner], ring(levels[levels.length - 1], WIRE_HEIGHT)[corner], 0.34))
  }
  for (let index = 1; index < levels.length; index += 1) {
    const current = ring(levels[index], levels[index].y)
    const below = ring(levels[index - 1], levels[index - 1].y)
    for (let corner = 0; corner < 4; corner += 1) {
      tower.add(buildMember(unit, steel, current[corner], current[(corner + 1) % 4], 0.18))
      if (index <= 2) {
        tower.add(buildMember(unit, steel, below[corner], current[(corner + 1) % 4], 0.14))
        tower.add(buildMember(unit, steel, current[corner], below[(corner + 1) % 4], 0.14))
      }
    }
  }
  ARM_LEVELS.forEach(arm => {
    for (const side of [-1, 1] as const) {
      const inner = new THREE.Vector3(side * arm.inner, arm.y, 0)
      const tip = new THREE.Vector3(side * arm.tip * 1.5, arm.y, 0)
      tower.add(buildMember(unit, steel, inner, tip, 0.26))
      tower.add(buildMember(unit, steel, tip, new THREE.Vector3(tip.x - side * 0.9, arm.y - 3, 0), 0.16))
      tower.add(buildMember(unit, steel, tip, new THREE.Vector3(tip.x, arm.y - 1.2, 0), 0.2))
    }
  })
  return tower
}

/** 材质按名字缓存，Robot 与 Dock 共用一份：它们的材质表本来就来自同一个模型。 */
function createMaterials() {
  const cache = new Map<string, THREE.MeshStandardMaterial>()
  const table = robotModel.materials as Record<
    string, { color: number[]; metalness: number; roughness: number }>
  return (name: string | null) => {
    const key = name ?? 'Shell'
    const hit = cache.get(key)
    if (hit) return hit
    const spec = table[key] ?? table.Shell
    const material = new THREE.MeshStandardMaterial({
      color: new THREE.Color().setRGB(spec.color[0], spec.color[1], spec.color[2]),
      metalness: spec.metalness,
      roughness: spec.roughness,
    })
    cache.set(key, material)
    return material
  }
}

/** 直接渲染 tools/blender/export_parts.py 导出的构件表。
 *
 * 演示场景不再自己描述摆位：机构一改，插图和这里一起变，不会出现
 * "文档里是夹持式、演示里还是轮式"这种两边说法不一致的问题。
 */
function buildModel(
  parts: Part[],
  materialOf: (name: string | null) => THREE.MeshStandardMaterial,
) {
  const group = new THREE.Group()
  group.scale.setScalar(ROBOT_SCALE)

  for (const part of parts) {
    let geometry: THREE.BufferGeometry
    if (part.kind === 'box') {
      geometry = new THREE.BoxGeometry(...(part.size as [number, number, number]))
    } else if (part.kind === 'sphere') {
      geometry = new THREE.SphereGeometry(part.radius as number, 16, 12)
    } else if (part.kind === 'torus') {
      geometry = new THREE.TorusGeometry(part.radius as number, part.tube as number, 10, 28)
    } else {
      // three.js 的 CylinderGeometry 本身就是锥台，上下半径不同即为轮槽法兰
      geometry = new THREE.CylinderGeometry(
        part.rPos as number, part.rNeg as number, part.depth as number, 24)
    }
    const mesh = new THREE.Mesh(geometry, materialOf(part.material))
    mesh.position.set(...(part.pos as [number, number, number]))
    if (part.kind === 'box') {
      const [x, y, z, w] = part.quat as [number, number, number, number]
      mesh.quaternion.set(x, y, z, w)
    } else if (part.kind !== 'sphere') {
      mesh.quaternion.setFromUnitVectors(
        CANONICAL_AXIS[part.kind], AXIS_VECTOR[part.axis as 'x' | 'y' | 'z'])
    }
    group.add(mesh)
  }
  return group
}

function buildRidge(color: number, z: number, height: number, seed: number) {
  const shape = new THREE.Shape()
  shape.moveTo(-1600, 0)
  for (let index = 0; index <= 14; index += 1) {
    const x = -1600 + index * (3200 / 14)
    const peak = Math.abs(Math.sin(index * 1.7 + seed)) * height
    const dip = Math.abs(Math.sin(index * 3.3 + seed)) * height * 0.32
    shape.lineTo(x, 6 + peak - dip)
  }
  shape.lineTo(1600, 0)
  shape.closePath()
  const mesh = new THREE.Mesh(
    new THREE.ExtrudeGeometry(shape, { depth: 8, bevelEnabled: false }),
    new THREE.MeshStandardMaterial({ color, metalness: 0.04, roughness: 0.96, flatShading: true }),
  )
  mesh.position.z = z
  return mesh
}

function buildSky() {
  return new THREE.Mesh(
    new THREE.SphereGeometry(2600, 24, 16),
    new THREE.ShaderMaterial({
      side: THREE.BackSide,
      depthWrite: false,
      uniforms: {
        topColor: { value: new THREE.Color(0xa9c8de) },
        bottomColor: { value: new THREE.Color(0xf4f9fa) },
      },
      vertexShader: `
        varying vec3 vWorld;
        void main() {
          vWorld = (modelMatrix * vec4(position, 1.0)).xyz;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }`,
      fragmentShader: `
        uniform vec3 topColor;
        uniform vec3 bottomColor;
        varying vec3 vWorld;
        void main() {
          float h = clamp((normalize(vWorld).y + 0.12) / 0.7, 0.0, 1.0);
          gl_FragColor = vec4(mix(bottomColor, topColor, pow(h, 0.85)), 1.0);
        }`,
    }),
  )
}

const LABEL_PALETTES = {
  ink: { color: '#3f5b65', background: 'rgba(255,255,255,0.94)', border: '#c8d6da' },
  target: { color: '#9a6812', background: '#fff6e2', border: '#e5bc6c' },
  robot: { color: '#ffffff', background: 'rgba(11,124,142,0.95)', border: 'rgba(11,124,142,0.95)' },
}

function applyLabelTone(element: HTMLDivElement, tone: 'ink' | 'target' | 'robot') {
  const palette = LABEL_PALETTES[tone]
  Object.assign(element.style, {
    padding: '3px 9px',
    borderRadius: '10px',
    border: `1px solid ${palette.border}`,
    background: palette.background,
    color: palette.color,
    fontSize: '12px',
    fontWeight: '600',
    lineHeight: '1.4',
    whiteSpace: 'nowrap',
    fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
    boxShadow: '0 2px 6px rgba(12,40,52,.14)',
  })
}

function labelElement(text: string, tone: 'ink' | 'target' | 'robot') {
  const element = document.createElement('div')
  element.textContent = text
  applyLabelTone(element, tone)
  return element
}

let cleanup: (() => void) | null = null

onMounted(() => {
  const canvasHost = host.value
  if (!canvasHost) return

  let webgl: THREE.WebGLRenderer
  try {
    webgl = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  } catch (error) {
    failed.value = true
    console.warn('WebGL 初始化失败，已退回工程视图', error)
    return
  }

  const unit = new THREE.BoxGeometry(1, 1, 1)
  const scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0xd7e5eb, 260, 3600)

  const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 12000)
  webgl.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  webgl.outputColorSpace = THREE.SRGBColorSpace
  webgl.toneMapping = THREE.ACESFilmicToneMapping
  webgl.toneMappingExposure = 1.3
  canvasHost.appendChild(webgl.domElement)
  renderer.value = webgl

  const labels = new CSS2DRenderer()
  Object.assign(labels.domElement.style, { position: 'absolute', inset: '0', pointerEvents: 'none' })
  canvasHost.appendChild(labels.domElement)

  scene.add(buildSky())
  // 机体材质用的是渲染插图时那套线性基色（偏灰、偏深），按"标准"光照会糊成一团黑，
  // 所以这里补足环境光并抬一点曝光，让它在这种偏记录片式的场景里仍然能看清结构
  scene.add(new THREE.HemisphereLight(0xe8f3f7, 0x7d8f86, 1.55))
  const sun = new THREE.DirectionalLight(0xfff6e4, 2.0)
  sun.position.set(320, 420, 260)
  scene.add(sun)
  // 侧后方补一盏弱光：机体上的控制盒、电机、齿轮箱都是接近黑的深色件，
  // 只靠顶光会糊成一片剪影，看不出结构
  const fill = new THREE.DirectionalLight(0xdfeaf2, 0.85)
  fill.position.set(-260, 180, -340)
  scene.add(fill)
  scene.add(new THREE.AmbientLight(0xffffff, 0.45))

  // 远山：由近及远逐层变淡，形成空气透视，同时留出地平线与天空
  const ridgeSpec = [
    { color: 0x7c9c94, z: -520, height: 58, seed: 1.1 },
    { color: 0x87a79f, z: -900, height: 76, seed: 5.6 },
    { color: 0x93ada2, z: -1400, height: 92, seed: 0.4 },
    { color: 0x9db3b8, z: -2200, height: 116, seed: 2.1 },
    { color: 0xa7bac2, z: -3200, height: 142, seed: 4.3 },
  ]
  ridgeSpec.forEach(spec => scene.add(buildRidge(spec.color, spec.z, spec.height, spec.seed)))

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(9000, 7000),
    new THREE.MeshStandardMaterial({ color: 0x7b9484, metalness: 0, roughness: 1 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.set(SPAN / 2, 0, -1800)
  scene.add(ground)

  const steel = new THREE.MeshStandardMaterial({ color: 0x8095a0, metalness: 0.6, roughness: 0.46 })
  const wire = new THREE.MeshStandardMaterial({ color: 0xb6c0c5, metalness: 0.35, roughness: 0.45 })
  scene.add(buildTower(unit, steel, 0))
  scene.add(buildTower(unit, steel, SPAN))

  // 地线：与二维工程视图完全同一条曲线（线径放大到 WIRE_RADIUS_SHOWN 以便远距离可辨）
  const groundWire = new THREE.Mesh(
    new THREE.TubeGeometry(wireCurve(0, SPAN, WIRE_HEIGHT, SAG), 260, WIRE_RADIUS_SHOWN, 8, false), wire)
  scene.add(groundWire)
  ARM_LEVELS.forEach(arm => {
    const inset = arm.tip * 1.5
    scene.add(new THREE.Mesh(
      new THREE.TubeGeometry(
        wireCurve(inset, SPAN - inset, arm.y, arm.sag), 200, WIRE_RADIUS_SHOWN * 0.68, 7, false), wire))
  })

  const materialOf = createMaterials()
  const robot = buildModel(robotModel.robot as Part[], materialOf)
  // 在线状态要有可见的落点：让琥珀色指示灯自发光，离线时暗下去
  const indicator = materialOf('Amber')
  indicator.emissive.copy(indicator.color)
  robot.userData.indicator = indicator
  scene.add(robot)
  robotGroup.value = robot

  // 驻留坞同样来自导出的构件表，原点也在导线上，直接摆到档距起点
  const dock = buildModel(robotModel.dock as Part[], materialOf)
  dock.position.set(0, WIRE_HEIGHT, 0)
  scene.add(dock)

  // 检测点：线上放一个标记环，标签才有落点，否则文字会孤零零浮在天上。
  // 目标点必须跟着 props 走：只在挂载时读一次的话，创建任务后三维视图里所有点仍是灰环，
  // 而二维工程视图已经高亮，两个视图会对同一份数据给出相反的结论。
  const markerGeometry = new THREE.TorusGeometry(WIRE_RADIUS_SHOWN * 2.6, WIRE_RADIUS_SHOWN * 0.5, 8, 24)
  type PointMarker = { id: string; marker: THREE.Mesh; label: CSS2DObject; element: HTMLDivElement }
  let pointMarkers: PointMarker[] = []

  function buildPointMarkers() {
    pointMarkers.forEach(item => {
      scene.remove(item.marker)
      scene.remove(item.label)
      ;(item.marker.material as THREE.Material).dispose()
      item.element.remove()
    })
    pointMarkers = props.points.map(point => {
      const marker = new THREE.Mesh(markerGeometry, new THREE.MeshStandardMaterial({
        color: 0x8095a0, metalness: 0.3, roughness: 0.5,
      }))
      marker.position.set(xAt(point.positionMeters), yAt(point.positionMeters), 0)
      scene.add(marker)
      const element = labelElement(`${point.positionMeters} m`, 'ink')
      const label = new CSS2DObject(element)
      label.position.set(xAt(point.positionMeters), yAt(point.positionMeters) + 1, 0)
      scene.add(label)
      return { id: point.id, marker, label, element }
    })
  }

  function applyTargetTone() {
    pointMarkers.forEach(item => {
      const target = item.id === props.targetPointId
      const material = item.marker.material as THREE.MeshStandardMaterial
      material.color.set(target ? 0xf0a72f : 0x8095a0)
      material.emissive.set(target ? 0x6b4708 : 0x000000)
      applyLabelTone(item.element, target ? 'target' : 'ink')
    })
  }

  const stopPoints = watch([() => props.targetPointId, () => props.points], () => {
    const samePoints = pointMarkers.length === props.points.length
      && pointMarkers.every((item, index) => item.id === props.points[index].id)
    if (!samePoints) buildPointMarkers()
    applyTargetTone()
  }, { immediate: true })

  const robotLabel = new CSS2DObject(labelElement('Robot', 'robot'))
  robotLabel.position.set(xAt(props.robotPosition), yAt(props.robotPosition) + 1, 0)
  scene.add(robotLabel)

  const controls = new OrbitControls(camera, webgl.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 2.2
  controls.maxDistance = 460
  controls.maxPolarAngle = Math.PI * 0.495
  controls.enablePan = false

  // 视点比地线低一截（机体吊在线下面），相机距离要同时容下机体和驻留坞：
  // 只按机体取景的话，停在坞里时坞的导轨会怼到镜头前，看着像一堆横七竖八的梁。
  const focus = new THREE.Vector3(xAt(props.robotPosition), yAt(props.robotPosition), 0)
  camera.position.set(focus.x + 2.8, focus.y + 2.1, 7.9)
  controls.target.set(focus.x, focus.y - TARGET_DROP, 0)

  const resize = () => {
    const width = canvasHost.clientWidth || 1
    const height = canvasHost.clientHeight || 1
    camera.aspect = width / height
    camera.updateProjectionMatrix()
    webgl.setSize(width, height)
    labels.setSize(width, height)
  }
  resize()
  window.addEventListener('resize', resize)

  const animate = () => {
    controls.update()
    webgl.render(scene, camera)
    labels.render(scene, camera)
    frameRef = window.requestAnimationFrame(animate)
  }
  let frameRef = window.requestAnimationFrame(animate)

  // 机器人位置变化时同步模型与相机：整体平移，保留用户当前的环绕视角
  const stopWatch = watch(() => props.robotPosition, next => {
    const t = ratio(next)
    const x = xAt(next)
    const y = yAt(next)
    robot.position.set(x, y, 0)
    // 依据曲线切线给出俯仰角，避免上坡段出现穿模观感
    const slope = -4 * SAG * (1 - 2 * t) / SPAN
    robot.rotation.z = Math.atan(slope)
    robotLabel.position.set(x, y + 1, 0)
    // 目标点始终比地线低 TARGET_DROP，平移量要和初始化算法保持一致，
    // 否则第一次位置更新会让画面整体跳一下
    const target = new THREE.Vector3(x, y - TARGET_DROP, 0)
    const delta = target.clone().sub(controls.target)
    controls.target.copy(target)
    camera.position.add(delta)
  }, { immediate: true })

  const stopOnline = watch(() => props.robotOnline, online => {
    const indicator = robot.userData.indicator as THREE.MeshStandardMaterial | undefined
    if (indicator) indicator.emissiveIntensity = online ? 0.9 : 0.05
  }, { immediate: true })

  cleanup = () => {
    stopWatch()
    stopOnline()
    stopPoints()
    window.removeEventListener('resize', resize)
    window.cancelAnimationFrame(frameRef)
    controls.dispose()
    // 几何与材质都有共享（塔材复用同一个立方体、机体材质按名字缓存），
    // 逐个 dispose 会重复调用，先去重再释放
    const geometries = new Set<THREE.BufferGeometry>()
    const materials = new Set<THREE.Material>()
    scene.traverse(child => {
      const mesh = child as THREE.Mesh
      if (mesh.geometry) geometries.add(mesh.geometry)
      const material = mesh.material
      if (Array.isArray(material)) material.forEach(item => materials.add(item))
      else if (material) materials.add(material)
    })
    geometries.forEach(item => item.dispose())
    materials.forEach(item => item.dispose())
    webgl.dispose()
    labels.domElement.remove()
    webgl.domElement.remove()
  }
})

onBeforeUnmount(() => {
  cleanup?.()
  cleanup = null
  renderer.value = null
  robotGroup.value = null
})
</script>

<template>
  <div class="span-3d">
    <div class="span-3d__head">
      <div>
        <h3>线路现场 <span class="scene-caption">三维示意</span></h3>
      </div>
      <span class="span-3d__hint">拖动旋转 · 滚轮缩放</span>
    </div>
    <div ref="host" class="span-3d__stage"></div>
    <div v-if="failed" class="span-3d__fallback">当前浏览器不支持 WebGL，请使用工程视图。</div>
    <div class="span-3d__legend">
      <span><i class="dot dot--robot"></i>Robot 实时位置</span>
      <span><i class="dot dot--target"></i>本次检测目标</span>
      <span><i class="line"></i>OPGW 地线与三相导线</span>
    </div>
  </div>
</template>

<style scoped>
.span-3d { background: #fff; border: 1px solid var(--line); border-radius: 7px; overflow: hidden; }
.span-3d__head { padding: 17px 20px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); }
.span-3d__head h3 { margin: 0; font-size: 14px; font-weight: 600; }
.span-3d__hint { color: #8b9ba1; font-size: 11px; }
.span-3d__stage { position: relative; height: 320px; background: #dbe7ec; }
.span-3d__stage :deep(canvas) { display: block; }
.span-3d__legend { padding: 12px 20px; display: flex; gap: 16px; flex-wrap: wrap; color: var(--muted); background: #fbfcf9; border-top: 1px solid #e4ebed; font-size: 11px; }
.span-3d__legend .dot { display: inline-block; width: 7px; height: 7px; margin-right: 6px; border-radius: 50%; }
.dot--robot { background: #0f9bb0; }
.dot--target { background: #f0a72f; }
.span-3d__legend .line { display: inline-block; width: 18px; height: 2px; margin: 0 6px 3px 0; background: #5d757d; }
.span-3d__fallback { padding: 40px 20px; text-align: center; color: var(--muted); font-size: 12px; }
.scene-caption { margin-left: 8px; color: var(--muted); font-size: 11px; font-weight: 400; }
@media (max-width: 760px) {
 .span-3d__head { padding: 16px; }
 .span-3d__legend { padding: 12px 16px; gap: 10px 16px; font-size: 10px; }
 .span-3d__stage { height: 300px; }
 .span-3d__hint { font-size: 10px; }
}
</style>
