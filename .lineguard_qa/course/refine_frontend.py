from pathlib import Path
root=Path('lineguard-system/frontend/src')
p=root/'App.vue'
s=p.read_text().replace('单档距 · 1,200 m', "单档距 · {{ store.segments[0]?.lengthMeters.toLocaleString() ?? '—' }} m")
p.write_text(s)
p=root/'views/MissionConsole.vue'
s=p.read_text()
a=s.index('const realtimeLabel = computed('); b=s.index('\nonMounted',a)
s=s[:a]+s[b:]
s=s.replace('<span class="eyebrow">CORE MISSION LOOP</span>\n        <h2>单档距近距离巡检闭环</h2>\n        <p>从驻留待命到安全返回，所有动作围绕一条可追溯任务链推进。</p>', '<h2>巡检工作台</h2>\n        <p>{{ task ? task.task.statusMessage : \'设备待命。选择检测点，开始一次近距离巡检。\' }}</p>')
s=s.replace('<div class="view-switch">', '<div class="view-switch" role="group" aria-label="线路视图">')
s=s.replace(':class="{ active: viewMode === \'3d\' }"', ':class="{ active: viewMode === \'3d\' }" :aria-pressed="viewMode === \'3d\'"').replace(':class="{ active: viewMode === \'2d\' }"', ':class="{ active: viewMode === \'2d\' }" :aria-pressed="viewMode === \'2d\'"')
s=s.replace('        <div class="live-chip" :class="{ offline: !store.realtimeConnected }">\n          <span></span>{{ realtimeLabel }}\n        </div>\n', '')
s=s.replace('核心任务进度','任务进度').replace('创建首个巡检任务','新建巡检任务').replace('系统将自动执行预检并启动 Robot。','提交后检查设备状态，预检通过即自动出发。')
s=s.replace('<span class="card-code">NEW MISSION</span>', '<span class="card-code">{{ segment?.inspectionPoints.length ?? 0 }} 个检测点</span>')
s=s.replace('<span class="eyebrow">HUMAN REVIEW</span>', '<span class="eyebrow">检测图像已上传 · 需要人工确认</span>').replace('<span class="eyebrow danger-text">DATA QUALITY ALERT</span>', '<span class="eyebrow danger-text">图像质量异常</span>')
s=s.replace('<div><h3>设备快照</h3><p>预检与运行状态</p></div><span class="card-code">LIVE</span>', '<div><h3>设备状态</h3></div><RouterLink class="card-link" to="/devices">详情 ↗</RouterLink>')
s=s.replace('color="#00a6b7"', 'color="#66836c"')
s=s.replace('<section class="section-card fault-panel">', '<details class="section-card fault-panel">')
s=s.replace('<div class="card-header"><div><h3>异常场景注入</h3><p>验证本地安全策略</p></div><span class="card-code warning">SIM</span></div>', '<summary><div><strong>故障演示</strong><span>6 项场景 · 点击展开</span></div><span class="disclosure-arrow">⌄</span></summary>\n          <p class="fault-note">任务开始后，可注入异常并观察处置。</p>')
s=s.replace('          </div>\n        </section>\n\n        <section class="section-card event-panel">', '          </div>\n        </details>\n\n        <section class="section-card event-panel">')
s=s.replace('<div><h3>最近任务事件</h3><p>状态与动作全程留痕</p></div>', '<div><h3>任务日志</h3></div><span class="card-code">最近 {{ recentEvents.length }} 条</span>')
# 没有运行任务时，故障演示不提供无效操作。
a=s.index('<div class="fault-buttons">'); b=s.index('</details>',a)
s=s[:a]+s[a:b].replace('<button ', '<button :disabled="!task" ')+s[b:]
css='''
.heading-actions { display: flex; align-items: center; gap: 17px; flex: 0 0 auto; }
.view-switch { display: flex; padding: 3px; background: #e9ede5; border-radius: 6px; }
.view-switch button { padding: 7px 12px; color: var(--muted); background: none; border: 0; border-radius: 4px; cursor: pointer; font-size: 12px; }
.view-switch button.active { color: var(--ink); background: #fff; box-shadow: 0 1px 3px #283b2820; }
.scene-placeholder { padding: 70px 20px; color: var(--muted); background: #fff; border: 1px solid var(--line); border-radius: 7px; text-align: center; font-size: 13px; }
.text-button { padding: 8px 0; color: var(--muted); background: none; border: 0; cursor: pointer; font-size: 12px; }
.text-button:hover { color: var(--brand-ink); }
.mission-flow { margin-bottom: 22px; }
.flow-title { min-height: 48px; padding: 10px 20px; display: flex; align-items: center; gap: 14px; border-bottom: 1px solid var(--line); }
.flow-title > span:first-child { font-size: 13px; font-weight: 600; }
.standby-label { margin-left: auto; color: var(--muted); font-size: 12px; }
.flow-title .status-badge { margin-left: auto; }
.flow-steps { display: grid; grid-template-columns: repeat(9, minmax(72px, 1fr)); padding: 18px 20px; overflow: auto; }
.flow-step { position: relative; display: flex; flex-direction: column; gap: 8px; color: #949c91; }
.flow-step::after { content: ''; position: absolute; top: 10px; left: 30px; right: 10px; height: 1px; background: #e1e6dc; }
.flow-step:last-child::after { display: none; }
.step-index { position: relative; z-index: 1; display: grid; place-items: center; width: 21px; height: 21px; background: #f2f4ef; border-radius: 3px; font: 10px/1 var(--mono); }
.flow-step strong { font-size: 11px; font-weight: 400; white-space: nowrap; }
.flow-step.done { color: #64785d; }.flow-step.active { color: var(--brand-ink); }
.flow-step.done .step-index { background: #e2eadd; color: #48623e; }
.flow-step.active .step-index { color: #fff; background: var(--brand-ink); }
.flow-step.active strong { font-weight: 600; }
.exception-strip { padding: 12px 20px; display: flex; gap: 13px; color: #a44f43; background: #fff4ed; border-top: 1px solid #eadbd0; font-size: 12px; }
.console-grid { display: grid; grid-template-columns: minmax(0, 1fr) 292px; gap: 22px; align-items: start; }
.console-main, .console-side { min-width: 0; display: grid; gap: 20px; }
.card-code { color: var(--muted); font-size: 11px; white-space: nowrap; }
.card-link { color: var(--muted); font-size: 12px; text-decoration: none; }.card-link:hover { color: var(--brand-ink); }
.create-form { display: grid; grid-template-columns: 1fr 1.3fr; gap: 18px; }
.create-form label { min-width: 0; display: grid; gap: 9px; }
.create-form label > span { color: #576453; font-size: 12px; }
.create-form .wide-field { grid-column: 1 / -1; }
.primary-action, .secondary-action, .danger-action { min-height: 40px; padding: 10px 16px; border: 1px solid transparent; border-radius: 5px; cursor: pointer; font-size: 13px; }
.primary-action { grid-column: 1 / -1; color: #fff; background: var(--brand-ink); }
.primary-action:hover { background: #234431; }
.primary-action span { float: right; margin-left: 20px; font-size: 17px; }
.create-form .primary-action { justify-self: start; min-width: 200px; }
.primary-action.full { width: 100%; margin-top: 12px; }
.secondary-action { color: var(--ink); background: #fff; border-color: #d8dfd1; }.secondary-action:hover { background: #f4f6f0; }
.danger-action { color: #ae564b; background: #fff; border-color: #e8d9d2; }.danger-action:hover { background: #fff1ec; }
.task-time { color: var(--muted); font-size: 11px; white-space: nowrap; }
.task-facts { display: grid; grid-template-columns: repeat(4, 1fr); padding: 18px 0; border-bottom: 1px solid var(--line); }
.task-facts div { padding: 0 20px; border-right: 1px solid var(--line); }
.task-facts div:last-child { border-right: 0; }
.task-facts span, .task-facts strong { display: block; }
.task-facts span { margin-bottom: 8px; color: var(--muted); font-size: 11px; }
.task-facts strong { font-size: 13px; font-weight: 500; }
.mission-actions { padding: 16px 20px; display: flex; justify-content: flex-end; gap: 10px; }
.recovery-strip { padding: 14px 20px; color: #92642d; background: #faf3e6; font-size: 13px; line-height: 1.7; }
.review-workbench { display: grid; grid-template-columns: 1fr 1fr; padding: 20px; gap: 20px; border-bottom: 1px solid var(--line); }
.detection-preview { position: relative; align-self: start; overflow: hidden; background: #e5eae2; border-radius: 4px; }
.detection-preview img { display: block; width: 100%; aspect-ratio: 4/3; object-fit: contain; }
.image-caption { padding: 10px; display: flex; justify-content: space-between; gap: 8px; color: #71806f; background: #f2f5ed; font-size: 11px; }
.image-caption b { color: #4b6e46; font-weight: 500; }.image-caption b.bad { color: #b65a4b; }
.review-form { min-width: 0; }
.review-form h4 { margin: 7px 0 17px; font-size: 15px; font-weight: 600; }
.review-form p { color: var(--muted); font-size: 12px; line-height: 1.8; }
.review-options { width: 100%; display: grid; grid-template-columns: 1fr; gap: 7px; margin-bottom: 13px; }
.review-options :deep(.el-radio) { margin: 0; height: 32px; padding: 0 10px; }
.review-options :deep(.el-radio__label) { font-size: 12px; }
.danger-text { color: var(--danger); }
.device-row { padding: 18px; display: grid; grid-template-columns: 30px 1fr auto; gap: 10px; align-items: start; border-bottom: 1px solid var(--line); }
.device-symbol { display: grid; place-items: center; width: 30px; height: 30px; color: #5c755b; }
.device-copy strong, .device-copy span, .device-copy small { display: block; }
.device-copy strong { font-size: 13px; font-weight: 600; }.device-copy span { margin-top: 4px; color: #5d7557; font-size: 12px; }.device-copy small { margin-top: 6px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.device-health { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 11px; white-space: nowrap; }
.device-health i { width: 5px; height: 5px; border-radius: 50%; background: #b4aa97; }.device-health i.online { background: #6c8a62; }
.battery-panel { padding: 16px 18px 20px; }
.battery-panel > div { margin-bottom: 9px; display: flex; justify-content: space-between; color: var(--muted); font-size: 12px; }.battery-panel strong { color: var(--ink); font-family: var(--mono); font-weight: 500; }
.fault-panel { overflow: hidden; }
.fault-panel summary { display: flex; align-items: center; justify-content: space-between; padding: 17px 18px; cursor: pointer; list-style: none; }
.fault-panel summary::-webkit-details-marker { display: none; }
.fault-panel summary strong { display: block; font-size: 13px; font-weight: 600; }.fault-panel summary div > span { display: block; margin-top: 5px; color: var(--muted); font-size: 11px; }
.disclosure-arrow { font-size: 18px; color: #909987; }.fault-panel[open] .disclosure-arrow { transform: rotate(180deg); }
.fault-note { margin: 0; padding: 0 18px 14px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.fault-buttons { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--line); }
.fault-buttons button { min-height: 46px; padding: 10px; display: flex; align-items: center; gap: 7px; color: #626e5c; background: #fff; border: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); cursor: pointer; font-size: 12px; text-align: left; }
.fault-buttons button:nth-child(even) { border-right: 0; }.fault-buttons button:hover:not(:disabled) { color: #92642d; background: #fcf5e8; }
.fault-buttons b { color: #a9b1a1; font: 10px/1 var(--mono); }
.fault-recovery { padding: 12px 10px; display: flex; gap: 6px; flex-wrap: wrap; background: #f7f8f4; }
.fault-recovery button { padding: 6px 7px; color: #697461; background: #fff; border: 1px solid var(--line); border-radius: 4px; cursor: pointer; font-size: 11px; }
.event-panel .card-body { max-height: 310px; overflow: auto; }
@media (min-width: 1550px) { .review-options { grid-template-columns: 1fr 1fr; } }
@media (max-width: 1150px) {
  .console-grid { grid-template-columns: minmax(0, 1fr); }
  .console-side { grid-template-columns: 1fr 1fr; align-items: start; }
  .device-snapshot { grid-row: span 2; }
  .review-options { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 760px) {
  .heading-actions { width: 100%; justify-content: space-between; gap: 8px; }
  .mission-flow { margin-bottom: 18px; }
  .flow-title { padding: 10px 16px; }
  .flow-steps { padding: 16px; }
  .console-side { grid-template-columns: 1fr; }
  .device-snapshot { grid-row: auto; }
  .create-form { grid-template-columns: 1fr; }
  .create-form .primary-action { width: 100%; }
  .task-facts { grid-template-columns: 1fr 1fr; gap: 20px 0; }
  .task-facts div:nth-child(even) { border: 0; }
  .review-workbench { grid-template-columns: 1fr; padding: 16px; }
  .review-options { grid-template-columns: 1fr 1fr; }
  .task-time { display: none; }
  .exception-strip { flex-direction: column; gap: 6px; }
}
'''
s=s[:s.index('<style scoped>')]+'<style scoped>\n'+css+'</style>\n'
p.write_text(s)
for file, tag in [('components/SpanScene3D.vue', 'span-3d'), ('components/LineTrack.vue','span-view')]:
 p=root/file; s=p.read_text()
 s=s.replace('<span class="eyebrow">SPAN SCENE · 3D</span>\n        <h3>单档距三维场景</h3>', '<h3>线路现场 <span class="scene-caption">三维示意</span></h3>')
 s=s.replace('<span class="eyebrow">LIVE POSITION</span>\n        <h3>单档距运行视图</h3>', '<h3>全档距概览 <span class="scene-caption">工程视图</span></h3>')
 s=s.replace('border: 1px solid #dce5e8; overflow: hidden;', 'border: 1px solid var(--line); border-radius: 7px; overflow: hidden;')
 s=s.replace('border-bottom: 1px solid #e2eaed;', 'border-bottom: 1px solid var(--line);')
 s=s.replace('margin: 5px 0 0; font-size: 15px;', 'margin: 0; font-size: 14px; font-weight: 600;')
 s=s.replace('height: 420px;', 'height: 320px;')
 s=s.replace('gap: 24px; color: #73868f; background: #fbfcfc;', 'gap: 16px; flex-wrap: wrap; color: var(--muted); background: #fbfcf9;')
 s=s.replace('font-size: 10px; }', 'font-size: 11px; }')
 s=s.replace('</style>', '.scene-caption { margin-left: 8px; color: var(--muted); font-size: 11px; font-weight: 400; }\n@media (max-width: 760px) {\n .'+tag+'__head { padding: 16px; }\n .'+tag+'__legend { padding: 12px 16px; gap: 10px 16px; font-size: 10px; }\n .span-3d__stage { height: 300px; }\n .span-3d__hint { font-size: 10px; }\n}\n</style>')
 p.write_text(s)
