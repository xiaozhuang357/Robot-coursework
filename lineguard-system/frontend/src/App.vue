<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useLineGuardStore } from './stores/lineguard'

const route = useRoute()
const store = useLineGuardStore()

const realtimeLabel = computed(() => ({
  online: '实时链路已连接',
  connecting: '等待平台连接',
  reconnecting: '实时链路中断，正在重连',
}[store.realtimeStatus]))

const navigation = [
  { name: 'console', path: '/', label: '任务控制台', icon: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z' },
  { name: 'history', path: '/history', label: '历史任务', icon: 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h4' },
  { name: 'devices', path: '/devices', label: '设备状态', icon: 'M6 7h12v12H6z M9 11h6 M9 15h6 M9 3v4 M15 3v4 M3 11h3 M3 15h3 M18 11h3 M18 15h3 M9 19v2 M15 19v2' },
]
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <svg class="brand-mark" viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M5 6v20h22M13 6v12h14" stroke="currentColor" stroke-width="3"/><circle cx="25" cy="7" r="3" fill="currentColor"/></svg>
        <div>
          <strong>LineGuard</strong>
          <small>线路巡检工作站</small>
        </div>
      </div>

      <div class="workspace-label"><span>当前区段</span><strong>云岭示范区段</strong><small>单档距 · {{ store.segments[0]?.lengthMeters.toLocaleString() ?? '—' }} m</small></div>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.name === item.name }"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="item.icon" /></svg>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <div class="status-row">
          <span class="pulse" :class="{ muted: !store.realtimeConnected }"></span>
          <span>{{ realtimeLabel }}</span>
        </div>
        <p>Robot + Dock 协同巡检<br>课程设计演示环境</p>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <span class="breadcrumb">工作站 <span>/</span></span>
          <h1>{{ navigation.find(item => item.name === route.name)?.label }}</h1>
        </div>
        <div class="topbar-meta">
          <span class="environment-tag"><i></i>非带电仿真</span>
        </div>
      </header>
      <div v-if="store.error" class="global-error">
        <strong>操作提示</strong>
        <span>{{ store.error }}</span>
        <button @click="store.clearError()">关闭</button>
      </div>
      <RouterView />
    </main>
  </div>
</template>
