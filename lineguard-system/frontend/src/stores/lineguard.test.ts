// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Device, Segment, TaskDetail, TaskStatus } from '../types'

// 这个用例覆盖 SSE 的增量更新与重连：
// 事件 payload 被丢弃、每次事件都回源全量重取，或者 onerror 之后状态永远停在
// "正在连接"，都是这套代码实际出过的问题，而且从界面上看只是"有点卡"，很难归因。

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    segments: vi.fn(),
    devices: vi.fn(),
    tasks: vi.fn(),
    activeTask: vi.fn(),
    task: vi.fn(),
    createTask: vi.fn(),
    dispatch: vi.fn(),
    review: vi.fn(),
    taskAction: vi.fn(),
    injectFault: vi.fn(),
    reset: vi.fn(),
  },
}))

vi.mock('../api/client', () => ({ api: apiMock }))

import { useLineGuardStore } from './lineguard'

/** jsdom 没有 EventSource，用最小的替身：能记录实例、能手动触发事件。 */
class FakeEventSource {
  static opened: FakeEventSource[] = []
  readonly url: string
  closed = false
  onerror: ((event: Event) => void) | null = null
  private handlers = new Map<string, ((event: MessageEvent<string>) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.opened.push(this)
  }

  addEventListener(type: string, handler: (event: MessageEvent<string>) => void) {
    this.handlers.set(type, [...(this.handlers.get(type) ?? []), handler])
  }

  close() {
    this.closed = true
  }

  emit(type: string, payload?: unknown) {
    const event = { data: payload === undefined ? '' : JSON.stringify(payload) } as MessageEvent<string>
    for (const handler of this.handlers.get(type) ?? []) handler(event)
  }

  static latest() {
    const source = FakeEventSource.opened.at(-1)
    if (!source) throw new Error('还没有建立 SSE 连接')
    return source
  }
}

const ROBOT: Device = {
  id: 'LG-ROBOT-01', name: 'LineGuard Robot 01', type: 'ROBOT', state: 'DOCKED',
  online: true, batteryPercent: 96, positionMeters: 0, activeTaskId: null,
  statusMessage: '等待任务', updatedAt: '2026-09-17T10:00:00Z',
}
const DOCK: Device = {
  ...ROBOT, id: 'LG-DOCK-01', name: 'LineGuard Dock 01', type: 'DOCK', state: 'LOCKED_CHARGING',
}
const SEGMENT: Segment = {
  id: 'LG-SEG-001', name: '云岭示范区段', lengthMeters: 1000, description: '', inspectionPoints: [],
}
const TASK_ID = '11111111-1111-1111-1111-111111111111'

function taskDetail(status: TaskStatus = 'MOVING'): TaskDetail {
  return {
    task: {
      id: TASK_ID, taskCode: 'LG-20260917-1', source: 'MANUAL', inspectionPointId: 'LG-POINT-350',
      inspectionPointName: '地线附件检测点 A', inspectionItem: '连接金具外观检查',
      status, statusMessage: '前往检测点', createdAt: '2026-09-17T10:00:00Z',
      updatedAt: '2026-09-17T10:00:00Z', completedAt: null,
    },
    inspectionPoint: {
      id: 'LG-POINT-350', name: '地线附件检测点 A', positionMeters: 350,
      defaultInspectionItem: '连接金具外观检查',
    },
    latestDetection: null,
    review: null,
    events: [],
  }
}

function envelope(type: string, payload: Record<string, unknown>, at?: string) {
  return { type, payload, at }
}

