<script setup lang="ts">
// 设备图形符号：用线条图标代替字母方块，颜色随父级 color 变化。
withDefaults(defineProps<{ type: 'ROBOT' | 'DOCK'; size?: number }>(), { size: 22 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <template v-if="type === 'ROBOT'">
      <!-- 与三维模型同一套构型：两个夹持站点各有一对上下 V 槽轮夹住地线，机体吊在下方 -->
      <line x1="1.6" y1="8" x2="22.4" y2="8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.45"/>
      <g stroke="currentColor" stroke-width="1.5" fill="none">
        <path v-for="cx in [8, 16]" :key="`post${cx}`" :d="`M${cx} 5.4v5.2`" opacity="0.7"/>
        <circle v-for="cx in [8, 16]" :key="`up${cx}`" :cx="cx" cy="5.4" r="2.3"/>
        <circle v-for="cx in [8, 16]" :key="`down${cx}`" :cx="cx" cy="10.6" r="2.3"/>
      </g>
      <rect x="4" y="15.2" width="16" height="6.6" rx="2.2" stroke="currentColor" stroke-width="1.7"/>
    </template>
    <template v-else>
      <line x1="2" y1="7" x2="22" y2="7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" opacity="0.5"/>
      <path d="M5 7.6v2.2a3 3 0 0 0 3 3h4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
      <rect x="7.5" y="11.6" width="11.5" height="8.4" rx="2.2" stroke="currentColor" stroke-width="1.7"/>
      <path d="M10 15.2h7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      <path d="M17 11.6l3.4-2.6v3.2L17 15z" fill="currentColor" opacity="0.8"/>
    </template>
  </svg>
</template>
