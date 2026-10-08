// 把 Blender 渲染的底版合成文档插图：叠加中文标注、引线与尺寸线。
// 底版里没有文字，是因为 Blender 内置字体缺中文字形；这里走系统字体渲染。
//
// 用法：node tools/compose_figures.mjs
//   依赖 .lineguard_qa/plates/*.png + *.json（由 tools/blender/render_plates.py 产生）
//   输出 .lineguard_qa/assets/{cross-section,docking,three-views}.png

import { createRequire } from 'node:module'
import { mkdirSync, readFileSync } from 'node:fs'

const require = createRequire(
  '/opt/homebrew/lib/node_modules/@mermaid-js/mermaid-cli/node_modules/',
)
const puppeteer = require('puppeteer')

const ROOT = new URL('../', import.meta.url).pathname
const PLATES = `${ROOT}.lineguard_qa/plates/`
const OUT = `${ROOT}.lineguard_qa/assets/`
mkdirSync(OUT, { recursive: true })

const FONT = `-apple-system, "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif`

const spec = name => JSON.parse(readFileSync(PLATES + name + '.json', 'utf8'))
const plate = name => 'data:image/png;base64,' + readFileSync(PLATES + name + '.png').toString('base64')

// 断面图七个标注：列位与文案在此固定，不靠自动分列，避免每次渲染位置漂移。
const SECTION_LABELS = [
  { key: '驱动夹持轮', side: 'left', title: '驱动夹持轮', note: 'V 槽上轮，电机直驱' },
  { key: '检测云台', side: 'left', title: '检测云台', note: '可见光 + 红外双模' },
  { key: '通信天线', side: 'right', title: '通信天线', note: '4G / 自组网回传' },
  { key: '夹持框架', side: 'right', title: '夹持框架', note: '立柱承力，兼作防脱' },
  { key: '压紧轮与弹簧', side: 'right', title: '压紧轮与弹簧', note: '下轮顶紧，夹持力与自重无关' },
  { key: '承载单元', side: 'right', title: '承载单元', note: 'IP65 壳体，控制与存储' },
  { key: '电池与配重', side: 'right', title: '电池包', note: '低重心配重，抑制摆动' },
]

const DOCK_LABELS = {
  'docking-approach': [
    { key: '锁止机构', side: 'right', title: '锁止压板缩回', note: '止挡面让开，等待驶入' },
  ],
  'docking-locked': [
    { key: '锁止机构', side: 'right', title: '锁止压板伸出', note: '压住机壳顶盖，防止回退' },
    { key: '充电触点', side: 'left', title: '充电触点压接', note: '机壳侧壁取电' },
  ],
}

const VIEW_AXES = {
  'three-views-front': { h: 'x', v: 'z' },
  'three-views-top': { h: 'x', v: 'y' },
  'three-views-side': { h: 'y', v: 'z' },
}

const overlay = (w, h) =>
  `<svg class="overlay" width="${w}" height="${h}" style="position:absolute;left:0;top:0"></svg>`