async function connectedStore() {
  const store = useLineGuardStore()
  await store.initialize()
  FakeEventSource.latest().emit('connected')
  return store
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
  vi.clearAllMocks()
  FakeEventSource.opened = []
  vi.stubGlobal('EventSource', FakeEventSource)
  window.sessionStorage.clear()

  apiMock.segments.mockResolvedValue([SEGMENT])
  apiMock.devices.mockResolvedValue([{ ...ROBOT }, { ...DOCK }])
  apiMock.tasks.mockResolvedValue([])
  apiMock.activeTask.mockResolvedValue(null)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('SSE 增量更新', () => {
  it('收到 connected 才算接通', async () => {
    const store = useLineGuardStore()
    await store.initialize()
    expect(store.realtimeConnected).toBe(false)

    FakeEventSource.latest().emit('connected')

    expect(store.realtimeStatus).toBe('online')
    expect(store.realtimeConnected).toBe(true)
  })

  it('DEVICES_UPDATED 直接改设备，不再回源', async () => {
    const store = await connectedStore()
    expect(apiMock.devices).toHaveBeenCalledTimes(1)

    FakeEventSource.latest().emit('lineguard-update', envelope('DEVICES_UPDATED', {
      robot: { id: 'LG-ROBOT-01', state: 'MOVING', positionMeters: 350, batteryPercent: 88,
               statusMessage: '沿标定线路前往检测点' },
    }))

    expect(store.robot?.state).toBe('MOVING')
    expect(store.robot?.positionMeters).toBe(350)
    expect(store.robot?.batteryPercent).toBe(88)
    expect(store.robot?.statusMessage).toBe('沿标定线路前往检测点')
    // 快照里没带的设备保持原样，不能被清空
    expect(store.dock?.state).toBe('LOCKED_CHARGING')

    await vi.advanceTimersByTimeAsync(1000)
    expect(apiMock.devices).toHaveBeenCalledTimes(1)
  })

  it('遥测事件只改当前任务的状态与时间线', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('MOVING'))
    const store = await connectedStore()
    expect(store.activeTask?.events).toHaveLength(0)

    FakeEventSource.latest().emit('lineguard-update', envelope('TASK_EVENT', {
      taskId: TASK_ID, eventType: 'TELEMETRY', status: 'MOVING', message: 'Robot 前进到 350 m',
    }, '2026-09-17T10:00:05Z'))

    expect(store.activeTask?.task.statusMessage).toBe('Robot 前进到 350 m')
    expect(store.activeTask?.events).toHaveLength(1)
    expect(store.activeTask?.events[0]).toMatchObject({
      eventType: 'TELEMETRY', toStatus: 'MOVING', createdAt: '2026-09-17T10:00:05Z',
    })

    await vi.advanceTimersByTimeAsync(1000)
    expect(apiMock.devices).toHaveBeenCalledTimes(1)
  })

  it('非遥测事件改完之后要回源确认', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('MOVING'))
    const store = await connectedStore()

    FakeEventSource.latest().emit('lineguard-update', envelope('TASK_EVENT', {
      taskId: TASK_ID, eventType: 'DETECTION_UPLOADED', status: 'AWAITING_REVIEW',
      message: '检测图像已采集',
    }))

    expect(store.activeTask?.task.status).toBe('AWAITING_REVIEW')
    await vi.advanceTimersByTimeAsync(1000)
    expect(apiMock.devices).toHaveBeenCalledTimes(2)
    expect(apiMock.activeTask).toHaveBeenCalledTimes(2)
  })

  it('别的任务的事件不会写进当前任务', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('MOVING'))
    const store = await connectedStore()

    FakeEventSource.latest().emit('lineguard-update', envelope('TASK_EVENT', {
      taskId: '22222222-2222-2222-2222-222222222222', eventType: 'TELEMETRY',
      status: 'COMPLETED', message: '不是当前任务',
    }))

    expect(store.activeTask?.task.status).toBe('MOVING')
    expect(store.activeTask?.events).toHaveLength(0)
  })

  it('事件体解析不了也要回源兜底', async () => {
    await connectedStore()
    FakeEventSource.latest().emit('lineguard-update', '这不是 JSON')
    await vi.advanceTimersByTimeAsync(1000)
    expect(apiMock.devices).toHaveBeenCalledTimes(2)
  })

  it('流断开后进入重连状态并按退避重连', async () => {
    const store = await connectedStore()
    const first = FakeEventSource.latest()

    first.onerror?.(new Event('error'))

    expect(first.closed).toBe(true)
    expect(store.realtimeStatus).toBe('reconnecting')
    expect(FakeEventSource.opened).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(1000)
    expect(FakeEventSource.opened).toHaveLength(2)
    expect(FakeEventSource.latest().url).toBe('/api/v1/events/stream')
  })

  it('首次接通不重复取数', async () => {
    await connectedStore()
    expect(apiMock.devices).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1000)
    expect(apiMock.devices).toHaveBeenCalledTimes(1)
    expect(apiMock.tasks).toHaveBeenCalledTimes(1)
  })

  it('重连后必须回源补齐断线期间的变化', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('MOVING'))
    await connectedStore()
    const first = FakeEventSource.latest()

    // 断线：这段时间里任务可能已经转入等待复核——而该状态不再产生新事件，
    // 光恢复连接不补数据，界面就会永远停在"移动中"
    first.onerror?.(new Event('error'))
    await vi.advanceTimersByTimeAsync(1000)

    FakeEventSource.latest().emit('connected')
    await vi.advanceTimersByTimeAsync(1000)

    expect(apiMock.devices).toHaveBeenCalledTimes(2)
    expect(apiMock.activeTask).toHaveBeenCalledTimes(2)
    // 任务列表也要重取：断线期间可能有新任务或任务完成
    expect(apiMock.tasks).toHaveBeenCalledTimes(2)
  })
})

