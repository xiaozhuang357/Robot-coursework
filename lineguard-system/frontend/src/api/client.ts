import type {
  Device,
  FaultType,
  ReviewConclusion,
  Segment,
  TaskDetail,
  TaskSource,
  TaskSummary,
} from '../types'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers ?? {}),
    },
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { detail?: string } | null
    throw new Error(payload?.detail ?? `请求失败（${response.status}）`)
  }
  if (response.status === 204) return null as T
  return response.json() as Promise<T>
}

export const api = {
  segments: () => request<Segment[]>('/api/v1/assets/segments'),
  devices: () => request<Device[]>('/api/v1/devices'),
  tasks: () => request<TaskSummary[]>('/api/v1/tasks'),
  activeTask: () => request<TaskDetail | null>('/api/v1/tasks/active'),
  task: (id: string) => request<TaskDetail>(`/api/v1/tasks/${id}`),
  createTask: (payload: {
    source: TaskSource
    inspectionPointId: string
    inspectionItem?: string
  }) => request<TaskDetail>('/api/v1/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  dispatch: (taskId: string, commandId: string) => request<{ task: TaskDetail }>(
    `/api/v1/tasks/${taskId}/dispatch`,
    { method: 'POST', body: JSON.stringify({ commandId }) },
  ),
  review: (taskId: string, payload: {
    commandId: string
    conclusion: ReviewConclusion
    remark: string
    reviewer: string
  }) => request<{ task: TaskDetail }>(`/api/v1/tasks/${taskId}/review`, {
    method: 'POST', body: JSON.stringify(payload),
  }),
  taskAction: (taskId: string, action: 'return' | 'abort' | 'recapture', commandId: string) =>
    request<{ task: TaskDetail }>(`/api/v1/tasks/${taskId}/actions/${action}`, {
      method: 'POST', body: JSON.stringify({ commandId }),
    }),
  injectFault: (taskId: string, type: FaultType, enabled = true) =>
    request<{ message: string; status: string }>('/api/v1/simulator/faults', {
      method: 'POST', body: JSON.stringify({ taskId, type, enabled }),
    }),
  reset: () => request<void>('/api/v1/simulator/reset', { method: 'POST' }),
}

