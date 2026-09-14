import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api/client'
import type { Device, FaultType, ReviewConclusion, Segment, TaskDetail, TaskSource, TaskSummary } from '../types'
import { commandId } from '../types'

export const useLineGuardStore = defineStore('lineguard', () => {
  const segments = ref<Segment[]>([])
  const devices = ref<Device[]>([])
  const tasks = ref<TaskSummary[]>([])
  const activeTask = ref<TaskDetail | null>(null)
  const selectedTask = ref<TaskDetail | null>(null)
  const loading = ref(false)
  const error = ref('')
  const realtimeConnected = ref(false)
  const lastDispatchCommands = new Map<string, string>()
  let eventSource: EventSource | null = null
  let refreshTimer: number | null = null

  const robot = computed(() => devices.value.find(device => device.type === 'ROBOT') ?? null)
  const dock = computed(() => devices.value.find(device => device.type === 'DOCK') ?? null)

  async function initialize() {
    loading.value = true
    error.value = ''
    try {
      await Promise.all([refreshAssets(), refreshLive(), refreshTasks()])
      connectRealtime()
    } catch (reason) {
      setError(reason)
    } finally {
      loading.value = false
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
    selectedTask.value = await api.task(id)
  }

  async function createAndDispatch(source: TaskSource, pointId: string, inspectionItem: string) {
    return run(async () => {
      const created = await api.createTask({ source, inspectionPointId: pointId, inspectionItem })
      const nextCommandId = commandId('dispatch')
      lastDispatchCommands.set(created.task.id, nextCommandId)
      activeTask.value = (await api.dispatch(created.task.id, nextCommandId)).task
      await refreshLive()
      await refreshTasks()
    })
  }

  async function repeatDispatch() {
    const id = activeTask.value?.task.id
    if (!id) return
    const previous = lastDispatchCommands.get(id)
    if (!previous) throw new Error('当前页面没有可重发的派发指令')
    return run(async () => {
      await api.dispatch(id, previous)
      await refreshLive()
    })
  }

  async function submitReview(conclusion: ReviewConclusion, remark: string) {
    const id = activeTask.value?.task.id
    if (!id) return
    return run(async () => {
      activeTask.value = (await api.review(id, {
        commandId: commandId('review'), conclusion, remark, reviewer: '演示运维员',
      })).task
      await refreshLive()
    })
  }

  async function taskAction(action: 'return' | 'abort' | 'recapture') {
    const id = activeTask.value?.task.id
    if (!id) return
    return run(async () => {
      activeTask.value = (await api.taskAction(id, action, commandId(action))).task
      await refreshLive()
      await refreshTasks()
    })
  }

  async function injectFault(type: FaultType, enabled = true) {
    const id = activeTask.value?.task.id
    if (!id) throw new Error('请先启动一个巡检任务')
    return run(async () => {
      await api.injectFault(id, type, enabled)
      await refreshLive()
    })
  }

  async function resetDemo() {
    return run(async () => {
      await api.reset()
      lastDispatchCommands.clear()
      selectedTask.value = null
      await Promise.all([refreshAssets(), refreshLive(), refreshTasks()])
    })
  }

  function connectRealtime() {
    if (eventSource) return
    eventSource = new EventSource('/api/v1/events/stream')
    eventSource.addEventListener('connected', () => {
      realtimeConnected.value = true
    })
    eventSource.addEventListener('lineguard-update', () => {
      realtimeConnected.value = true
      scheduleRefresh()
    })
    eventSource.onerror = () => {
      realtimeConnected.value = false
    }
  }

  function scheduleRefresh() {
    if (refreshTimer !== null) window.clearTimeout(refreshTimer)
    refreshTimer = window.setTimeout(async () => {
      try {
        await refreshLive()
        if (!activeTask.value || activeTask.value.task.status === 'COMPLETED') await refreshTasks()
      } catch (reason) {
        setError(reason)
      }
    }, 100)
  }

  async function run(action: () => Promise<void>) {
    loading.value = true
    error.value = ''
    try {
      await action()
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

  return {
    segments, devices, tasks, activeTask, selectedTask, loading, error,
    realtimeConnected, robot, dock, initialize, refreshAssets, refreshLive,
    refreshTasks, loadTask, createAndDispatch, repeatDispatch, submitReview,
    taskAction, injectFault, resetDemo,
  }
})
