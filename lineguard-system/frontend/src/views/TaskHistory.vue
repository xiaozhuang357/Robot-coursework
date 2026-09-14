<script setup lang="ts">
import { computed, onMounted } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import TaskTimeline from '../components/TaskTimeline.vue'
import { useLineGuardStore } from '../stores/lineguard'
import { reviewLabels, sourceLabels, type TaskSummary } from '../types'

const store = useLineGuardStore()
const detailVisible = computed({
  get: () => store.selectedTask !== null,
  set: (value: boolean) => { if (!value) store.selectedTask = null },
})

onMounted(async () => {
  if (!store.segments.length) await store.initialize()
  else await store.refreshTasks()
})

async function openTask(row: TaskSummary) {
  await store.loadTask(row.id)
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <div class="page-content history-page" v-loading="store.loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">MISSION ARCHIVE</span>
        <h2>任务记录与复核档案</h2>
        <p>检查每次巡检的状态链、检测结果和人工结论。</p>
      </div>
      <button class="refresh-button" @click="store.refreshTasks">刷新记录</button>
    </div>

    <section class="section-card history-table">
      <el-table :data="store.tasks" row-key="id" @row-click="openTask">
        <el-table-column label="任务编号" min-width="205">
          <template #default="{ row }"><span class="mono task-code">{{ row.taskCode }}</span></template>
        </el-table-column>
        <el-table-column label="任务来源" min-width="125">
          <template #default="{ row }">{{ sourceLabels[row.source as keyof typeof sourceLabels] }}</template>
        </el-table-column>
        <el-table-column prop="inspectionPointName" label="检测目标" min-width="180" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="175">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="175">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="!store.tasks.length" class="empty-panel">暂无任务记录，请先在任务控制台执行一次巡检。</div>
    </section>

    <el-drawer v-model="detailVisible" size="600px" title="任务完整记录">
      <template v-if="store.selectedTask">
        <div class="detail-head">
          <div><span class="eyebrow">MISSION DETAIL</span><h3>{{ store.selectedTask.task.taskCode }}</h3></div>
          <StatusBadge :status="store.selectedTask.task.status" />
        </div>
        <div class="detail-grid">
          <div><span>检测目标</span><strong>{{ store.selectedTask.inspectionPoint.name }}</strong></div>
          <div><span>目标位置</span><strong>{{ store.selectedTask.inspectionPoint.positionMeters }} m</strong></div>
          <div><span>检测内容</span><strong>{{ store.selectedTask.task.inspectionItem }}</strong></div>
          <div><span>任务来源</span><strong>{{ sourceLabels[store.selectedTask.task.source] }}</strong></div>
        </div>
        <div v-if="store.selectedTask.latestDetection" class="archive-image">
          <img :src="store.selectedTask.latestDetection.imageUrl" alt="历史任务检测图像" />
          <span>第 {{ store.selectedTask.latestDetection.captureIndex }} 次采集 · {{ store.selectedTask.latestDetection.quality === 'READABLE' ? '可判读' : '不可判读' }}</span>
        </div>
        <div v-if="store.selectedTask.review" class="review-result">
          <span>人工复核结论</span>
          <strong>{{ reviewLabels[store.selectedTask.review.conclusion] }}</strong>
          <p>{{ store.selectedTask.review.remark || '未填写补充说明' }}</p>
        </div>
        <h4 class="timeline-title">任务状态时间线</h4>
        <TaskTimeline :events="store.selectedTask.events" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.refresh-button { padding: 9px 14px; color: #087f8c; background: #fff; border: 1px solid #b9dadd; cursor: pointer; font-size: 11px; }
.history-table { overflow: hidden; }
.history-table :deep(.el-table__row) { cursor: pointer; }
.history-table :deep(.el-table__header th) { color: #71838d; background: #f5f8f9; font-size: 10px; font-weight: 600; }
.history-table :deep(.el-table__cell) { padding: 13px 0; font-size: 11px; }
.task-code { color: #176878; font-size: 10px; }
.detail-head { padding-bottom: 17px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e2e9eb; }
.detail-head h3 { margin: 6px 0 0; font-size: 17px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; margin: 18px 0; border: 1px solid #e1e8ea; }
.detail-grid div { min-height: 72px; padding: 13px; border-right: 1px solid #e1e8ea; border-bottom: 1px solid #e1e8ea; }
.detail-grid div:nth-child(even) { border-right: 0; }.detail-grid div:nth-child(n+3) { border-bottom: 0; }
.detail-grid span, .detail-grid strong { display: block; }.detail-grid span { margin-bottom: 7px; color: #82939a; font-size: 9px; }.detail-grid strong { color: #2b414a; font-size: 11px; }
.archive-image { overflow: hidden; margin-bottom: 17px; border: 1px solid #dce5e8; }
.archive-image img { display: block; width: 100%; max-height: 260px; object-fit: cover; }.archive-image span { display: block; padding: 9px 12px; color: #71838d; font-size: 9px; }
.review-result { margin-bottom: 20px; padding: 14px; background: #eef9f9; border-left: 3px solid #0da4b1; }
.review-result span, .review-result strong { display: block; }.review-result span { color: #6f858e; font-size: 9px; }.review-result strong { margin-top: 5px; color: #087f8c; font-size: 13px; }.review-result p { margin: 8px 0 0; color: #627780; font-size: 10px; }
.timeline-title { margin: 24px 0 16px; color: #2b414a; font-size: 13px; }
</style>

