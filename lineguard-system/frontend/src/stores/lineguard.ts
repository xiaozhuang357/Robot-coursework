import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api/client'
import type {
  Device,
  FaultType,
  ReviewConclusion,
  Segment,
  TaskDetail,
  TaskEvent,
  TaskSource,
  TaskStatus,
  TaskSummary,
} from '../types'
import { commandId } from '../types'

const dispatchCommandKey = 'lineguard:last-dispatch'
const maxReconnectDelay = 15000

interface RealtimeEnvelope {
  type: string
  payload: Record<string, unknown>
  at?: string
}

export const useLineGuardStore = defineStore('lineguard', () => {
  const segments = ref<Segment[]>([])
  const devices = ref<Device[]>([])
  const tasks = ref<TaskSummary[]>([])
  const activeTask = ref<TaskDetail | null>(null)
  const selectedTask = ref<TaskDetail | null>(null)
  const loading = ref(false)
  const error = ref('')
  const realtimeStatus = ref<'connecting' | 'online' | 'reconnecting'>('connecting')
  const realtimeConnected = computed(() => realtimeStatus.value === 'online')
  const lastDispatchCommands = new Map<string, string>()
  let eventSource: EventSource | null = null
  let refreshTimer: number | null = null
  let reconnectTimer: number | null = null
  let reconnectAttempt = 0
  let hasConnected = false
  let needsResync = false

  const robot = computed(() => devices.value.find(device => device.type === 'ROBOT') ?? null)
  const dock = computed(() => devices.value.find(device => device.type === 'DOCK') ?? null)

  async function initialize() {
    loading.value = true
    try {
      await resyncAll()
    } catch {
      // 原因已进错误栏；SSE 接通后还会再补一次全量数据。
    } finally {
      // 三个首屏请求任何一个失败，SSE 也照样要接上。原先连接写在这三个请求成功之后，
      // 且各视图都用 segments.length 判断"是否已初始化"，于是部分失败时既不会重连
      // 也不会再初始化，界面会永久停在"正在连接"。
      connectRealtime()
      loading.value = false
    }
  }

  async function resyncAll() {
    try {
      await Promise.all([refreshAssets(), refreshLive(), refreshTasks()])
      error.value = ''
    } catch (reason) {
      setError(reason)
      needsResync = true
      throw reason
    }
  }

  async function refreshAssets() {
    segments.value = await api.segments()
  }

  async function refreshLive() {
    const [nextDevices, nextTask] = await Promise.all([api.devices(), api.activeTask()])
    devices.value = nextDevices
    activeTask.value = nextTask
  }

  async function refreshTasks() {
    tasks.value = await api.tasks()
  }

  async function loadTask(id: string) {
    return run(async () => {
      selectedTask.value = await api.task(id)
    })
  }

  async function createAndDispatch(source: TaskSource, pointId: string, inspectionItem: string) {
    return run(async () => {
      const created = await api.createTask({ source, inspectionPointId: pointId, inspectionItem })
      const nextCommandId = commandId('dispatch')
      rememberDispatch(created.task.id, nextCommandId)
      const detail = (await api.dispatch(created.task.id, nextCommandId)).task
      activeTask.value = detail
      await refreshLive()
      await refreshTasks()
      // 预检失败的任务不属于"活动任务"，/tasks/active 会返回空，refreshLive 刚把 activeTask 置空。
      // 如果这里当成成功返回，调用方会弹"派发成功"、创建表单重新出现，失败原因只剩历史页能看到。
      if (detail.task.status === 'PRECHECK_FAILED') {
        throw new Error(`预检未通过：${detail.task.statusMessage}`)
      }
    })
  }

  async function repeatDispatch() {
    return run(async () => {
      const id = activeTask.value?.task.id
      if (!id) throw new Error('当前没有运行中的任务，无法重发派发指令')
      const previous = recallDispatch(id)
      if (!previous) throw new Error('本次会话没有记录该任务的派发指令编号，请重新创建任务')
      await api.dispatch(id, previous)
      await refreshLive()
    })
  }

  async function submitReview(conclusion: ReviewConclusion, remark: string) {
    return run(async () => {
      const id = activeTask.value?.task.id
      if (!id) throw new Error('当前没有待复核的任务')
      activeTask.value = (await api.review(id, {
        commandId: commandId('review'), conclusion, remark, reviewer: '演示运维员',
      })).task
      await refreshLive()
    })
  }

  async function taskAction(action: 'return' | 'abort' | 'recapture') {
    let status: TaskStatus | null = null
    await run(async () => {
      const id = activeTask.value?.task.id
      if (!id) throw new Error('当前没有可操作的巡检任务')
      const detail = (await api.taskAction(id, action, commandId(action))).task
      activeTask.value = detail
      status = detail.task.status
      await refreshLive()
      await refreshTasks()
    })
    return status
  }

  async function injectFault(type: FaultType, enabled = true) {
    let status: string | null = null
    await run(async () => {
      const id = activeTask.value?.task.id
      if (!id) throw new Error('当前没有运行中的任务，无法注入故障')
      const result = await api.injectFault(id, type, enabled)
      status = result.status
      await refreshLive()
    })
    return status
  }

  async function resetDemo() {
    return run(async () => {
      await api.reset()
      lastDispatchCommands.forEach((_, taskId) => forgetDispatch(taskId))
      lastDispatchCommands.clear()
      selectedTask.value = null
      await resyncAll()
    })
  }

  function rememberDispatch(taskId: string, value: string) {
    lastDispatchCommands.set(taskId, value)
    try {
      window.sessionStorage.setItem(`${dispatchCommandKey}:${taskId}`, value)
    } catch {
      // 隐私模式下 sessionStorage 不可写，内存记录仍然可用。
    }
  }

  function recallDispatch(taskId: string): string | null {
    const cached = lastDispatchCommands.get(taskId)
    if (cached) return cached
    try {
      return window.sessionStorage.getItem(`${dispatchCommandKey}:${taskId}`)
    } catch {
      return null
    }
  }

  function forgetDispatch(taskId: string) {
    try {
      window.sessionStorage.removeItem(`${dispatchCommandKey}:${taskId}`)
    } catch {
      // 忽略不可写的存储。
    }
  }

  function connectRealtime() {
    if (eventSource) return
    realtimeStatus.value = reconnectAttempt === 0 ? 'connecting' : 'reconnecting'
    eventSource = new EventSource('/api/v1/events/stream')
    eventSource.addEventListener('connected', () => {
      realtimeStatus.value = 'online'
      // 断线期间的事件不会重放。只恢复连接不补数据，那段时间的状态变化就永远丢了：
      // 例如任务在断线期间转入等待复核，而该状态不再产生新事件，界面会一直停在旧状态。
      // 首屏请求失败过的这次接通同样要补——否则那一轮没拿到的数据没有第二个补救入口。
      if (hasConnected || needsResync) {
        needsResync = false
        void resyncAll().catch(() => {
          // resyncAll 失败时会把 needsResync 置回 true，下次接通再补。
        })
      }
      hasConnected = true
      reconnectAttempt = 0
    })
    eventSource.addEventListener('lineguard-update', handleRealtimeEvent)
    eventSource.onerror = () => {
      eventSource?.close()
      eventSource = null
      realtimeStatus.value = 'reconnecting'
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer !== null) return
    const delay = Math.min(1000 * 2 ** reconnectAttempt, maxReconnectDelay)
    reconnectAttempt += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connectRealtime()
    }, delay)
  }

  function handleRealtimeEvent(message: MessageEvent<string>) {
    realtimeStatus.value = 'online'
    reconnectAttempt = 0
    let envelope: RealtimeEnvelope
    try {
      envelope = JSON.parse(message.data) as RealtimeEnvelope
    } catch {
      scheduleRefresh()
      return
    }
    if (envelope.type === 'DEVICES_UPDATED') {
      applyDeviceSnapshot(envelope.payload)
      return
    }
    if (envelope.type === 'TASK_EVENT') {
      applyTaskEvent(envelope)
      // 遥测事件只更新位置和时间线；其余事件可能带来检测或复核结果，需要回源确认。
      if (envelope.payload.eventType !== 'TELEMETRY') scheduleRefresh()
      return
    }
    scheduleRefresh()
  }

  function applyDeviceSnapshot(payload: Record<string, unknown>) {
    const snapshots = [payload.robot, payload.dock].filter(Boolean) as Partial<Device>[]
    if (!snapshots.length) return
    devices.value = devices.value.map(device => {
      const snapshot = snapshots.find(item => item.id === device.id)
      return snapshot ? { ...device, ...snapshot } : device
    })
  }

  function applyTaskEvent(envelope: RealtimeEnvelope) {
    const detail = activeTask.value
    if (!detail || envelope.payload.taskId !== detail.task.id) return
    const status = envelope.payload.status as TaskStatus
    const message = String(envelope.payload.message ?? '')
    detail.task.status = status
    detail.task.statusMessage = message
    const event: TaskEvent = {
      id: -(detail.events.length + 1),
      fromStatus: null,
      toStatus: status,
      eventType: String(envelope.payload.eventType ?? 'TASK_EVENT'),
      message,
      createdAt: envelope.at ?? new Date().toISOString(),
    }
    detail.events.push(event)
  }

  function scheduleRefresh(forceTasks = false) {
    if (refreshTimer !== null) window.clearTimeout(refreshTimer)
    refreshTimer = window.setTimeout(async () => {
      try {
        await refreshLive()
        if (forceTasks || !activeTask.value || activeTask.value.task.status === 'COMPLETED') {
          await refreshTasks()
        }
      } catch (reason) {
        setError(reason)
      }
    }, 100)
  }

  async function run<T>(action: () => Promise<T>): Promise<T> {
    loading.value = true
    error.value = ''
    try {
      return await action()
    } catch (reason) {
      setError(reason)
      throw reason
    } finally {
      loading.value = false
    }
  }

  function setError(reason: unknown) {
    error.value = reason instanceof Error ? reason.message : '系统操作失败'
  }

  function clearError() {
    error.value = ''
  }

  return {
    segments, devices, tasks, activeTask, selectedTask, loading, error,
    realtimeStatus, realtimeConnected, robot, dock, initialize, refreshAssets, refreshLive,
    refreshTasks, loadTask, createAndDispatch, repeatDispatch, submitReview,
    taskAction, injectFault, resetDemo, clearError,
  }
})
