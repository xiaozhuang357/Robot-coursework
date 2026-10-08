<script setup lang="ts">
import { computed, defineAsyncComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RouterLink } from 'vue-router'
import LineTrack from '../components/LineTrack.vue'
import DeviceGlyph from '../components/DeviceGlyph.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TaskTimeline from '../components/TaskTimeline.vue'
import { useLineGuardStore } from '../stores/lineguard'
import {
  coreTaskSteps,
  reviewLabels,
  sourceLabels,
  stateLabels,
  taskStatusLabels,
  type FaultType,
  type ReviewConclusion,
  type TaskSource,
} from '../types'

// 三维场景按需加载，three.js 不打进首屏包
const SpanScene3D = defineAsyncComponent({
  loader: () => import('../components/SpanScene3D.vue'),
  loadingComponent: { render: () => h('div', { class: 'scene-placeholder' }, '正在加载三维场景…') },
  errorComponent: { render: () => h('div', { class: 'scene-placeholder' }, '三维场景加载失败，请切换工程视图。') },
  delay: 120,
})

const store = useLineGuardStore()
const viewMode = ref<'3d' | '2d'>('3d')
const createForm = reactive({
  source: 'MANUAL' as TaskSource,
  pointId: '',
  inspectionItem: '',
})
const reviewForm = reactive({
  conclusion: 'NO_ABNORMALITY' as ReviewConclusion,
  remark: '',
})

const segment = computed(() => store.segments[0] ?? null)
const task = computed(() => store.activeTask)
const robot = computed(() => store.robot)
const dock = computed(() => store.dock)
const canCreate = computed(() => !task.value && !!createForm.pointId)
const canManualReturn = computed(() => task.value && [
  'MOVING', 'INSPECTING', 'UPLOADING', 'AWAITING_REVIEW', 'WAITING_MANUAL', 'SAFE_PARKED',
].includes(task.value.task.status))
const canAbort = computed(() => task.value && !['COMPLETED', 'ABORTED'].includes(task.value.task.status))
const recentEvents = computed(() => task.value?.events.slice(-7).reverse() ?? [])
const recoveryRequired = computed(() => !!task.value && [
  'WAITING_MANUAL', 'SAFE_PARKED',
].includes(task.value.task.status))

onMounted(async () => {
  if (!store.segments.length) await store.initialize()
  selectDefaultPoint()
})

watch(segment, selectDefaultPoint)
watch(() => task.value?.task.id, () => {
  reviewForm.conclusion = 'NO_ABNORMALITY'
  reviewForm.remark = ''
})
watch(() => createForm.pointId, (id) => {
  const point = segment.value?.inspectionPoints.find(item => item.id === id)
  if (point) createForm.inspectionItem = point.defaultInspectionItem
})

function selectDefaultPoint() {
  if (!createForm.pointId && segment.value?.inspectionPoints.length) {
    createForm.pointId = segment.value.inspectionPoints[0].id
    createForm.inspectionItem = segment.value.inspectionPoints[0].defaultInspectionItem
  }
}

function stepClass(step: string, index: number) {
  const status = task.value?.task.status
  if (!status) return ''
  if (status === 'COMPLETED') return 'done'
  if (['PRECHECK_FAILED', 'WAITING_MANUAL', 'SAFE_PARKED', 'ABORTED'].includes(status)) {
    const lastNormal = [...(task.value?.events ?? [])].reverse()
      .find(event => coreTaskSteps.includes(event.toStatus))?.toStatus
    const lastIndex = lastNormal ? coreTaskSteps.indexOf(lastNormal) : -1
    return index <= lastIndex ? 'done' : ''
  }
  const activeIndex = coreTaskSteps.indexOf(status)
  if (index < activeIndex) return 'done'
  if (step === status) return 'active'
  return ''
}

async function createMission() {
  try {
    await store.createAndDispatch(createForm.source, createForm.pointId, createForm.inspectionItem)
    ElMessage.success('任务已创建并完成派发')
  } catch {
    // 全局错误栏已展示具体原因。
  }
}

async function submitReview() {
  try {
    await store.submitReview(reviewForm.conclusion, reviewForm.remark)
    ElMessage.success('复核结论已保存，Robot 开始返航')
  } catch {
    // 全局错误栏已展示具体原因。
  }
}

