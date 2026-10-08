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
  try {
    await store.loadTask(row.id)
  } catch {
    // 原因已进全局错误栏；打开抽屉失败就保持关闭，不再抛未捕获的 rejection。
  }
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}

/** 任务用时：计划书 §8.2 说平台要展示各状态与用时，这里把它显式算出来。
 *  未完成的任务没有完成时间，显示"进行中"而不是一个假的秒数。 */
function formatDuration(row: TaskSummary) {
  if (!row.completedAt) return '进行中'
  const seconds = Math.max(0, Math.round(
    (new Date(row.completedAt).getTime() - new Date(row.createdAt).getTime()) / 1000))
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`
  return `${Math.floor(minutes / 60)} 时 ${minutes % 60} 分`
}
</script>

<template>
  <div class="page-content history-page" v-loading="store.loading">
    <div class="page-heading">
      <div>
        <h2>巡检档案</h2>
        <p>检查每次巡检的状态链、检测结果和人工结论。</p>
      </div>
      <button class="refresh-button" @click="store.refreshTasks">刷新记录</button>
    </div>

    <section class="section-card history-table">
      <div class="archive-toolbar"><strong>全部任务 <span>{{ store.tasks.length }}</span></strong><p>点击记录，查看图像和复核结论</p></div>
      <el-table :data="store.tasks" row-key="id" @row-click="openTask">
        <template #empty><div class="empty-panel">暂无任务记录，请先在任务控制台执行一次巡检。</div></template>
        <el-table-column label="任务编号" min-width="205">
          <template #default="{ row }"><span class="mono task-code">{{ row.taskCode }}</span></template>
        </el-table-column>
        <el-table-column label="任务来源" min-width="125">
          <template #default="{ row }">{{ sourceLabels[row.source as keyof typeof sourceLabels] }}</template>
        </el-table-column>
        <el-table-column prop="inspectionPointName" label="检测目标" min-width="150" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="175">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="175">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="用时" width="120">
          <template #default="{ row }">{{ formatDuration(row) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" size="min(640px, 100vw)" title="任务完整记录">
      <template v-if="store.selectedTask">
        <div class="detail-head">
          <div><span class="eyebrow">巡检任务</span><h3>{{ store.selectedTask.task.taskCode }}</h3></div>
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
.refresh-button { padding: 10px 16px; color: var(--ink); background: #fff; border: 1px solid #d5dccd; border-radius: 5px; cursor: pointer; font-size: 12px; }
.refresh-button:hover { background: #edf2e9; }
.history-table { overflow: hidden; }
.archive-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px; }
.archive-toolbar strong { font-size: 13px; font-weight: 600; }
.archive-toolbar strong span { margin-left: 8px; padding: 2px 7px; color: var(--muted); background: #f0f3ec; border-radius: 4px; font: 12px var(--mono); }
.archive-toolbar p { margin: 0; color: var(--muted); font-size: 12px; }
.history-table :deep(.el-table__row) { cursor: pointer; }
.history-table :deep(.el-table__header th) { color: var(--muted); background: #f4f6f0; font-size: 12px; font-weight: 500; }
.history-table :deep(.el-table__cell) { padding: 16px 0; font-size: 12px; border-color: var(--line); }
.history-table :deep(.cell) { padding: 0 16px; }
.history-table :deep(.el-table__row:hover > td) { background: #f8faf4; }
.task-code { color: var(--brand-ink); font-size: 11px; }
.detail-head { padding-bottom: 20px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--line); }
.detail-head h3 { margin: 8px 0 0; font-size: 17px; font-weight: 600; word-break: break-word; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; margin: 20px 0; gap: 22px 16px; }
.detail-grid span, .detail-grid strong { display: block; }
.detail-grid span { margin-bottom: 8px; color: var(--muted); font-size: 12px; }
.detail-grid strong { font-size: 13px; font-weight: 500; line-height: 1.7; }
.archive-image { overflow: hidden; margin: 24px 0; border: 1px solid var(--line); border-radius: 5px; background: #f3f6ee; }
.archive-image img { display: block; width: 100%; max-height: 280px; object-fit: contain; }
.archive-image span { display: block; padding: 10px 14px; color: var(--muted); font-size: 12px; }
.review-result { margin-bottom: 24px; padding: 18px; background: #f0f4eb; border-radius: 5px; }
.review-result span, .review-result strong { display: block; }
.review-result span { color: var(--muted); font-size: 12px; }
.review-result strong { margin-top: 8px; color: var(--brand-ink); font-size: 15px; font-weight: 500; }
.review-result p { margin: 10px 0 0; color: #6e7b64; font-size: 12px; line-height: 1.7; }
.timeline-title { margin: 28px 0 20px; font-size: 14px; font-weight: 600; }
@media (max-width: 760px) { .archive-toolbar { padding: 16px; flex-wrap: wrap; } .archive-toolbar p { font-size: 11px; } }
</style>
