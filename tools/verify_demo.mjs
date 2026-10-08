// 在独立演示实例上验证浏览器闭环与真实 HTTP 接口，避免清空日常演示数据。
// 环境变量：LINEGUARD_DEMO_URL、LINEGUARD_API_URL；需 Playwright 与 Chrome。
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const url = process.env.LINEGUARD_DEMO_URL || 'http://127.0.0.1:15173'
const api = process.env.LINEGUARD_API_URL || 'http://127.0.0.1:18080'
const out = new URL('../.lineguard_qa/course/smoke/', import.meta.url).pathname
mkdirSync(out, { recursive: true })
const results = []
const assert = (condition, message) => { if (!condition) throw new Error(message) }
async function request(path, payload, expected = 200) {
  const response = await fetch(`${api}/api/v1${path}`, {
    method: payload === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
  const raw = await response.text()
  assert(response.status === expected, `${path}: ${response.status} ${raw}`)
  return raw ? JSON.parse(raw) : null
}
async function until(id, state) {
  for (let i=0; i<160; i++) {
    const detail = await request(`/tasks/${id}`)
    if (detail.task.status === state) return detail
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error(`任务未到达 ${state}`)
}
const command = () => ({ commandId: crypto.randomUUID() })
async function create() {
  const task = await request('/tasks', { source: 'MANUAL', inspectionPointId: 'LG-POINT-720' }, 201)
  return task.task.id
}
const browser = await chromium.launch({ channel: 'chrome', headless: true,
  args: ['--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--ignore-gpu-blocklist'] })
const page = await browser.newPage({ viewport: { width: 1520, height: 1000 }, deviceScaleFactor: 1 })
const errors = []
page.on('pageerror', error => errors.push(error.message))
try {
  // 只允许操作默认隔离测试端口，显式覆盖地址时需要独立数据库。
  await request('/simulator/reset', {}, 204)
  await page.goto(url)
  await page.locator('.create-mission-card .primary-action').click()
  await page.getByRole('button', { name: '提交结论并下达返航' }).waitFor({ timeout: 30000 })
  assert(await page.locator('.span-3d canvas').count() === 1, '三维场景未创建画布')
  await page.locator('.el-message').waitFor({ state: 'hidden' })
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: `${out}console.png`, fullPage: true })
  const active = await request('/tasks/active')
  assert(active.latestDetection.quality === 'READABLE', '正常检测图不可判读')
  await page.getByRole('button', { name: '提交结论并下达返航' }).click()
  await page.locator('.create-mission-card .primary-action').waitFor({ timeout: 30000 })
  const completed = await until(active.task.id, 'COMPLETED')
  assert(completed.review && completed.latestDetection, '正常闭环缺少图像或复核')
  results.push({ scenario: '浏览器创建、三维展示、复核与对接闭环', passed: true, taskId: active.task.id })
  await page.goto(`${url}/history`)
  await page.locator('.el-table__row').first().click()
  await page.locator('.el-drawer').waitFor()
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${out}history.png`, fullPage: true })
  await page.goto(`${url}/devices`)
  await page.locator('.devices-page').waitFor()
  await page.screenshot({ path: `${out}devices.png`, fullPage: true })
  results.push({ scenario: '历史记录详情与设备页', passed: true })

  let id = await create()
  const same = command()
  await request(`/tasks/${id}/dispatch`, same)
  await request(`/tasks/${id}/dispatch`, same)
  let detail = await until(id, 'AWAITING_REVIEW')
  assert(detail.events.filter(e => e.toStatus === 'DEPARTING' && e.eventType === 'STATUS_CHANGED').length === 1,
    '重复指令重复离站')
  await request(`/tasks/${id}/actions/return`, command())
  await until(id, 'COMPLETED')
  results.push({ scenario: 'HTTP 重复派发只执行一次', passed: true })

  id = await create()
  await request(`/tasks/${id}/dispatch`, command())
  await until(id, 'AWAITING_REVIEW')
  await request('/simulator/faults', { taskId: id, type: 'OBSTACLE', enabled: true })
  await request('/simulator/faults', { taskId: id, type: 'LOW_BATTERY', enabled: true })
  detail = await request(`/tasks/${id}`)
  assert(detail.task.status === 'SAFE_PARKED', '低电量绕过障碍联锁')
  await request(`/tasks/${id}/actions/return`, command(), 409)
  await request('/simulator/faults', { taskId: id, type: 'OBSTACLE', enabled: false })
  await request(`/tasks/${id}/actions/return`, command())
  await until(id, 'COMPLETED')
  results.push({ scenario: 'HTTP 障碍与低电量叠加、解除后返航', passed: true })

  id = await create()
  await request(`/tasks/${id}/dispatch`, command())
  await request('/simulator/faults', { taskId: id, type: 'BLURRED_IMAGE', enabled: true })
  detail = await until(id, 'AWAITING_REVIEW')
  assert(detail.latestDetection.quality === 'UNREADABLE', '模糊图故障未生效')
  await request(`/tasks/${id}/review`, { ...command(), conclusion: 'NO_ABNORMALITY' }, 409)
  await request(`/tasks/${id}/actions/recapture`, command())
  detail = await until(id, 'AWAITING_REVIEW')
  assert(detail.latestDetection.quality === 'READABLE', '重采未恢复可判读图像')
  await request(`/tasks/${id}/review`, { ...command(), conclusion: 'CONTINUE_OBSERVATION', reviewer: '演示运维员' })
  await until(id, 'COMPLETED')
  results.push({ scenario: 'HTTP 模糊图阻止复核、原位重采并完成', passed: true })
  assert(errors.length === 0, `浏览器异常：${errors.join('; ')}`)
  results.push({ scenario: '浏览器运行无未捕获异常', passed: true })
  writeFileSync(`${out}verification.json`, JSON.stringify({ checkedAt: new Date().toISOString(), url, api, results, errors }, null, 2))
  console.log(JSON.stringify(results, null, 2))
} catch (error) {
  await page.screenshot({ path: `${out}failure.png`, fullPage: true }).catch(() => {})
  writeFileSync(`${out}failure.txt`, `${error}\n${await page.locator('body').innerText()}`)
  throw error
} finally {
  await browser.close()
}
