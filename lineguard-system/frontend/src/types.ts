export type TaskStatus =
  | 'CREATED' | 'PRECHECKING' | 'DEPARTING' | 'MOVING' | 'INSPECTING'
  | 'UPLOADING' | 'AWAITING_REVIEW' | 'RETURNING' | 'DOCKING' | 'COMPLETED'
  | 'PRECHECK_FAILED' | 'WAITING_MANUAL' | 'SAFE_PARKED' | 'ABORTED'

export type TaskSource = 'MANUAL' | 'PERIODIC' | 'EXTERNAL_ALERT'
export type ReviewConclusion =
  | 'NO_ABNORMALITY' | 'CONTINUE_OBSERVATION'
  | 'SPECIAL_REINSPECTION' | 'DISPATCH_PERSONNEL'
export type DetectionQuality = 'READABLE' | 'UNREADABLE'
export type FaultType =
  | 'LOW_BATTERY' | 'COMMUNICATION_LOSS' | 'OBSTACLE'
  | 'DOCKING_FAILURE' | 'BLURRED_IMAGE'

export interface InspectionPoint {
  id: string
  name: string
  positionMeters: number
  defaultInspectionItem: string
}

export interface Segment {
  id: string
  name: string
  lengthMeters: number
  description: string
  inspectionPoints: InspectionPoint[]
}

export interface Device {
  id: string
  name: string
  type: 'ROBOT' | 'DOCK'
  state: string
  online: boolean
  batteryPercent: number
  positionMeters: number
  activeTaskId: string | null
  statusMessage: string
  updatedAt: string
}

export interface TaskSummary {
  id: string
  taskCode: string
  source: TaskSource
  inspectionPointId: string
  inspectionPointName: string
  inspectionItem: string
  status: TaskStatus
  statusMessage: string
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export interface Detection {
  id: string
  inspectionPointId: string
  positionMeters: number
  quality: DetectionQuality
  captureIndex: number
  capturedAt: string
  imageUrl: string
}

export interface Review {
  conclusion: ReviewConclusion
  remark: string
  reviewer: string
  reviewedAt: string
}

export interface TaskEvent {
  id: number
  fromStatus: TaskStatus | null
  toStatus: TaskStatus
  eventType: string
  message: string
  createdAt: string
}

export interface TaskDetail {
  task: TaskSummary
  inspectionPoint: InspectionPoint
  latestDetection: Detection | null
  review: Review | null
  events: TaskEvent[]
}

export const coreTaskSteps: TaskStatus[] = [
  'PRECHECKING', 'DEPARTING', 'MOVING', 'INSPECTING', 'UPLOADING',
  'AWAITING_REVIEW', 'RETURNING', 'DOCKING', 'COMPLETED',
]

export const taskStatusLabels: Record<TaskStatus, string> = {
  CREATED: '待派发',
  PRECHECKING: '任务预检',
  DEPARTING: '解除驻留',
  MOVING: '前往检测点',
  INSPECTING: '定点检测',
  UPLOADING: '数据上传',
  AWAITING_REVIEW: '等待复核',
  RETURNING: '安全返航',
  DOCKING: '对接驻留',
  COMPLETED: '任务完成',
  PRECHECK_FAILED: '预检未通过',
  WAITING_MANUAL: '等待人工处理',
  SAFE_PARKED: '安全驻停',
  ABORTED: '任务已终止',
}

export const sourceLabels: Record<TaskSource, string> = {
  MANUAL: '人工任务',
  PERIODIC: '周期任务',
  EXTERNAL_ALERT: '外部告警复核',
}

export const reviewLabels: Record<ReviewConclusion, string> = {
  NO_ABNORMALITY: '未见异常',
  CONTINUE_OBSERVATION: '继续观察',
  SPECIAL_REINSPECTION: '安排专项复检',
  DISPATCH_PERSONNEL: '派遣现场人员',
}

export const stateLabels: Record<string, string> = {
  DOCKED: '驻留充电',
  UNDOCKING: '解除锁止',
  MOVING_OUTBOUND: '沿线出发',
  INSPECTING: '正在检测',
  WAITING_REVIEW: '等待复核',
  MOVING_RETURN: '正在返航',
  DOCKING: '正在对接',
  SAFE_PARKED: '安全驻停',
  FAULT: '设备故障',
  LOCKED_CHARGING: '锁止补能',
  RELEASING: '释放机器人',
  WAITING_RETURN: '等待返回',
}

export function taskStatusTone(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (['PRECHECK_FAILED', 'WAITING_MANUAL', 'SAFE_PARKED'].includes(status)) return 'danger'
  if (status === 'ABORTED') return 'info'
  if (status === 'AWAITING_REVIEW') return 'warning'
  return 'primary'
}

export function commandId(prefix: string): string {
  const id = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${id}`
}