async function action(type: 'return' | 'abort' | 'recapture') {
  try {
    if (type === 'abort') {
      await ElMessageBox.confirm('任务终止后，线路上的 Robot 将保持安全驻停，需要召回才能返回 Dock。确认继续吗？', '终止任务', {
        confirmButtonText: '确认终止', cancelButtonText: '取消', type: 'warning',
      })
    }
    const status = await store.taskAction(type)
    if (type === 'recapture') {
      ElMessage.success('已启动重新采集')
    } else if (type === 'return') {
      ElMessage.success(recoveryRequired.value ? '已下达召回返航指令' : '已下达返航指令')
    } else {
      ElMessage.success(status === 'SAFE_PARKED'
        ? '任务已终止，Robot 挂线驻停，可点击「召回返航」'
        : '任务已终止')
    }
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
  }
}

async function fault(type: FaultType, enabled = true) {
  try {
    const status = await store.injectFault(type, enabled)
    if (!enabled) {
      ElMessage.success('故障状态已解除')
      return
    }
    // 低电量自动返航要过和人工返航同一套联锁（离线、未解除的障碍、等待人工处理），
    // 被拦下时状态不会是 RETURNING。这时不能还报"将执行安全策略"，否则用户以为机器人在返航。
    if (type === 'LOW_BATTERY' && status !== 'RETURNING') {
      ElMessage.warning('已注入低电量，但当前存在未解除的联锁，暂不自动返航')
      return
    }
    const message = {
      LOW_BATTERY: '已注入低电量，系统将执行安全策略',
      COMMUNICATION_LOSS: '已注入通信中断',
      OBSTACLE: '已注入未知障碍',
      DOCKING_FAILURE: '已设置返航对接失败',
      BLURRED_IMAGE: '下一次检测将生成不可判读图像',
    }[type]
    ElMessage.warning(message)
  } catch {
    // 全局错误栏已展示具体原因。
  }
}

async function repeatCommand() {
  try {
    await store.repeatDispatch()
    ElMessage.info('重复派发指令已被幂等处理，Robot 未重复动作')
  } catch {
    // 全局错误栏已展示具体原因。
  }
}

