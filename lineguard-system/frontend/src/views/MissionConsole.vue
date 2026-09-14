<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LineTrack from '../components/LineTrack.vue'
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

const store = useLineGuardStore()
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

onMounted(async () => {
  if (!store.segments.length) await store.initialize()
  selectDefaultPoint()
})

watch(segment, selectDefaultPoint)
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
      await ElMessageBox.confirm('任务终止后，线路上的 Robot 将保持安全驻停。确认继续吗？', '终止任务', {
        confirmButtonText: '确认终止', cancelButtonText: '取消', type: 'warning',
      })
    }
    await store.taskAction(type)
    ElMessage.success(type === 'recapture' ? '已启动重新采集' : type === 'return' ? '已下达返航指令' : '任务已终止')
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
  }
}

async function fault(type: FaultType, enabled = true) {
  try {
    await store.injectFault(type, enabled)
    const message = !enabled ? '故障状态已解除' : {
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
        <span class="eyebrow">CORE MISSION LOOP</span>
        <h2>单档距近距离巡检闭环</h2>
        <p>从驻留待命到安全返回，所有动作围绕一条可追溯任务链推进。</p>
      </div>
      <div class="heading-actions">
        <button class="text-button" @click="resetDemo">重置演示环境</button>
        <div class="live-chip" :class="{ offline: !store.realtimeConnected }">
          <span></span>{{ store.realtimeConnected ? 'SSE 实时同步' : '正在连接' }}
        </div>
      </div>
    </div>

    <section class="mission-flow section-card">
      <div class="flow-title">
        <span>核心任务进度</span>
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
      <div v-if="task && ['PRECHECK_FAILED', 'WAITING_MANUAL', 'SAFE_PARKED', 'ABORTED'].includes(task.task.status)" class="exception-strip">
        <strong>{{ taskStatusLabels[task.task.status] }}</strong>
        <span>{{ task.task.statusMessage }}</span>
      </div>
    </section>

    <div class="console-grid">
      <div class="console-main">
        <LineTrack
          v-if="segment"
          :length-meters="segment.lengthMeters"
          :points="segment.inspectionPoints"
          :robot-position="robot?.positionMeters ?? 0"
          :target-point-id="task?.inspectionPoint.id"
          :robot-online="robot?.online ?? false"
        />

        <section v-if="!task" class="section-card create-mission-card">
          <div class="card-header">
            <div><h3>创建首个巡检任务</h3><p>系统将自动执行预检并启动 Robot。</p></div>
            <span class="card-code">NEW MISSION</span>
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
                <span class="eyebrow">HUMAN REVIEW</span>
                <h4>记录人工复核结论</h4>
                <el-radio-group v-model="reviewForm.conclusion" class="review-options">
                  <el-radio v-for="(label, value) in reviewLabels" :key="value" :value="value" border>{{ label }}</el-radio>
                </el-radio-group>
                <el-input v-model="reviewForm.remark" type="textarea" :rows="3" placeholder="补充观察说明（可选）" maxlength="500" />
                <button class="primary-action full" @click="submitReview">提交结论并下达返航</button>
              </template>
              <template v-else>
                <span class="eyebrow danger-text">DATA QUALITY ALERT</span>
                <h4>当前图像无法支持人工判断</h4>
                <p>系统没有将缺失结果记录为正常。可以在原位置重新采集一次。</p>
                <button class="primary-action full" @click="action('recapture')">重新采集图像</button>
              </template>
            </div>
          </div>

          <div class="mission-actions">
            <button v-if="canManualReturn" class="secondary-action" @click="action('return')">人工返航</button>
            <button v-if="canAbort" class="danger-action" @click="action('abort')">终止任务</button>
          </div>
        </section>
      </div>

      <aside class="console-side">
        <section class="section-card device-snapshot">
          <div class="card-header"><div><h3>设备快照</h3><p>预检与运行状态</p></div><span class="card-code">LIVE</span></div>
          <div class="device-row">
            <div class="device-symbol robot-symbol">R</div>
            <div class="device-copy">
              <strong>Robot 01</strong>
              <span>{{ robot ? (stateLabels[robot.state] ?? robot.state) : '读取中' }}</span>
              <small>{{ robot?.statusMessage }}</small>
            </div>
            <div class="device-health"><i :class="{ online: robot?.online }"></i>{{ robot?.online ? '在线' : '离线' }}</div>
          </div>
          <div class="device-row">
            <div class="device-symbol dock-symbol">D</div>
            <div class="device-copy">
              <strong>Dock 01</strong>
              <span>{{ dock ? (stateLabels[dock.state] ?? dock.state) : '读取中' }}</span>
              <small>{{ dock?.statusMessage }}</small>
            </div>
            <div class="device-health"><i :class="{ online: dock?.online }"></i>{{ dock?.online ? '在线' : '离线' }}</div>
          </div>
          <div class="battery-panel">
            <div><span>Robot 电量</span><strong>{{ robot?.batteryPercent ?? 0 }}%</strong></div>
            <el-progress :percentage="robot?.batteryPercent ?? 0" :stroke-width="6" :show-text="false" color="#00a6b7" />
          </div>
        </section>

        <section class="section-card fault-panel">
          <div class="card-header"><div><h3>异常场景注入</h3><p>验证本地安全策略</p></div><span class="card-code warning">SIM</span></div>
          <div class="fault-buttons">
            <button @click="fault('LOW_BATTERY')"><b>01</b><span>低电量返航</span></button>
            <button @click="fault('COMMUNICATION_LOSS')"><b>02</b><span>通信中断</span></button>
            <button @click="fault('OBSTACLE')"><b>03</b><span>未知障碍</span></button>
            <button @click="fault('DOCKING_FAILURE')"><b>04</b><span>对接失败</span></button>
            <button @click="fault('BLURRED_IMAGE')"><b>05</b><span>模糊图像</span></button>
            <button @click="repeatCommand"><b>06</b><span>重复指令</span></button>
          </div>
          <div class="fault-recovery">
            <button @click="fault('COMMUNICATION_LOSS', false)">恢复通信</button>
            <button @click="fault('OBSTACLE', false)">解除障碍</button>
            <button @click="fault('DOCKING_FAILURE', false)">恢复 Dock</button>
          </div>
        </section>

        <section class="section-card event-panel">
          <div class="card-header"><div><h3>最近任务事件</h3><p>状态与动作全程留痕</p></div></div>
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
.heading-actions { display: flex; align-items: center; gap: 17px; }
.text-button { border: 0; color: #71838d; background: transparent; cursor: pointer; font-size: 11px; }
.text-button:hover { color: #087f8c; }
.live-chip { display: flex; align-items: center; gap: 8px; padding: 8px 11px; color: #277b4a; background: #effaf4; border: 1px solid #b7dfc8; font: 10px/1 "JetBrains Mono", monospace; }
.live-chip span { width: 6px; height: 6px; border-radius: 50%; background: #35ad66; box-shadow: 0 0 0 4px rgba(53,173,102,.11); }
.live-chip.offline { color: #7c8d94; background: #f2f5f6; border-color: #d6dfe2; }
.live-chip.offline span { background: #87979e; box-shadow: none; }
.mission-flow { margin-bottom: 18px; }
.flow-title { min-height: 55px; padding: 12px 18px; display: flex; align-items: center; gap: 14px; border-bottom: 1px solid #e3eaed; }
.flow-title > span:first-child { color: #435b65; font-size: 12px; font-weight: 650; }
.standby-label { margin-left: auto; color: #93a0a5; font-size: 10px; }
.flow-title .status-badge { margin-left: auto; }
.flow-steps { display: grid; grid-template-columns: repeat(9, 1fr); padding: 18px 17px 20px; }
.flow-step { position: relative; color: #9aa8ad; text-align: center; }
.flow-step::before { content: ''; position: absolute; left: -50%; right: 50%; top: 10px; height: 1px; background: #d9e3e6; }
.flow-step:first-child::before { display: none; }
.step-index { position: relative; z-index: 1; display: grid; place-items: center; width: 22px; height: 22px; margin: 0 auto 8px; background: #fff; border: 1px solid #d3dfe2; border-radius: 50%; font: 8px/1 "JetBrains Mono", monospace; }
.flow-step strong { font-size: 10px; font-weight: 500; }
.flow-step.done, .flow-step.active { color: #087f8c; }
.flow-step.done::before, .flow-step.active::before { background: #48c4ce; }
.flow-step.done .step-index { color: #fff; background: #0aa3b1; border-color: #0aa3b1; }
.flow-step.active .step-index { color: #087f8c; border-color: #13a7b5; box-shadow: 0 0 0 5px #e2f8f9; }
.exception-strip { padding: 10px 18px; display: flex; gap: 13px; color: #a8413e; background: #fff3f2; border-top: 1px solid #f2d2d0; font-size: 11px; }
.console-grid { display: grid; grid-template-columns: minmax(650px, 1fr) 345px; gap: 18px; align-items: start; }
.console-main, .console-side { display: grid; gap: 18px; }
.card-code { color: #8ca0a8; font: 9px/1 "JetBrains Mono", monospace; letter-spacing: 1.4px; }
.card-code.warning { color: #a66f15; }
.create-form { display: grid; grid-template-columns: 1fr 1.3fr; gap: 17px; }
.create-form label { display: grid; gap: 8px; }
.create-form label > span { color: #657a83; font-size: 11px; }
.create-form .wide-field { grid-column: 1 / -1; }
.primary-action, .secondary-action, .danger-action { min-height: 40px; padding: 0 17px; border: 0; cursor: pointer; transition: .2s; font-size: 12px; }
.primary-action { grid-column: 1 / -1; color: #fff; background: linear-gradient(95deg, #087f8c, #05a8b7); box-shadow: 0 7px 17px rgba(5,132,145,.15); }
.primary-action:hover { filter: brightness(1.06); }
.primary-action:disabled { opacity: .45; cursor: not-allowed; }
.primary-action span { float: right; font-size: 17px; }
.primary-action.full { width: 100%; margin-top: 4px; }
.secondary-action { color: #087f8c; background: #edfafa; border: 1px solid #b5e1e4; }
.danger-action { color: #ac413e; background: #fff3f2; border: 1px solid #efc7c5; }
.task-time { color: #8da0a7; font: 9px/1 "JetBrains Mono", monospace; }
.task-facts { display: grid; grid-template-columns: repeat(4, 1fr); border-bottom: 1px solid #e2eaed; }
.task-facts div { padding: 15px 18px; border-right: 1px solid #e2eaed; }
.task-facts div:last-child { border-right: 0; }
.task-facts span, .task-facts strong { display: block; }
.task-facts span { margin-bottom: 6px; color: #82939a; font-size: 9px; }
.task-facts strong { color: #233b45; font-size: 12px; }
.mission-actions { padding: 14px 18px; display: flex; justify-content: flex-end; gap: 10px; }
.review-workbench { display: grid; grid-template-columns: 1.15fr .85fr; border-bottom: 1px solid #e2eaed; }
.detection-preview { position: relative; min-height: 285px; overflow: hidden; background: #102a36; }
.detection-preview img { display: block; width: 100%; height: 285px; object-fit: cover; }
.image-caption { position: absolute; left: 0; right: 0; bottom: 0; padding: 11px 14px; display: flex; justify-content: space-between; color: #d8e9ed; background: rgba(5,20,28,.8); font-size: 10px; }
.image-caption b { color: #77edb0; }.image-caption b.bad { color: #ffb0aa; }
.review-form { padding: 22px; }
.review-form h4 { margin: 7px 0 14px; font-size: 15px; }
.review-form p { color: #71838d; font-size: 11px; line-height: 1.8; }
.review-options { width: 100%; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 13px; }
.review-options :deep(.el-radio) { margin: 0; }
.danger-text { color: #c34945; }
.device-row { padding: 15px 17px; display: grid; grid-template-columns: 38px 1fr auto; gap: 12px; align-items: start; border-bottom: 1px solid #e5ecee; }
.device-symbol { display: grid; place-items: center; width: 36px; height: 36px; color: #fff; font: 12px/1 "JetBrains Mono", monospace; }
.robot-symbol { background: #098e9b; }.dock-symbol { background: #1f4656; }
.device-copy strong, .device-copy span, .device-copy small { display: block; }
.device-copy strong { font-size: 12px; }.device-copy span { margin-top: 3px; color: #087f8c; font-size: 10px; }.device-copy small { max-width: 170px; margin-top: 5px; color: #8a9a9f; font-size: 9px; line-height: 1.45; }
.device-health { display: flex; align-items: center; gap: 5px; color: #8a9a9f; font-size: 9px; }
.device-health i { width: 6px; height: 6px; border-radius: 50%; background: #9eaaae; }.device-health i.online { background: #41bd72; }
.battery-panel { padding: 14px 17px 17px; }
.battery-panel > div { margin-bottom: 8px; display: flex; justify-content: space-between; color: #71838d; font-size: 10px; }.battery-panel strong { color: #233b45; }
.fault-buttons { display: grid; grid-template-columns: 1fr 1fr; }
.fault-buttons button { min-height: 53px; padding: 10px 12px; display: flex; align-items: center; gap: 9px; color: #536b74; background: #fff; border: 0; border-right: 1px solid #e5ecee; border-bottom: 1px solid #e5ecee; cursor: pointer; font-size: 10px; text-align: left; }
.fault-buttons button:nth-child(even) { border-right: 0; }.fault-buttons button:hover { color: #9d6812; background: #fff9ec; }
.fault-buttons b { color: #b7c4c9; font: 8px/1 "JetBrains Mono", monospace; }
.fault-recovery { padding: 10px 12px; display: flex; gap: 7px; flex-wrap: wrap; background: #f8fafb; }
.fault-recovery button { padding: 6px 8px; color: #637780; background: #fff; border: 1px solid #d8e2e5; cursor: pointer; font-size: 9px; }
.event-panel .card-body { max-height: 330px; overflow: auto; }
@media (max-width: 1400px) { .console-grid { grid-template-columns: minmax(620px, 1fr) 315px; } }
</style>

