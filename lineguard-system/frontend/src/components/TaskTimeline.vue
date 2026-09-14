<script setup lang="ts">
import type { TaskEvent } from '../types'
import { taskStatusLabels } from '../types'

defineProps<{ events: TaskEvent[]; compact?: boolean }>()

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
          <strong>{{ event.eventType === 'STATUS_CHANGED' ? taskStatusLabels[event.toStatus] : event.eventType }}</strong>
          <time>{{ time(event.createdAt) }}</time>
        </div>
        <p>{{ event.message }}</p>
      </div>
    </div>
  </div>
</template>

