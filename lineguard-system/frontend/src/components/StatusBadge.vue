<script setup lang="ts">
import { computed } from 'vue'
import type { TaskStatus } from '../types'
import { taskStatusLabels, taskStatusTone } from '../types'

const props = defineProps<{ status: TaskStatus }>()
const tone = computed(() => taskStatusTone(props.status))
</script>

<template>
  <span class="status-badge" :class="`tone-${tone}`">
    <span class="status-dot"></span>
    {{ taskStatusLabels[status] }}
  </span>
</template>

