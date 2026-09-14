<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useLineGuardStore } from '../stores/lineguard'
import { stateLabels } from '../types'

const store = useLineGuardStore()
const segment = computed(() => store.segments[0] ?? null)

onMounted(async () => {
  if (!store.segments.length) await store.initialize()
  else await store.refreshLive()
})

function formatTime(value?: string) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <div class="page-content devices-page" v-loading="store.loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">DEVICE READINESS</span>
        <h2>Robot 与 Dock 设备状态</h2>
        <p>设备可用性直接决定任务是否能够通过预检。</p>
      </div>
    </div>

    <div class="device-grid">
      <section v-for="device in store.devices" :key="device.id" class="section-card device-card">
        <div class="device-card-head">
          <div class="large-symbol" :class="device.type.toLowerCase()">{{ device.type === 'ROBOT' ? 'R' : 'D' }}</div>
          <div><span class="eyebrow">{{ device.type }}</span><h3>{{ device.name }}</h3><small class="mono">{{ device.id }}</small></div>
          <div class="online-state" :class="{ online: device.online }"><i></i>{{ device.online ? 'ONLINE' : 'OFFLINE' }}</div>
        </div>
        <div class="device-metrics">
          <div><span>运行状态</span><strong>{{ stateLabels[device.state] ?? device.state }}</strong></div>
          <div><span>剩余电量</span><strong>{{ device.batteryPercent }}%</strong></div>
          <div><span>线路位置</span><strong>{{ Math.round(device.positionMeters) }} m</strong></div>
          <div><span>最近上报</span><strong>{{ formatTime(device.updatedAt) }}</strong></div>
        </div>
        <div class="device-message">{{ device.statusMessage }}</div>
      </section>
    </div>

    <div class="lower-grid">
      <section class="section-card readiness-card">
        <div class="card-header"><div><h3>任务预检条件</h3><p>首阶段固定检查项</p></div></div>
        <div class="readiness-list">
          <div><i :class="{ pass: store.robot?.online }"></i><span>Robot 通信在线</span><b>{{ store.robot?.online ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: store.dock?.online }"></i><span>Dock 通信在线</span><b>{{ store.dock?.online ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: store.robot?.state === 'DOCKED' }"></i><span>Robot 已安全驻留</span><b>{{ store.robot?.state === 'DOCKED' ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: (store.robot?.batteryPercent ?? 0) >= 40 }"></i><span>Robot 电量不低于 40%</span><b>{{ (store.robot?.batteryPercent ?? 0) >= 40 ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: store.dock?.state === 'LOCKED_CHARGING' }"></i><span>Dock 锁止与补能确认</span><b>{{ store.dock?.state === 'LOCKED_CHARGING' ? '通过' : '未通过' }}</b></div>
        </div>
      </section>

      <section class="section-card asset-card">
        <div class="card-header"><div><h3>{{ segment?.name ?? '示范线路' }}</h3><p>{{ segment?.description }}</p></div><strong>{{ segment?.lengthMeters ?? 0 }} m</strong></div>
        <div class="asset-points">
          <div v-for="point in segment?.inspectionPoints ?? []" :key="point.id">
            <span class="point-position">{{ point.positionMeters }} m</span>
            <div><strong>{{ point.name }}</strong><small>{{ point.defaultInspectionItem }}</small></div>
            <span class="mono point-id">{{ point.id }}</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.device-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-bottom: 18px; }
.device-card { padding: 22px; }
.device-card-head { display: grid; grid-template-columns: 54px 1fr auto; gap: 14px; align-items: center; padding-bottom: 19px; border-bottom: 1px solid #e1e9eb; }
.large-symbol { display: grid; place-items: center; width: 52px; height: 52px; color: #fff; font: 17px/1 "JetBrains Mono", monospace; }.large-symbol.robot { background: #098e9b; }.large-symbol.dock { background: #1d4555; }
.device-card-head h3 { margin: 5px 0 3px; font-size: 15px; }.device-card-head small { color: #8b9ba1; font-size: 9px; }
.online-state { display: flex; align-items: center; gap: 7px; color: #9a6a1d; font: 9px/1 "JetBrains Mono", monospace; }.online-state i { width: 7px; height: 7px; border-radius: 50%; background: #d5a241; }.online-state.online { color: #2e8a52; }.online-state.online i { background: #3bc174; box-shadow: 0 0 0 4px rgba(59,193,116,.12); }
.device-metrics { display: grid; grid-template-columns: repeat(4, 1fr); padding: 18px 0; }
.device-metrics div { padding: 0 13px; border-right: 1px solid #e2e9eb; }.device-metrics div:first-child { padding-left: 0; }.device-metrics div:last-child { border: 0; }
.device-metrics span, .device-metrics strong { display: block; }.device-metrics span { margin-bottom: 7px; color: #81949b; font-size: 9px; }.device-metrics strong { color: #29414b; font-size: 11px; }
.device-message { padding: 10px 12px; color: #5e747d; background: #f3f8f9; font-size: 10px; }
.lower-grid { display: grid; grid-template-columns: .85fr 1.15fr; gap: 18px; }
.readiness-list > div { padding: 14px 18px; display: grid; grid-template-columns: 15px 1fr auto; gap: 10px; align-items: center; border-bottom: 1px solid #e5ecee; color: #536a74; font-size: 11px; }
.readiness-list > div:last-child { border: 0; }.readiness-list i { width: 8px; height: 8px; border-radius: 50%; background: #dd5a55; }.readiness-list i.pass { background: #3bbd71; }.readiness-list b { color: #7b8f97; font-size: 9px; }
.asset-card .card-header > strong { color: #087f8c; font: 20px/1 "JetBrains Mono", monospace; }
.asset-points > div { padding: 16px 18px; display: grid; grid-template-columns: 70px 1fr auto; gap: 13px; align-items: center; border-bottom: 1px solid #e5ecee; }
.asset-points > div:last-child { border: 0; }.point-position { color: #087f8c; font: 12px/1 "JetBrains Mono", monospace; }.asset-points strong, .asset-points small { display: block; }.asset-points strong { color: #2d444e; font-size: 11px; }.asset-points small { margin-top: 5px; color: #82939a; font-size: 9px; }.point-id { color: #a1adb1; font-size: 8px; }
</style>

