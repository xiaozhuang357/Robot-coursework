from pathlib import Path
root=Path('lineguard-system/frontend/src/views')
p=root/'TaskHistory.vue'; s=p.read_text()
s=s.replace('<span class="eyebrow">MISSION ARCHIVE</span>\n        <h2>任务记录与复核档案</h2>', '<h2>巡检档案</h2>')
s=s.replace('<section class="section-card history-table">', '<section class="section-card history-table">\n      <div class="archive-toolbar"><strong>全部任务 <span>{{ store.tasks.length }}</span></strong><p>点击记录，查看图像和复核结论</p></div>')
s=s.replace('size="600px"', 'size="min(640px, 100vw)"')
s=s.replace('<span class="eyebrow">MISSION DETAIL</span>', '<span class="eyebrow">巡检任务</span>')
s=s.replace('<el-table :data="store.tasks" row-key="id" @row-click="openTask">', '<el-table :data="store.tasks" row-key="id" @row-click="openTask">\n        <template #empty><div class="empty-panel">暂无任务记录，请先在任务控制台执行一次巡检。</div></template>')
s=s.replace('      <div v-if="!store.tasks.length" class="empty-panel">暂无任务记录，请先在任务控制台执行一次巡检。</div>\n', '')
s=s.replace('min-width="205"', 'min-width="205"').replace('min-width="180"', 'min-width="150"')
s=s[:s.index('<style scoped>')]+'''<style scoped>
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
'''; p.write_text(s)
p=root/'DeviceStatus.vue'; s=p.read_text()
s=s.replace('<span class="eyebrow">DEVICE READINESS</span>\n        <h2>Robot 与 Dock 设备状态</h2>','<h2>设备与线路</h2>')
s=s.replace("{{ device.online ? 'ONLINE' : 'OFFLINE' }}", "{{ device.online ? '在线' : '离线' }}")
s=s.replace('首阶段固定检查项', '新任务启动前，所有条件需同时满足')
s=s[:s.index('<style scoped>')]+'''<style scoped>
.device-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; margin-bottom: 22px; }
.device-card { padding: 24px; }
.device-card-head { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; gap: 14px; align-items: center; padding-bottom: 22px; border-bottom: 1px solid var(--line); }
.large-symbol { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 7px; background: #edf2e7; color: #586e4f; }
.large-symbol.dock { color: #69766a; background: #f0f2ee; }
.device-card-head h3 { margin: 6px 0 4px; font-size: 16px; font-weight: 600; }
.device-card-head small { color: var(--muted); font-size: 11px; }
.online-state { display: flex; align-items: center; gap: 6px; color: #9c7652; font-size: 12px; }
.online-state i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.online-state.online { color: #607e53; }
.device-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 26px 16px; padding: 24px 0; }
.device-metrics span, .device-metrics strong { display: block; }
.device-metrics span { margin-bottom: 10px; color: var(--muted); font-size: 12px; }
.device-metrics strong { font-size: 19px; font-weight: 500; font-variant-numeric: tabular-nums; }
.device-metrics div:last-child strong { font-size: 13px; line-height: 26px; }
.device-message { padding: 12px 14px; color: #748068; background: #f5f7f0; border-radius: 4px; font-size: 12px; line-height: 1.7; }
.lower-grid { display: grid; grid-template-columns: .95fr 1.05fr; gap: 22px; align-items: start; }
.readiness-list > div { padding: 17px 20px; display: grid; grid-template-columns: 12px 1fr auto; gap: 10px; align-items: center; border-bottom: 1px solid var(--line); font-size: 13px; }
.readiness-list > div:last-child { border: 0; }
.readiness-list i { width: 6px; height: 6px; border-radius: 50%; background: #b87e5f; }.readiness-list i.pass { background: #6f8b59; }
.readiness-list b { color: var(--muted); font-size: 11px; font-weight: 400; }
.asset-card .card-header { align-items: flex-start; }
.asset-card .card-header > strong { color: #61744e; font: 19px/1.4 var(--mono); white-space: nowrap; }
.asset-points > div { padding: 20px; display: grid; grid-template-columns: 68px 1fr; gap: 7px 13px; align-items: center; border-bottom: 1px solid var(--line); }
.asset-points > div:last-child { border: 0; }
.point-position { color: #5c744b; font: 14px/1 var(--mono); }
.asset-points strong, .asset-points small { display: block; }
.asset-points strong { font-size: 13px; font-weight: 500; }
.asset-points small { margin-top: 7px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.point-id { grid-column: 2; color: #9aa38f; font-size: 10px; }
@media (max-width: 1100px) { .lower-grid { grid-template-columns: 1fr; } .device-card { padding: 20px; } .device-card-head { gap: 10px; grid-template-columns: 36px minmax(0,1fr) auto; } .large-symbol { width: 34px; height: 38px; } }
@media (max-width: 760px) { .device-grid { grid-template-columns: 1fr; gap: 18px; } .device-card { padding: 20px; } }
</style>
'''; p.write_text(s)
