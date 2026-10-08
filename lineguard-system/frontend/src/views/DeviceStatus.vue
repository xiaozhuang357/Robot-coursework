<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useLineGuardStore } from '../stores/lineguard'
import DeviceGlyph from '../components/DeviceGlyph.vue'
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
        <h2>设备与线路</h2>
        <p>设备可用性直接决定任务是否能够通过预检。</p>
      </div>
    </div>

    <div class="device-grid">
      <section v-for="device in store.devices" :key="device.id" class="section-card device-card">
        <div class="device-card-head">
          <div class="large-symbol" :class="device.type.toLowerCase()">
            <DeviceGlyph :type="device.type" :size="30" />
          </div>
          <div><span class="eyebrow">{{ device.type }}</span><h3>{{ device.name }}</h3><small class="mono">{{ device.id }}</small></div>
          <div class="online-state" :class="{ online: device.online }"><i></i>{{ device.online ? '在线' : '离线' }}</div>
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
        <div class="card-header"><div><h3>任务预检条件</h3><p>新任务启动前，所有条件需同时满足</p></div></div>
        <div class="readiness-list">
          <div><i :class="{ pass: store.robot?.online }"></i><span>Robot 通信在线</span><b>{{ store.robot?.online ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: store.dock?.online }"></i><span>Dock 通信在线</span><b>{{ store.dock?.online ? '通过' : '未通过' }}</b></div>
          <div><i :class="{ pass: store.robot?.state === 'DOCKED' }"></i><span>Robot 已驻留 Dock</span><b>{{ store.robot?.state === 'DOCKED' ? '通过' : '未通过' }}</b></div>
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
.device-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; margin-bottom: 22px; }
.device-card { padding: 24px; }
.device-card-head { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; gap: 14px; align-items: center; padding-bottom: 22px; border-bottom: 1px solid var(--line); }
.large-symbol { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 7px; background: #edf2e7; color: #586e4f; }
.large-symbol.dock { color: #69766a; background: #f0f2ee; }
.device-card-head h3 { margin: 6px 0 4px; font-size: 16px; font-weight: 600; }
.device-card-head small { color: var(--muted); font-size: 11px; }
.online-state { display: flex; align-items: center; gap: 6px; color: #9c7652; font-size: 12px; }
.online-state i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.online-state.online { color: #607e53; }
.device-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 26px 16px; padding: 24px 0; }
.device-metrics span, .device-metrics strong { display: block; }
.device-metrics span { margin-bottom: 10px; color: var(--muted); font-size: 12px; }
.device-metrics strong { font-size: 19px; font-weight: 500; font-variant-numeric: tabular-nums; }
.device-metrics div:last-child strong { font-size: 13px; line-height: 26px; }
.device-message { padding: 12px 14px; color: #748068; background: #f5f7f0; border-radius: 4px; font-size: 12px; line-height: 1.7; }
.lower-grid { display: grid; grid-template-columns: .95fr 1.05fr; gap: 22px; align-items: start; }
.readiness-list > div { padding: 17px 20px; display: grid; grid-template-columns: 12px 1fr auto; gap: 10px; align-items: center; border-bottom: 1px solid var(--line); font-size: 13px; }
.readiness-list > div:last-child { border: 0; }
.readiness-list i { width: 6px; height: 6px; border-radius: 50%; background: #b87e5f; }.readiness-list i.pass { background: #6f8b59; }
.readiness-list b { color: var(--muted); font-size: 11px; font-weight: 400; }
.asset-card .card-header { align-items: flex-start; }
.asset-card .card-header > strong { color: #61744e; font: 19px/1.4 var(--mono); white-space: nowrap; }
.asset-points > div { padding: 20px; display: grid; grid-template-columns: 68px 1fr; gap: 7px 13px; align-items: center; border-bottom: 1px solid var(--line); }
.asset-points > div:last-child { border: 0; }
.point-position { color: #5c744b; font: 14px/1 var(--mono); }
.asset-points strong, .asset-points small { display: block; }
.asset-points strong { font-size: 13px; font-weight: 500; }
.asset-points small { margin-top: 7px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.point-id { grid-column: 2; color: #9aa38f; font-size: 10px; }
@media (max-width: 1100px) { .lower-grid { grid-template-columns: 1fr; } .device-card { padding: 20px; } .device-card-head { gap: 10px; grid-template-columns: 36px minmax(0,1fr) auto; } .large-symbol { width: 34px; height: 38px; } }
@media (max-width: 760px) { .device-grid { grid-template-columns: 1fr; gap: 18px; } .device-card { padding: 20px; } }
</style>
