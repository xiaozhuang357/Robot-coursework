// 用 mermaid-cli 自带的 puppeteer 抓取原型界面截图。
// 机械插图已改为 Blender 渲染，见 tools/blender/render_plates.py + tools/compose_figures.mjs。
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'

const require = createRequire(
  '/opt/homebrew/lib/node_modules/@mermaid-js/mermaid-cli/node_modules/',
)
const puppeteer = require('puppeteer')

const ROOT = new URL('../', import.meta.url).pathname
const OUT = `${ROOT}.lineguard_qa/screenshots/`
mkdirSync(OUT, { recursive: true })

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function waitForText(page, selector, expected, timeout = 30000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const text = await page.$eval(selector, node => node.textContent).catch(() => '')
    if (text.includes(expected)) return true
    await sleep(300)
  }
  return false
}

// 三维场景需要 WebGL：使用新版无头模式并启用 SwiftShader 软件渲染，
// chrome-headless-shell 不带 GPU 能力，会把渲染进程直接拖崩。
const browser = await puppeteer.launch({
  headless: true,
  args: [
    '--enable-unsafe-swiftshader',
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--ignore-gpu-blocklist',
    '--enable-webgl',
    '--disable-dev-shm-usage',
  ],
})

const page = await browser.newPage()
await page.setViewport({ width: 1600, height: 1000, deviceScaleFactor: 2 })

const missed = []

async function shoot(name, selector) {
  const target = await page.$(selector)
  if (!target) {
    missed.push(name)
    console.log(`  跳过 ${name}：未找到 ${selector}`)
    return
  }
  await target.screenshot({ path: `${OUT}${name}.png` })
  const box = await target.boundingBox()
  console.log(`  ${name}.png  ${Math.round(box.width)}x${Math.round(box.height)}`)
}

/** 当前控制台顶部的状态徽章文案，用于在驱动失败时报出实际停在哪一步。 */
const badgeText = () => page
  .evaluate(() => document.querySelector('.status-badge')?.textContent?.trim() ?? '(无状态徽章)')

console.log('界面截图：')
// 先重置演示环境，否则上一轮截图留下的活动任务会让创建表单不再出现。
try {
  await fetch('http://localhost:8080/api/v1/simulator/reset', { method: 'POST' })
} catch {
  console.log('  后端未启动，跳过界面截图。')
  await browser.close()
  process.exit(0)
}
try {
  await page.goto('http://localhost:5173', { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForSelector('.span-3d, .span-view', { timeout: 30000 })
} catch (error) {
  console.log('  前端未启动，跳过界面截图。', error instanceof Error ? error.message : error)
  await browser.close()
  process.exit(0)
}
await wait(1500)
await shoot('console-idle', '.mission-flow')
await shoot('scene-3d', '.span-3d')

const createButton = await page.$('.create-mission-card .primary-action')
if (!createButton) {
  // 以前这里是 if(createButton){...} 没有 else：一旦驱动不了，整段流程静默跳过，
  // 而旧截图还留在磁盘上，看起来像本轮跑成功了。现在必须报出来。
  missed.push('创建任务流程全部截图')
  console.log(`  驱动不了演示流程：控制台没有建任务卡片（当前状态：${await badgeText()}）`)
} else {
  await createButton.click()
  if (!await waitForText(page, '.status-badge', '等待复核', 40000)) {
    missed.push('任务进入等待复核')
    console.log(`  任务未在 40 秒内进入等待复核（当前状态：${await badgeText()}）`)
  }
  await wait(1800)
  await shoot('scene-3d-running', '.span-3d')
  // 切到工程视图补抓二维场景
  const viewButtons = await page.$$('.view-switch button')
  if (viewButtons[1]) {
    await viewButtons[1].click()
    await wait(700)
    await shoot('console-running', '.span-view')
    await viewButtons[0].click()
    await wait(500)
  }
  await shoot('review-workbench', '.review-workbench')
  // 这一张要趁任务还在飞的时候拍：任务闭环后控制台退回空闲态，信息量少一大截
  await shoot('console-full', '.mission-console')

  // 走完最后一段：提交复核结论并观察返航到对接完成。
  // 这一段以前只在接口与集成测试里验过，界面上从没点过。
  // 注意不能用状态徽章的文案判断结束：任务一旦进入终态就不再是"活动任务"，
  // 控制台会退回可建任务的空闲态，徽章直接消失。可靠的信号是建任务卡片重新出现。
  const reviewButton = await page.$('.review-workbench button.primary-action.full')
  if (reviewButton) {
    await reviewButton.click()
    try {
      await page.waitForSelector('.create-mission-card .primary-action', { timeout: 90000 })
      console.log('  复核提交后任务已闭环（控制台回到可建任务的空闲态）')
    } catch {
      console.log('  复核提交后未在超时内回到空闲态，请检查复核链路')
    }
  }

  await shoot('mission-flow', '.mission-flow')
}

await page.goto('http://localhost:5173/devices', { waitUntil: 'networkidle2' })
await wait(1500)
await shoot('devices', '.devices-page')

// 任务历史：列表 + 点开一条任务后的完整记录抽屉
await page.goto('http://localhost:5173/history', { waitUntil: 'networkidle2' })
await wait(1500)
await shoot('history', '.history-page')

const firstRow = await page.$('.history-page .el-table__row')
if (firstRow) {
  await firstRow.click()
  await wait(1400)
  await shoot('history-detail', '.el-drawer')
}

await browser.close()

if (missed.length) {
  console.log(`\n有 ${missed.length} 项没拿到：${missed.join('、')}`)
  console.log('对应文件仍是上一轮的旧内容，不能当本轮证据。')
  process.exitCode = 1
}
console.log(`界面截图输出：${OUT}`)
