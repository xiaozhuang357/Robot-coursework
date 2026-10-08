<script setup lang="ts">
import { computed } from 'vue'
import type { InspectionPoint } from '../types'

const props = defineProps<{
  lengthMeters: number
  points: InspectionPoint[]
  robotPosition: number
  targetPointId?: string
  robotOnline: boolean
}>()

// 场景坐标系：地线是一条悬链线，所有元素共用同一套「里程 → 坐标」映射，
// 机器人、检测点、Dock 因此必然落在地线上，不会各自漂移。
const VIEW_W = 1000
const VIEW_H = 300
const TOWER_LEFT = 148
const TOWER_RIGHT = 852
const PEAK_Y = 112
const SAG = 16
const SPAN = TOWER_RIGHT - TOWER_LEFT
const DOCK_X = TOWER_LEFT + 42
const towerXs = [TOWER_LEFT, TOWER_RIGHT]

function ratio(position: number) {
  return Math.max(0, Math.min(1, position / Math.max(props.lengthMeters, 1)))
}

// 与模板中地线路径的二次贝塞尔严格等价：y(t) = PEAK_Y + 4·SAG·t·(1-t)
function wireYAtX(x: number) {
  const t = Math.max(0, Math.min(1, (x - TOWER_LEFT) / SPAN))
  return PEAK_Y + 4 * SAG * t * (1 - t)
}

function xAt(position: number) {
  return TOWER_LEFT + ratio(position) * SPAN
}

function yAt(position: number) {
  const t = ratio(position)
  return PEAK_Y + 4 * SAG * t * (1 - t)
}

const wirePath = computed(() => {
  const cx = (TOWER_LEFT + TOWER_RIGHT) / 2
  return `M ${TOWER_LEFT} ${PEAK_Y} Q ${cx} ${PEAK_Y + 2 * SAG} ${TOWER_RIGHT} ${PEAK_Y}`
})

const robot = computed(() => ({ x: xAt(props.robotPosition), y: yAt(props.robotPosition) }))
const dock = computed(() => ({ x: DOCK_X, y: wireYAtX(DOCK_X) }))

const markers = computed(() => props.points.map(point => ({
  ...point,
  cx: xAt(point.positionMeters),
  cy: yAt(point.positionMeters),
  target: point.id === props.targetPointId,
})))

// 已行进里程：与地线同一条曲线，只取到机器人当前位置
const progressPath = computed(() => {
  const cx = (TOWER_LEFT + robot.value.x) / 2
  const ctrl = PEAK_Y + 4 * SAG * ratio(props.robotPosition) * 0.5
  return `M ${TOWER_LEFT} ${PEAK_Y} Q ${cx} ${ctrl} ${robot.value.x} ${robot.value.y}`
})

// 杆塔：腿部半宽随高度线性收窄，横担长度按腿部宽度推导，避免横担与斜撑脱节
const legLevels = [
  { y: 266, half: 40 },
  { y: 250, half: 37.1 },
  { y: 214, half: 30.5 },
  { y: 176, half: 23.6 },
  { y: 138, half: 16.7 },
  { y: 112, half: 12 },
]
const arms = [
  { y: 214, leg: 30.5, tip: 70, braceEndY: 250, braceEndHalf: 34, sag: 13 },
  { y: 176, leg: 23.6, tip: 58, braceEndY: 214, braceEndHalf: 27, sag: 26 },
  { y: 138, leg: 16.7, tip: 46, braceEndY: 176, braceEndHalf: 21, sag: 36 },
]

// 导线：挂在三组横担内侧，垂度逐层放大但保证最低点不落到地形之下
const conductors = arms.map(arm => ({
  key: `c${arm.y}`,
  d: `M ${TOWER_LEFT + arm.tip} ${arm.y} Q ${(TOWER_LEFT + TOWER_RIGHT) / 2} ${arm.y + 2 * arm.sag} ${TOWER_RIGHT - arm.tip} ${arm.y}`,
}))
</script>

