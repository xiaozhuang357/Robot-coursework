<script setup lang="ts">
import type { TaskEvent } from '../types'
import { taskStatusLabels } from '../types'

defineProps<{ events: TaskEvent[]; compact?: boolean }>()

// 事件类型是后端内部命名（全大写下划线）。以前只有 STATUS_CHANGED 会被翻译，
// 其余类型直接把 TASK_CREATED、TELEMETRY 这种字样摆到时间线上，一条中文界面里
// 混着两套写法。这里把后端实际会发出的类型都给出中文名，未收录的才退回原样，
// 以便新类型出现时不至于显示成空白。
const eventTypeLabels: Record<string, string> = {
  TELEMETRY: '位置上报',
  DOCK_RETRY: '对接重试',
  FAULT_INJECTED: '注入故障',
  FAULT_CLEARED: '故障解除',
  FAULT_ARMED: '故障待触发',
  SAFE_PARK: '安全驻停',
  DUPLICATE_COMMAND: '重复指令',
  STARTUP_RECOVERY: '重启恢复',
}

/** 状态迁移类事件用状态名作标题，其余用事件类型的中文名。 */
function title(event: TaskEvent) {
  if (event.eventType === 'STATUS_CHANGED' || event.eventType === 'TASK_CREATED') {
    return taskStatusLabels[event.toStatus] ?? event.eventType
  }
  return eventTypeLabels[event.eventType] ?? event.eventType
}

function time(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <div class="task-timeline" :class="{ compact }">
    <div v-for="event in events" :key="event.id" class="timeline-row">
      <div class="timeline-rail"><span></span></div>
      <div class="timeline-content">
        <div>
          <strong>{{ title(event) }}</strong>
          <time>{{ time(event.createdAt) }}</time>
        </div>
        <p>{{ event.message }}</p>
      </div>
    </div>
  </div>
</template>

