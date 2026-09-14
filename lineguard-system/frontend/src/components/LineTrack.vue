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

const robotPercent = computed(() => Math.max(0, Math.min(100,
  (props.robotPosition / Math.max(props.lengthMeters, 1)) * 100)))

function pointPercent(position: number) {
  return `${(position / Math.max(props.lengthMeters, 1)) * 100}%`
}
</script>

<template>
  <div class="line-track-panel">
    <div class="track-header">
      <div>
        <span class="eyebrow">LIVE POSITION</span>
        <h3>单档距运行视图</h3>
      </div>
      <div class="distance-readout">
        <strong>{{ Math.round(robotPosition) }}</strong>
        <span>/ {{ lengthMeters }} m</span>
      </div>
    </div>

    <div class="track-stage">
      <div class="mountain mountain-a"></div>
      <div class="mountain mountain-b"></div>
      <div class="tower tower-left"><i></i><i></i><i></i></div>
      <div class="tower tower-right"><i></i><i></i><i></i></div>
      <div class="ground-wire"></div>
      <div class="dock-node">
        <span class="dock-core">D</span>
        <small>Dock · 0 m</small>
      </div>

      <div
        v-for="point in points"
        :key="point.id"
        class="inspection-marker"
        :class="{ target: point.id === targetPointId }"
        :style="{ left: pointPercent(point.positionMeters) }"
      >
        <span></span>
        <small>{{ point.positionMeters }} m</small>
      </div>

      <div class="robot-marker" :class="{ offline: !robotOnline }" :style="{ left: `${robotPercent}%` }">
        <div class="robot-wheels"><i></i><i></i></div>
        <div class="robot-body">LG</div>
        <small>Robot</small>
      </div>
    </div>

    <div class="track-legend">
      <span><i class="legend-dot cyan"></i>Robot 实时位置</span>
      <span><i class="legend-dot amber"></i>本次检测目标</span>
      <span><i class="legend-line"></i>OPGW 模拟线路</span>
    </div>
  </div>
</template>