function pageHtml(body, style) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { background: #ffffff; font-family: ${FONT}; }
    .figure { display: inline-block; }
    .row { display: flex; gap: 18px; align-items: flex-start; }
    .panel { position: relative; }
    .panel img { display: block; border-radius: 4px; }
    .step { margin-top: 10px; text-align: center; font-size: 17px; font-weight: 600; color: #1f2d3a; }
    .dims { font-size: 14px; font-weight: 400; color: #5b6b7a; }
    ${style || ''}
  </style></head><body>${body}</body></html>`
}

// 引线走正交折线：标注框边缘 → 竖直段 → 目标点，末端画实心圆点。
function drawCallouts(payload) {
  const { anchors, items, width, font, scope } = payload
  const host = document.querySelectorAll(scope)[payload.index]
  const svg = host.querySelector('svg.overlay')
  svg.style.overflow = 'visible'

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  const LINE1 = 16, LINE2 = 12.5, PAD_X = 14, PAD_Y = 9, GAP = 12, MARGIN = 22
  const measure = (text, size, weight) => {
    ctx.font = `${weight} ${size}px ${font}`
    return ctx.measureText(text).width
  }

  const boxes = items.map(item => {
    const point = anchors[item.key]
    const boxWidth = Math.ceil(Math.max(measure(item.title, LINE1, 600),
                                        measure(item.note, LINE2, 400)) + PAD_X * 2)
    return { ...item, point, width: boxWidth, height: LINE1 + LINE2 + PAD_Y * 2 + 2 }
  })

  const height = Number(svg.getAttribute('height'))
  for (const side of ['left', 'right']) {
    const column = boxes.filter(box => box.side === side).sort((a, b) => a.point[1] - b.point[1])
    let cursor = 6
    for (const box of column) {
      box.y = Math.max(box.point[1] - box.height / 2, cursor)
      cursor = box.y + box.height + GAP
    }
    const overflow = cursor - GAP - (height - 6)
    if (overflow > 0) column.forEach(box => { box.y -= overflow })
  }

  const NS = 'http://www.w3.org/2000/svg'
  const add = (tag, attrs) => {
    const node = document.createElementNS(NS, tag)
    for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, value)
    svg.appendChild(node)
    return node
  }

  for (const box of boxes) {
    const [ax, ay] = box.point
    const x = box.side === 'left' ? MARGIN : width - MARGIN - box.width
    const edge = box.side === 'left' ? x + box.width : x
    const elbow = box.side === 'left' ? edge + 18 : edge - 18
    const centerY = box.y + box.height / 2

    add('path', { d: `M ${edge} ${centerY} H ${elbow} V ${ay} H ${ax}`,
                  fill: 'none', stroke: '#5b6b7a', 'stroke-width': 1.4 })
    add('circle', { cx: ax, cy: ay, r: 6, fill: '#ffffff', stroke: '#5b6b7a', 'stroke-width': 1.4 })
    add('circle', { cx: ax, cy: ay, r: 2.6, fill: '#1f2d3a' })
    add('rect', { x, y: box.y, width: box.width, height: box.height, rx: 6,
                  fill: 'rgba(255,255,255,0.93)', stroke: '#c3ced9', 'stroke-width': 1 })
    add('rect', { x, y: box.y, width: 4, height: box.height, rx: 2,
                  fill: box.side === 'left' ? '#2a7d8c' : '#7a8794' })

    const title = add('text', { x: x + PAD_X + 3, y: box.y + PAD_Y + LINE1 - 4,
                                'font-size': LINE1, 'font-weight': 600,
                                fill: '#17222c', 'font-family': font })
    title.textContent = box.title
    const note = add('text', { x: x + PAD_X + 3, y: box.y + PAD_Y + LINE1 + LINE2 - 2,
                               'font-size': LINE2, fill: '#5b6b7a', 'font-family': font })
    note.textContent = box.note
  }
}

// 正交三视图的尺寸线：像素盒定位，数字取世界坐标包围盒，两者永远对得上。
function drawDims(payload) {
  const { bbox, axes, width, height, font, scope } = payload
  const host = document.querySelectorAll(scope)[payload.index]
  const svg = host.querySelector('svg.overlay')
  svg.style.overflow = 'visible'

  const NS = 'http://www.w3.org/2000/svg'
  const add = (tag, attrs) => {
    const node = document.createElementNS(NS, tag)
    for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, value)
    svg.appendChild(node)
    return node
  }
  const mm = axis => {
    const at = { x: 0, y: 1, z: 2 }[axis]
    return Math.round((bbox.world_max[at] - bbox.world_min[at]) * 1000)
  }
  const ink = '#41505e'
  const [x0, y0] = bbox.min
  const [x1, y1] = bbox.max
  const label = (x, y, text, transform) => {
    const node = add('text', { x, y, 'text-anchor': 'middle', 'font-size': 20,
                               'font-weight': 600, fill: '#17222c', 'font-family': font })
    if (transform) node.setAttribute('transform', transform)
    node.textContent = text
  }

  const hy = Math.min(y1 + 58, height - 30)
  add('path', { d: `M ${x0} ${y0 + 6} V ${hy}`, stroke: ink, 'stroke-width': .9, 'stroke-dasharray': '4 4' })
  add('path', { d: `M ${x1} ${y0 + 6} V ${hy}`, stroke: ink, 'stroke-width': .9, 'stroke-dasharray': '4 4' })
  add('path', { d: `M ${x0} ${hy} H ${x1}`, stroke: ink, 'stroke-width': 1.4 })
  for (const [px, dir] of [[x0, 1], [x1, -1]]) {
    add('path', { d: `M ${px + dir * 13} ${hy - 5} L ${px} ${hy} L ${px + dir * 13} ${hy + 5}`,
                  fill: 'none', stroke: ink, 'stroke-width': 1.4 })
  }
  label((x0 + x1) / 2, hy - 11, `${mm(axes.h)} mm`)

  const vx = Math.max(x0 - 58, 28)
  add('path', { d: `M ${x0 - 6} ${y0} H ${vx}`, stroke: ink, 'stroke-width': .9, 'stroke-dasharray': '4 4' })
  add('path', { d: `M ${x0 - 6} ${y1} H ${vx}`, stroke: ink, 'stroke-width': .9, 'stroke-dasharray': '4 4' })
  add('path', { d: `M ${vx} ${y0} V ${y1}`, stroke: ink, 'stroke-width': 1.4 })
  for (const [py, dir] of [[y0, 1], [y1, -1]]) {
    add('path', { d: `M ${vx - 5} ${py + dir * 13} L ${vx} ${py} L ${vx + 5} ${py + dir * 13}`,
                  fill: 'none', stroke: ink, 'stroke-width': 1.4 })
  }
  label(0, 0, `${mm(axes.v)} mm`, `translate(${vx - 13} ${(y0 + y1) / 2}) rotate(-90)`)
}

const browser = await puppeteer.launch({ headless: true, args: ['--disable-dev-shm-usage'] })
const page = await browser.newPage()

async function load(html, viewport) {
  await page.setViewport({ ...viewport, deviceScaleFactor: 1 })
  await page.setContent(html, { waitUntil: 'load' })
  await page.evaluate(() => document.fonts.ready)
}

async function shoot(name, selector) {
  const target = await page.$(selector)
  await target.screenshot({ path: `${OUT}${name}.png` })
  const box = await target.boundingBox()
  console.log(`  ${name}.png  ${Math.round(box.width)}x${Math.round(box.height)}`)
}

console.log('插图合成：')

// ---- 挂线断面：整幅底版 + 七个中文标注
{
  const data = spec('cross-section')
  const html = pageHtml(
    `<div class="figure"><img class="plate" src="${plate('cross-section')}">${overlay(data.width, data.height)}</div>`,
  )
  await load(html, { width: data.width + 8, height: data.height + 8 })
  await page.evaluate(payload => {
    window.__payload = payload
    const call = new Function(`return (${payload.script})`)()
    call(payload)
  }, { script: drawCallouts.toString(), anchors: data.anchors, items: SECTION_LABELS,
       width: data.width, font: FONT, scope: '.figure', index: 0 })
  await shoot('cross-section', '.figure')
}

// ---- Dock 对接分步：两块独立底版并排，各自带步骤标题与标注
{
  const steps = spec('docking').steps
  const body = steps.map(step =>
    `<div class="panel"><img src="${plate(step.figure)}" width="${step.width}" height="${step.height}">`
    + `${overlay(step.width, step.height)}<div class="step">${step.label}</div></div>`).join('')
  const width = steps.reduce((sum, step) => sum + step.width, 0) + 18 * (steps.length - 1)
  await load(pageHtml(`<div class="figure"><div class="row">${body}</div></div>`),
             { width: width + 8, height: 660 })
  for (const [index, step] of steps.entries()) {
    const items = DOCK_LABELS[step.figure]
    if (!items) continue
    await page.evaluate(payload => {
      window.__payload = payload
      const call = new Function(`return (${payload.script})`)()
      call(payload)
    }, { script: drawCallouts.toString(), anchors: step.anchors, items,
         width: step.width, font: FONT, scope: '.panel', index })
  }
  await shoot('docking', '.figure')
}

// ---- 三视图：三块正交底版并排，各带尺寸线与视图名
{
  const views = spec('three-views').views
  const body = views.map(view =>
    `<div class="panel"><img src="${plate(view.figure)}" width="${view.width}" height="${view.height}">`
    + `${overlay(view.width, view.height)}`
    + `<div class="step">${view.label}　<span class="dims">${view.dims}</span></div></div>`).join('')
  const width = views.reduce((sum, view) => sum + view.width, 0) + 18 * (views.length - 1)
  await load(pageHtml(`<div class="figure"><div class="row">${body}</div></div>`),
             { width: width + 8, height: 900 })
  for (const [index, view] of views.entries()) {
    await page.evaluate(payload => {
      window.__payload = payload
      const call = new Function(`return (${payload.script})`)()
      call(payload)
    }, { script: drawDims.toString(), bbox: view.bbox, axes: VIEW_AXES[view.figure],
         width: view.width, height: view.height, font: FONT, scope: '.panel', index })
  }
  await shoot('three-views', '.figure')
}

await browser.close()
console.log('  完成')