describe('派发指令编号', () => {
  it('没有派发记录时给出明确提示，而不是发一个空指令', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('CREATED'))
    const store = await connectedStore()

    await expect(store.repeatDispatch()).rejects.toThrow('没有记录该任务的派发指令编号')
    expect(apiMock.dispatch).not.toHaveBeenCalled()
  })

  it('刷新页面后重发，复用的是同一个指令编号（靠 sessionStorage 记住）', async () => {
    apiMock.createTask.mockResolvedValue(taskDetail('CREATED'))
    apiMock.dispatch.mockResolvedValue({ task: taskDetail('DEPARTING') })
    apiMock.activeTask.mockResolvedValue(taskDetail('CREATED'))

    const store = await connectedStore()
    await store.createAndDispatch('MANUAL', 'LG-POINT-350', '连接金具外观检查')
    const issued = apiMock.dispatch.mock.calls.at(-1)?.[1]
    expect(issued).toMatch(/^dispatch-/)

    // 模拟刷新：换一个全新的 store，内存里的记录没了，只能靠 sessionStorage
    setActivePinia(createPinia())
    apiMock.activeTask.mockResolvedValue(taskDetail('DEPARTING'))
    const reloaded = useLineGuardStore()
    await reloaded.initialize()
    await reloaded.repeatDispatch()

    expect(apiMock.dispatch).toHaveBeenCalledTimes(2)
    expect(apiMock.dispatch.mock.calls.at(-1)?.[1]).toBe(issued)
  })
})

// 这一组覆盖"失败了但界面看不出来"：守卫写在 run() 之外时 error 永远不会被写入，
// 调用方的 catch 又是空的，于是按钮点了没有任何反应；预检失败更糟，会弹一条成功提示。
describe('失败必须可见', () => {
  it('没有运行任务时点注入故障：既报错也写进错误栏', async () => {
    const store = await connectedStore()
    expect(store.activeTask).toBeNull()

    await expect(store.injectFault('LOW_BATTERY')).rejects.toThrow('无法注入故障')
    // 关键断言：错误栏必须拿到原因。守卫若在 run() 之外抛，这里会是空串，按钮点了没反应。
    expect(store.error).toContain('无法注入故障')
    expect(apiMock.injectFault).not.toHaveBeenCalled()
  })

  it('预检失败按失败上报，不返回成功', async () => {
    const failed = taskDetail('PRECHECK_FAILED')
    failed.task.statusMessage = 'Robot 电量不足'
    apiMock.createTask.mockResolvedValue(taskDetail('CREATED'))
    apiMock.dispatch.mockResolvedValue({ task: failed })
    // 预检失败的任务不是活动任务，后端 /tasks/active 返回空
    apiMock.activeTask.mockResolvedValue(null)

    const store = await connectedStore()
    await expect(store.createAndDispatch('MANUAL', 'LG-POINT-350', '连接金具外观检查'))
      .rejects.toThrow('Robot 电量不足')
    expect(store.error).toContain('Robot 电量不足')
    // 失败的任务不该被当成当前活动任务挂在界面上
    expect(store.activeTask).toBeNull()
  })

  it('注入故障把后端返回的状态交给调用方，用于区分是否真的转入返航', async () => {
    apiMock.activeTask.mockResolvedValue(taskDetail('MOVING'))
    apiMock.injectFault.mockResolvedValue({ message: '仿真故障指令已处理', status: 'SAFE_PARKED' })

    const store = await connectedStore()
    // 被联锁拦下时状态不是 RETURNING，界面不能报"将执行安全策略"
    await expect(store.injectFault('LOW_BATTERY')).resolves.toBe('SAFE_PARKED')
  })

  it('首屏请求部分失败也要把 SSE 接上，并在接通后补一次全量数据', async () => {
    apiMock.tasks.mockRejectedValue(new Error('任务列表暂不可用'))

    const store = useLineGuardStore()
    await store.initialize()

    // 先前的实现把 connectRealtime 放在三个请求之后，一个失败就永远不连接
    expect(FakeEventSource.opened).toHaveLength(1)
    expect(store.realtimeStatus).toBe('connecting')
    expect(apiMock.tasks).toHaveBeenCalledTimes(1)

    apiMock.tasks.mockResolvedValue([])
    FakeEventSource.latest().emit('connected')
    await vi.advanceTimersByTimeAsync(0)

    // 接通后要补齐首屏那轮没拿到的数据，否则界面会缺数据且没有第二个补救入口
    expect(apiMock.tasks).toHaveBeenCalledTimes(2)
    expect(store.error).toBe('')
  })
})