async function resetDemo() {
  try {
    await ElMessageBox.confirm('这会清空当前仿真任务和历史记录，并让 Robot 返回初始驻留状态。', '重置演示环境', {
      confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning',
    })
    await store.resetDemo()
    selectDefaultPoint()
    ElMessage.success('演示环境已重置')
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
  }
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <div class="page-content mission-console" v-loading="store.loading">
    <div class="page-heading">
      <div>
        <h2>巡检工作台</h2>
        <p>{{ task ? task.task.statusMessage : '设备待命。选择检测点，开始一次近距离巡检。' }}</p>
      </div>
      <div class="heading-actions">
        <div class="view-switch" role="group" aria-label="线路视图">
          <button :class="{ active: viewMode === '3d' }" :aria-pressed="viewMode === '3d'" @click="viewMode = '3d'">三维场景</button>
          <button :class="{ active: viewMode === '2d' }" :aria-pressed="viewMode === '2d'" @click="viewMode = '2d'">工程视图</button>
        </div>
        <button class="text-button" @click="resetDemo">重置演示环境</button>
      </div>
    </div>

    <section class="mission-flow section-card">
      <div class="flow-title">
        <span>任务进度</span>
        <StatusBadge v-if="task" :status="task.task.status" />
        <span v-else class="standby-label">等待创建任务</span>
      </div>
      <div class="flow-steps">
        <div
          v-for="(step, index) in coreTaskSteps"
          :key="step"
          class="flow-step"
          :class="stepClass(step, index)"
        >
          <span class="step-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <strong>{{ taskStatusLabels[step] }}</strong>
        </div>
      </div>
      <div v-if="task && ['WAITING_MANUAL', 'SAFE_PARKED', 'ABORTED'].includes(task.task.status)" class="exception-strip">
        <strong>{{ taskStatusLabels[task.task.status] }}</strong>
        <span>{{ task.task.statusMessage }}</span>
      </div>
    </section>

    <div class="console-grid">
      <div class="console-main" :class="{ 'review-first': task?.task.status === 'AWAITING_REVIEW' }">
        <SpanScene3D
          v-if="viewMode === '3d' && segment"
          :length-meters="segment.lengthMeters"
          :points="segment.inspectionPoints"
          :robot-position="robot?.positionMeters ?? 0"
          :target-point-id="task?.inspectionPoint.id"
          :robot-online="robot?.online ?? false"
        />
        <LineTrack
          v-else-if="segment"
          :length-meters="segment.lengthMeters"
          :points="segment.inspectionPoints"
          :robot-position="robot?.positionMeters ?? 0"
          :target-point-id="task?.inspectionPoint.id"
          :robot-online="robot?.online ?? false"
        />

        <section v-if="!task" class="section-card create-mission-card">
          <div class="card-header">
            <div><h3>新建巡检任务</h3><p>提交后检查设备状态，预检通过即自动出发。</p></div>
            <span class="card-code">{{ segment?.inspectionPoints.length ?? 0 }} 个检测点</span>
          </div>
          <div class="create-form card-body">
            <label>
              <span>任务来源</span>
              <el-select v-model="createForm.source">
                <el-option v-for="(label, value) in sourceLabels" :key="value" :label="label" :value="value" />
              </el-select>
            </label>
            <label>
              <span>目标检测点</span>
              <el-select v-model="createForm.pointId">
                <el-option
                  v-for="point in segment?.inspectionPoints ?? []"
                  :key="point.id"
                  :label="`${point.name} · ${point.positionMeters} m`"
                  :value="point.id"
                />
              </el-select>
            </label>
            <label class="wide-field">
              <span>检测内容</span>
              <el-input v-model="createForm.inspectionItem" maxlength="120" />
            </label>
            <button class="primary-action" :disabled="!canCreate" @click="createMission">
              创建并开始任务 <span>→</span>
            </button>
          </div>
        </section>

        <section v-else class="section-card mission-detail-card">
          <div class="card-header">
            <div>
              <h3>{{ task.task.taskCode }}</h3>
              <p>{{ task.inspectionPoint.name }} · {{ task.task.inspectionItem }}</p>
            </div>
            <span class="task-time">更新于 {{ formatTime(task.task.updatedAt) }}</span>
          </div>
          <div class="task-facts">
            <div><span>任务来源</span><strong>{{ sourceLabels[task.task.source] }}</strong></div>
            <div><span>目标位置</span><strong>{{ task.inspectionPoint.positionMeters }} m</strong></div>
            <div><span>Robot 位置</span><strong>{{ Math.round(robot?.positionMeters ?? 0) }} m</strong></div>
            <div><span>剩余电量</span><strong>{{ robot?.batteryPercent ?? '--' }}%</strong></div>
          </div>

          <div v-if="task.task.status === 'AWAITING_REVIEW'" class="review-workbench">
            <div class="detection-preview">
              <img v-if="task.latestDetection" :src="task.latestDetection.imageUrl" alt="Robot 上传的近距离检测图像" />
              <div v-else class="empty-panel">等待检测图像</div>
              <div v-if="task.latestDetection" class="image-caption">
                <span>第 {{ task.latestDetection.captureIndex }} 次采集</span>
                <b :class="{ bad: task.latestDetection.quality === 'UNREADABLE' }">
                  {{ task.latestDetection.quality === 'READABLE' ? '图像可判读' : '图像不可判读' }}
                </b>
              </div>
            </div>
            <div class="review-form">
              <template v-if="task.latestDetection?.quality === 'READABLE'">
                <span class="eyebrow">检测图像已上传 · 需要人工确认</span>
                <h4>记录人工复核结论</h4>
                <el-radio-group v-model="reviewForm.conclusion" class="review-options">
                  <el-radio v-for="(label, value) in reviewLabels" :key="value" :value="value" border>{{ label }}</el-radio>
                </el-radio-group>
                <el-input v-model="reviewForm.remark" type="textarea" :rows="3" placeholder="补充观察说明（可选）" maxlength="500" />
                <button class="primary-action full" @click="submitReview">提交结论并下达返航</button>
              </template>
              <template v-else>
                <span class="eyebrow danger-text">图像质量异常</span>
                <h4>当前图像无法支持人工判断</h4>
                <p>系统没有将缺失结果记录为正常。可以在原位置重新采集一次。</p>
                <button class="primary-action full" @click="action('recapture')">重新采集图像</button>
              </template>
            </div>
          </div>

          <div v-if="recoveryRequired" class="recovery-strip">
            <span>Robot 当前挂线驻停，任务需要召回返航后才能结束。</span>
          </div>

          <div class="mission-actions">
            <button v-if="canManualReturn" class="secondary-action" @click="action('return')">
              {{ recoveryRequired ? '召回返航' : '人工返航' }}
            </button>
            <button v-if="canAbort" class="danger-action" @click="action('abort')">终止任务</button>
          </div>
        </section>
      </div>

      <aside class="console-side">
        <section class="section-card device-snapshot">
          <div class="card-header"><div><h3>设备状态</h3></div><RouterLink class="card-link" to="/devices">详情 ↗</RouterLink></div>
          <div class="device-row">
            <div class="device-symbol robot-symbol"><DeviceGlyph type="ROBOT" :size="22" /></div>
            <div class="device-copy">
              <strong>Robot 01</strong>
              <span>{{ robot ? (stateLabels[robot.state] ?? robot.state) : '读取中' }}</span>
              <small>{{ robot?.statusMessage }}</small>
            </div>
            <div class="device-health"><i :class="{ online: robot?.online }"></i>{{ robot?.online ? '在线' : '离线' }}</div>
          </div>
          <div class="device-row">
            <div class="device-symbol dock-symbol"><DeviceGlyph type="DOCK" :size="22" /></div>
            <div class="device-copy">
              <strong>Dock 01</strong>
              <span>{{ dock ? (stateLabels[dock.state] ?? dock.state) : '读取中' }}</span>
              <small>{{ dock?.statusMessage }}</small>
            </div>
            <div class="device-health"><i :class="{ online: dock?.online }"></i>{{ dock?.online ? '在线' : '离线' }}</div>
          </div>
          <div class="battery-panel">
            <div><span>Robot 电量</span><strong>{{ robot?.batteryPercent ?? 0 }}%</strong></div>
            <el-progress :percentage="robot?.batteryPercent ?? 0" :stroke-width="6" :show-text="false" color="#66836c" />
          </div>
        </section>

        <details class="section-card fault-panel">
          <summary><div><strong>故障演示</strong><span>6 项场景 · 点击展开</span></div><span class="disclosure-arrow">⌄</span></summary>
          <p class="fault-note">任务开始后，可注入异常并观察处置。</p>
          <div class="fault-buttons">
            <button :disabled="!task" @click="fault('LOW_BATTERY')"><b>01</b><span>低电量返航</span></button>
            <button :disabled="!task" @click="fault('COMMUNICATION_LOSS')"><b>02</b><span>通信中断</span></button>
            <button :disabled="!task" @click="fault('OBSTACLE')"><b>03</b><span>未知障碍</span></button>
            <button :disabled="!task" @click="fault('DOCKING_FAILURE')"><b>04</b><span>对接失败</span></button>
            <button :disabled="!task" @click="fault('BLURRED_IMAGE')"><b>05</b><span>模糊图像</span></button>
            <button :disabled="!task" @click="repeatCommand"><b>06</b><span>重复指令</span></button>
          </div>
          <div class="fault-recovery">
            <button :disabled="!task" @click="fault('COMMUNICATION_LOSS', false)">恢复通信</button>
            <button :disabled="!task" @click="fault('OBSTACLE', false)">解除障碍</button>
            <button :disabled="!task" @click="fault('DOCKING_FAILURE', false)">恢复 Dock</button>
          </div>
        </details>

        <section class="section-card event-panel">
          <div class="card-header"><div><h3>任务日志</h3></div><span class="card-code">最近 {{ recentEvents.length }} 条</span></div>
          <div class="card-body">
            <TaskTimeline v-if="recentEvents.length" :events="recentEvents" compact />
            <div v-else class="empty-panel">任务启动后将在这里显示状态事件</div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>

.heading-actions { display: flex; align-items: center; gap: 17px; flex: 0 0 auto; }
.view-switch { display: flex; padding: 3px; background: #e9ede5; border-radius: 6px; }
.view-switch button { padding: 7px 12px; color: var(--muted); background: none; border: 0; border-radius: 4px; cursor: pointer; font-size: 12px; }
.view-switch button.active { color: var(--ink); background: #fff; box-shadow: 0 1px 3px #283b2820; }
.scene-placeholder { padding: 70px 20px; color: var(--muted); background: #fff; border: 1px solid var(--line); border-radius: 7px; text-align: center; font-size: 13px; }
.text-button { padding: 8px 0; color: var(--muted); background: none; border: 0; cursor: pointer; font-size: 12px; }
.text-button:hover { color: var(--brand-ink); }
.mission-flow { margin-bottom: 22px; }
.flow-title { min-height: 48px; padding: 10px 20px; display: flex; align-items: center; gap: 14px; border-bottom: 1px solid var(--line); }
.flow-title > span:first-child { font-size: 13px; font-weight: 600; }
.standby-label { margin-left: auto; color: var(--muted); font-size: 12px; }
.flow-title .status-badge { margin-left: auto; }
.flow-steps { display: grid; grid-template-columns: repeat(9, minmax(72px, 1fr)); padding: 18px 20px; overflow: auto; }
.flow-step { position: relative; display: flex; flex-direction: column; gap: 8px; color: #949c91; }
.flow-step::after { content: ''; position: absolute; top: 10px; left: 30px; right: 10px; height: 1px; background: #e1e6dc; }
.flow-step:last-child::after { display: none; }
.step-index { position: relative; z-index: 1; display: grid; place-items: center; width: 21px; height: 21px; background: #f2f4ef; border-radius: 3px; font: 10px/1 var(--mono); }
.flow-step strong { font-size: 11px; font-weight: 400; white-space: nowrap; }
.flow-step.done { color: #64785d; }.flow-step.active { color: var(--brand-ink); }
.flow-step.done .step-index { background: #e2eadd; color: #48623e; }
.flow-step.active .step-index { color: #fff; background: var(--brand-ink); }
.flow-step.active strong { font-weight: 600; }
.exception-strip { padding: 12px 20px; display: flex; gap: 13px; color: #a44f43; background: #fff4ed; border-top: 1px solid #eadbd0; font-size: 12px; }
.console-grid { display: grid; grid-template-columns: minmax(0, 1fr) 292px; gap: 22px; align-items: start; }
.console-main, .console-side { min-width: 0; display: grid; gap: 20px; }
.console-main.review-first .mission-detail-card { order: -1; }
.card-code { color: var(--muted); font-size: 11px; white-space: nowrap; }
.card-link { color: var(--muted); font-size: 12px; text-decoration: none; }.card-link:hover { color: var(--brand-ink); }
.create-form { display: grid; grid-template-columns: 1fr 1.3fr; gap: 18px; }
.create-form label { min-width: 0; display: grid; gap: 9px; }
.create-form label > span { color: #576453; font-size: 12px; }
.create-form .wide-field { grid-column: 1 / -1; }
.primary-action, .secondary-action, .danger-action { min-height: 40px; padding: 10px 16px; border: 1px solid transparent; border-radius: 5px; cursor: pointer; font-size: 13px; }
.primary-action { grid-column: 1 / -1; color: #fff; background: var(--brand-ink); }
.primary-action:hover { background: #234431; }
.primary-action span { float: right; margin-left: 20px; font-size: 17px; }
.create-form .primary-action { justify-self: start; min-width: 200px; }
.primary-action.full { width: 100%; margin-top: 12px; }
.secondary-action { color: var(--ink); background: #fff; border-color: #d8dfd1; }.secondary-action:hover { background: #f4f6f0; }
.danger-action { color: #ae564b; background: #fff; border-color: #e8d9d2; }.danger-action:hover { background: #fff1ec; }
.task-time { color: var(--muted); font-size: 11px; white-space: nowrap; }
.task-facts { display: grid; grid-template-columns: repeat(4, 1fr); padding: 18px 0; border-bottom: 1px solid var(--line); }
.task-facts div { padding: 0 20px; border-right: 1px solid var(--line); }
.task-facts div:last-child { border-right: 0; }
.task-facts span, .task-facts strong { display: block; }
.task-facts span { margin-bottom: 8px; color: var(--muted); font-size: 11px; }
.task-facts strong { font-size: 13px; font-weight: 500; }
.mission-actions { padding: 16px 20px; display: flex; justify-content: flex-end; gap: 10px; }
.recovery-strip { padding: 14px 20px; color: #92642d; background: #faf3e6; font-size: 13px; line-height: 1.7; }
.review-workbench { display: grid; grid-template-columns: 1fr 1fr; padding: 20px; gap: 20px; border-bottom: 1px solid var(--line); }
.detection-preview { position: relative; align-self: start; overflow: hidden; background: #e5eae2; border-radius: 4px; }
.detection-preview img { display: block; width: 100%; aspect-ratio: 16/10; object-fit: contain; }
.image-caption { padding: 10px; display: flex; justify-content: space-between; gap: 8px; color: #71806f; background: #f2f5ed; font-size: 11px; }
.image-caption b { color: #4b6e46; font-weight: 500; }.image-caption b.bad { color: #b65a4b; }
.review-form { min-width: 0; }
.review-form h4 { margin: 7px 0 17px; font-size: 15px; font-weight: 600; }
.review-form p { color: var(--muted); font-size: 12px; line-height: 1.8; }
.review-options { width: 100%; display: grid; grid-template-columns: 1fr; gap: 7px; margin-bottom: 13px; }
.review-options :deep(.el-radio) { margin: 0; height: 32px; padding: 0 10px; }
.review-options :deep(.el-radio__label) { font-size: 12px; }
.danger-text { color: var(--danger); }
.device-row { padding: 18px; display: grid; grid-template-columns: 30px 1fr auto; gap: 10px; align-items: start; border-bottom: 1px solid var(--line); }
.device-symbol { display: grid; place-items: center; width: 30px; height: 30px; color: #5c755b; }
.device-copy strong, .device-copy span, .device-copy small { display: block; }
.device-copy strong { font-size: 13px; font-weight: 600; }.device-copy span { margin-top: 4px; color: #5d7557; font-size: 12px; }.device-copy small { margin-top: 6px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.device-health { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 11px; white-space: nowrap; }
.device-health i { width: 5px; height: 5px; border-radius: 50%; background: #b4aa97; }.device-health i.online { background: #6c8a62; }
.battery-panel { padding: 16px 18px 20px; }
.battery-panel > div { margin-bottom: 9px; display: flex; justify-content: space-between; color: var(--muted); font-size: 12px; }.battery-panel strong { color: var(--ink); font-family: var(--mono); font-weight: 500; }
.fault-panel { overflow: hidden; }
.fault-panel summary { display: flex; align-items: center; justify-content: space-between; padding: 17px 18px; cursor: pointer; list-style: none; }
.fault-panel summary::-webkit-details-marker { display: none; }
.fault-panel summary strong { display: block; font-size: 13px; font-weight: 600; }.fault-panel summary div > span { display: block; margin-top: 5px; color: var(--muted); font-size: 11px; }
.disclosure-arrow { font-size: 18px; color: #909987; }.fault-panel[open] .disclosure-arrow { transform: rotate(180deg); }
.fault-note { margin: 0; padding: 0 18px 14px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.fault-buttons { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--line); }
.fault-buttons button { min-height: 46px; padding: 10px; display: flex; align-items: center; gap: 7px; color: #626e5c; background: #fff; border: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); cursor: pointer; font-size: 12px; text-align: left; }
.fault-buttons button:nth-child(even) { border-right: 0; }.fault-buttons button:hover:not(:disabled) { color: #92642d; background: #fcf5e8; }
.fault-buttons b { color: #a9b1a1; font: 10px/1 var(--mono); }
.fault-recovery { padding: 12px 10px; display: flex; gap: 6px; flex-wrap: wrap; background: #f7f8f4; }
.fault-recovery button { padding: 6px 7px; color: #697461; background: #fff; border: 1px solid var(--line); border-radius: 4px; cursor: pointer; font-size: 11px; }
.event-panel .card-body { max-height: 310px; overflow: auto; }
@media (min-width: 1550px) { .review-options { grid-template-columns: 1fr 1fr; } }
@media (max-width: 1150px) {
  .console-grid { grid-template-columns: minmax(0, 1fr); }
  .console-side { grid-template-columns: 1fr 1fr; align-items: start; }
  .device-snapshot { grid-row: span 2; }
  .review-options { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 760px) {
  .heading-actions { width: 100%; justify-content: space-between; gap: 8px; }
  .mission-flow { margin-bottom: 18px; }
  .flow-title { padding: 10px 16px; }
  .flow-steps { padding: 16px; }
  .console-side { grid-template-columns: 1fr; }
  .device-snapshot { grid-row: auto; }
  .create-form { grid-template-columns: 1fr; }
  .create-form .primary-action { width: 100%; }
  .task-facts { grid-template-columns: 1fr 1fr; gap: 20px 0; }
  .task-facts div:nth-child(even) { border: 0; }
  .review-workbench { grid-template-columns: 1fr; padding: 16px; }
  .review-options { grid-template-columns: 1fr 1fr; }
  .task-time { display: none; }
  .exception-strip { flex-direction: column; gap: 6px; }
}
</style>