<template>
  <div class="span-view">
    <div class="span-view__head">
      <div>
        <h3>全档距概览 <span class="scene-caption">工程视图</span></h3>
      </div>
      <div class="distance-readout">
        <strong>{{ Math.round(robotPosition) }}</strong>
        <span>/ {{ lengthMeters }} m</span>
      </div>
    </div>

    <svg class="span-view__scene" :viewBox="`0 0 ${VIEW_W} ${VIEW_H}`" role="img"
         aria-label="单档距仿真线路：机器人沿地线运行，杆塔顶端悬挂地线">
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#cde2ec"/>
          <stop offset="52%" stop-color="#e7f2f5"/>
          <stop offset="100%" stop-color="#f7fbfb"/>
        </linearGradient>
        <radialGradient id="sunGlow" cx="0.8" cy="0.1" r="0.52">
          <stop offset="0%" stop-color="#fff7e2" stop-opacity="0.9"/>
          <stop offset="100%" stop-color="#fff7e2" stop-opacity="0"/>
        </radialGradient>
        <linearGradient id="ridgeFar" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#aec8d0"/>
          <stop offset="100%" stop-color="#d2e2e5"/>
        </linearGradient>
        <linearGradient id="ridgeMid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#8caeb1"/>
          <stop offset="100%" stop-color="#b8cecb"/>
        </linearGradient>
        <linearGradient id="ridgeNear" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#75968e"/>
          <stop offset="100%" stop-color="#9eb9ad"/>
        </linearGradient>
        <linearGradient id="valley" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#7f9d92"/>
          <stop offset="100%" stop-color="#67857b"/>
        </linearGradient>
        <linearGradient id="steel" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#5e757d"/>
          <stop offset="45%" stop-color="#7a939b"/>
          <stop offset="100%" stop-color="#4a616a"/>
        </linearGradient>
        <linearGradient id="chassis" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#4ec1d0"/>
          <stop offset="42%" stop-color="#0f9bb0"/>
          <stop offset="100%" stop-color="#05606f"/>
        </linearGradient>
        <linearGradient id="wheel" x1="0.2" y1="0" x2="0.8" y2="1">
          <stop offset="0%" stop-color="#c9d6da"/>
          <stop offset="100%" stop-color="#6d8790"/>
        </linearGradient>
        <linearGradient id="dockBody" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#f6fafb"/>
          <stop offset="100%" stop-color="#c4d6db"/>
        </linearGradient>
        <linearGradient id="panel" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#1e4759"/>
          <stop offset="100%" stop-color="#0b2734"/>
        </linearGradient>
        <filter id="softShadow" x="-60%" y="-60%" width="220%" height="220%">
          <feDropShadow dx="0" dy="2.5" stdDeviation="3" flood-color="#12333f" flood-opacity="0.3"/>
        </filter>
        <filter id="wireShadow" x="-50%" y="-50%" width="200%" height="200%">
          <feDropShadow dx="0" dy="1.4" stdDeviation="1.1" flood-color="#12333f" flood-opacity="0.32"/>
        </filter>
      </defs>

      <rect x="0" y="0" :width="VIEW_W" :height="VIEW_H" fill="url(#sky)"/>
      <rect x="0" y="0" :width="VIEW_W" :height="VIEW_H" fill="url(#sunGlow)"/>

      <!-- 远景山脊：三层递进形成空气透视 -->
      <path d="M0 196 L96 138 L182 178 L268 122 L356 172 L446 130 L548 182 L636 138 L726 180 L812 132 L900 176 L1000 128 L1000 214 L0 214 Z"
            fill="url(#ridgeFar)"/>
      <path d="M0 214 L112 168 L206 202 L318 158 L424 198 L530 162 L648 200 L762 164 L872 198 L1000 160 L1000 232 L0 232 Z"
            fill="url(#ridgeMid)"/>
      <path d="M0 232 L128 200 L244 226 L372 196 L486 224 L604 198 L728 226 L846 200 L1000 224 L1000 252 L0 252 Z"
            fill="url(#ridgeNear)"/>
      <rect x="0" y="250" :width="VIEW_W" :height="VIEW_H - 250" fill="url(#valley)"/>

      <!-- 三相导线：挂在横担内侧，垂度大于地线，用于说明塔型并制造纵深 -->
      <g fill="none" stroke="#a9bcc2" stroke-width="2.4" opacity="0.9">
        <path v-for="conductor in conductors" :key="conductor.key" :d="conductor.d"/>
      </g>

      <!-- 杆塔：地线悬挂于塔顶绝缘子 -->
      <g v-for="tx in towerXs" :key="tx" :transform="`translate(${tx} 0)`">
        <g stroke="url(#steel)" fill="none" stroke-linecap="round">
          <path :d="`M${-legLevels[0].half} ${legLevels[0].y} L${-legLevels[5].half} ${legLevels[5].y}`" stroke-width="7"/>
          <path :d="`M${legLevels[0].half} ${legLevels[0].y} L${legLevels[5].half} ${legLevels[5].y}`" stroke-width="7"/>
          <path v-for="level in legLevels.slice(1, 5)" :key="`hb${level.y}`"
                :d="`M${-level.half} ${level.y} L${level.half} ${level.y}`" stroke-width="3"/>
          <path v-for="(level, index) in legLevels.slice(1, 2)" :key="`x${level.y}`"
                :d="`M${-level.half} ${level.y} L${legLevels[index + 2].half} ${legLevels[index + 2].y} M${level.half} ${level.y} L${-legLevels[index + 2].half} ${legLevels[index + 2].y}`"
                stroke-width="2.2" opacity="0.45"/>
          <g v-for="arm in arms" :key="`arm${arm.y}`">
            <path :d="`M${-arm.tip} ${arm.y} L${arm.tip} ${arm.y}`" stroke-width="5.5"/>
            <path :d="`M${arm.tip} ${arm.y} L${arm.braceEndHalf} ${arm.braceEndY}`" stroke-width="2.4" opacity="0.85"/>
            <path :d="`M${-arm.tip} ${arm.y} L${-arm.braceEndHalf} ${arm.braceEndY}`" stroke-width="2.4" opacity="0.85"/>
            <path :d="`M${arm.tip} ${arm.y} L${arm.tip} ${arm.y - 9}`" stroke-width="3.6"/>
            <path :d="`M${-arm.tip} ${arm.y} L${-arm.tip} ${arm.y - 9}`" stroke-width="3.6"/>
          </g>
          <path d="M-8 112 L8 112" stroke-width="4"/>
        </g>
        <g>
          <path d="M0 112 L0 121" stroke="#7d949b" stroke-width="1.8"/>
          <ellipse cx="0" cy="125" rx="5.6" ry="3.4" fill="#e2ecef" stroke="#8ea6ad" stroke-width="1.1"/>
          <ellipse cx="0" cy="132" rx="5" ry="3.1" fill="#e2ecef" stroke="#8ea6ad" stroke-width="1.1"/>
          <ellipse cx="0" cy="139" rx="4.4" ry="2.9" fill="#e2ecef" stroke="#8ea6ad" stroke-width="1.1"/>
        </g>
      </g>

      <!-- Dock：驻留坞挂在塔侧地线上，托架连到塔身 -->
      <g :transform="`translate(${dock.x} ${dock.y})`">
        <path d="M-24 12 L-52 26" stroke="#6b848c" stroke-width="3"/>
        <g filter="url(#softShadow)">
          <path d="M-13 -5 Q0 3 13 -5 L13 -12 L-13 -12 Z" fill="#7f979e" stroke="#4e6a73" stroke-width="1.2"/>
          <rect x="-27" y="-12" width="54" height="30" rx="5" fill="url(#dockBody)" stroke="#5b757e" stroke-width="1.7"/>
          <rect x="-27" y="-12" width="54" height="11" rx="4" fill="url(#panel)" stroke="#3d5a66" stroke-width="1.1"/>
          <rect x="-21" y="4" width="42" height="10" rx="2.5" fill="#eef5f7" stroke="#93a9b0" stroke-width="1"/>
          <path d="M24 -12 L46 -22 L46 -13 L24 -3 Z" fill="#1b4a5c" stroke="#41636f" stroke-width="1.1"/>
          <circle cx="-20" cy="-6.5" r="2.4" fill="#4fd18b"/>
        </g>
        <text x="0" y="34" class="scene-label">Dock · 0 m</text>
      </g>

      <!-- 已行进里程与地线本体（同一悬链线） -->
      <path :d="progressPath" fill="none" stroke="#0f9bb0" stroke-width="4.6"
            stroke-linecap="round" opacity="0.92" filter="url(#wireShadow)"/>
      <path :d="wirePath" fill="none" stroke="#5d757d" stroke-width="4.6" stroke-linecap="round"
            filter="url(#wireShadow)"/>

      <!-- 检测点：标签在地线之上，避免与机体相撞 -->
      <g v-for="marker in markers" :key="marker.id" :transform="`translate(${marker.cx} ${marker.cy})`">
        <circle :r="marker.target ? 4.6 : 3.4" :fill="marker.target ? '#f0a72f' : '#7d949b'"
                stroke="#ffffff" stroke-width="1.6"/>
        <path d="M0 -6 L0 -30" :stroke="marker.target ? '#d99a26' : '#a4b7bd'" stroke-width="1.4"
              :stroke-dasharray="marker.target ? '0' : '3 3'"/>
        <rect x="-27" y="-52" width="54" height="19" rx="9.5"
              :fill="marker.target ? '#fff6e2' : '#ffffff'" :stroke="marker.target ? '#e5bc6c' : '#c8d6da'"
              stroke-width="1.2"/>
        <text x="0" y="-38.5" class="scene-chip"
              :class="{ 'scene-chip--target': marker.target }">{{ marker.positionMeters }} m</text>
      </g>

      <!-- Robot：两个夹持站点把地线夹在上下 V 槽轮之间，机体吊在框架下方 -->
      <g :transform="`translate(${robot.x} ${robot.y})`" :class="{ 'is-offline': !robotOnline }">
        <ellipse cx="0" cy="52" rx="34" ry="5.5" fill="#12333f" opacity="0.14"/>
        <g filter="url(#softShadow)">
          <g v-for="cx in [-19, 19]" :key="cx">
            <!-- 夹持框架立柱：把上下轮的夹持反力传回机体 -->
            <rect :x="cx - 3.4" y="-16" width="6.8" height="40" rx="2.4" fill="#4d6b74"/>
            <!-- 下轮装在滑块上，由弹簧自下顶紧 -->
            <path :d="`M${cx - 5} 15 L${cx + 5} 18.5 L${cx - 5} 22 L${cx + 5} 25.5 L${cx - 5} 29`"
                  fill="none" stroke="#5b757e" stroke-width="1.7" stroke-linejoin="round"/>
            <circle :cx="cx" cy="-11" r="8.4" fill="url(#wheel)" stroke="#3f5a63" stroke-width="1.5"/>
            <circle :cx="cx" cy="-11" r="3.2" fill="#2f464e"/>
            <circle :cx="cx" cy="11" r="8.4" fill="url(#wheel)" stroke="#3f5a63" stroke-width="1.5"/>
            <circle :cx="cx" cy="11" r="3.2" fill="#2f464e"/>
          </g>
          <!-- 机体挂在夹持框架下方，配低重心电池 -->
          <path d="M-30 38 Q-30 33 -25 33 L25 33 Q30 33 30 38 L30 56 Q30 61 25 61 L-25 61 Q-30 61 -30 56 Z"
                fill="url(#chassis)" stroke="#05606f" stroke-width="1.6"/>
          <rect x="-21" y="39" width="42" height="5" rx="2.5" fill="#05606f" opacity="0.32"/>
          <rect x="-15" y="50" width="30" height="7" rx="2" fill="#05606f" opacity="0.32"/>
          <!-- 检测云台伸出机体前端 -->
          <circle cx="-35" cy="45" r="5.2" fill="url(#wheel)" stroke="#3f5a63" stroke-width="1.4"/>
          <circle cx="-39" cy="45" r="2" fill="#12333f"/>
          <!-- 通信天线 -->
          <path d="M29 33 L29 -3" stroke="#3f5a63" stroke-width="1.6" stroke-linecap="round"/>
          <circle cx="29" cy="-5.5" r="2.4" fill="#f0a72f"/>
        </g>
        <rect x="-26" y="68" width="52" height="18" rx="9" fill="#0a7c8e" opacity="0.95"/>
        <text x="0" y="81" class="scene-chip scene-chip--robot">Robot</text>
      </g>
    </svg>

    <div class="span-view__legend">
      <span><i class="dot dot--robot"></i>Robot 实时位置</span>
      <span><i class="dot dot--target"></i>本次检测目标</span>
      <span><i class="line"></i>OPGW 模拟线路</span>
      <span><i class="dot dot--dock"></i>驻留 Dock</span>
    </div>
  </div>
