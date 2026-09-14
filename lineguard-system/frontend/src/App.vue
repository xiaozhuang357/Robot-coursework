<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useLineGuardStore } from './stores/lineguard'

const route = useRoute()
const store = useLineGuardStore()

const navigation = [
  { name: 'console', path: '/', code: '01', label: '任务控制台', caption: '核心巡检闭环' },
  { name: 'history', path: '/history', code: '02', label: '历史任务', caption: '任务记录与复核' },
  { name: 'devices', path: '/devices', code: '03', label: '设备状态', caption: 'Robot 与 Dock' },
]
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div>
          <strong>LineGuard</strong>
          <small>智能运维平台</small>
        </div>
      </div>

      <div class="system-label">MISSION SYSTEM / V1</div>
      <nav class="main-nav">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.name === item.name }"
        >
          <span class="nav-code">{{ item.code }}</span>
          <span class="nav-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.caption }}</small>
          </span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <div class="status-row">
          <span class="pulse" :class="{ muted: !store.realtimeConnected }"></span>
          <span>{{ store.realtimeConnected ? '实时链路已连接' : '等待平台连接' }}</span>
        </div>
        <p>单档距核心闭环仿真环境</p>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <span class="eyebrow">TRANSMISSION LINE ROBOTICS</span>
          <h1>{{ navigation.find(item => item.name === route.name)?.label }}</h1>
        </div>
        <div class="topbar-meta">
          <span>云岭示范区段</span>
          <b>非带电仿真</b>
        </div>
      </header>
      <div v-if="store.error" class="global-error">
        <strong>操作提示</strong>
        <span>{{ store.error }}</span>
      </div>
      <RouterView />
    </main>
  </div>
</template>