</template>

<style scoped>
.span-view { background: #fff; border: 1px solid var(--line); border-radius: 7px; overflow: hidden; }
.span-view__head { padding: 17px 20px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); }
.span-view__head h3 { margin: 0; font-size: 14px; font-weight: 600; }
.span-view__scene { display: block; width: 100%; height: auto; }
.span-view__legend { padding: 12px 20px; display: flex; gap: 16px; flex-wrap: wrap; color: var(--muted); background: #fbfcf9; border-top: 1px solid #e4ebed; font-size: 11px; }
.span-view__legend .dot { display: inline-block; width: 7px; height: 7px; margin-right: 6px; border-radius: 50%; }
.dot--robot { background: #0f9bb0; }
.dot--target { background: #f0a72f; }
.dot--dock { background: #1b4a5c; }
.span-view__legend .line { display: inline-block; width: 18px; height: 2px; margin: 0 6px 3px 0; background: #5d757d; }
.scene-label { font-size: 11px; fill: #3f5b65; font-family: var(--mono); text-anchor: middle; }
.scene-chip { font-size: 13px; font-weight: 600; fill: #3f5b65; text-anchor: middle; }
.scene-chip--target { fill: #9a6812; }
.scene-chip--robot { fill: #ffffff; font-size: 12px; }
.is-offline { opacity: 0.45; filter: grayscale(0.7); }
.scene-caption { margin-left: 8px; color: var(--muted); font-size: 11px; font-weight: 400; }
@media (max-width: 760px) {
 .span-view__head { padding: 16px; }
 .span-view__legend { padding: 12px 16px; gap: 10px 16px; font-size: 10px; }
 .span-3d__stage { height: 300px; }
 .span-3d__hint { font-size: 10px; }
}
</style>
