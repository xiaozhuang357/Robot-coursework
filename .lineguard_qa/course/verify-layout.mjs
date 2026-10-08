import { createRequire } from 'node:module'
import { writeFileSync } from 'node:fs'
const { chromium } = createRequire(import.meta.url)('playwright')
const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--enable-unsafe-swiftshader','--use-angle=swiftshader'] })
const page = await browser.newPage()
const root = 'http://127.0.0.1:15173'
const out = new URL('./smoke/', import.meta.url).pathname
const results=[]
const errors=[]
page.on('pageerror', e=>errors.push(e.message))
async function check(name) {
 const metrics=await page.evaluate(()=>({viewport:innerWidth,width:document.documentElement.scrollWidth}))
 if(metrics.width>metrics.viewport+1) throw Error(`${name} 横向溢出: ${JSON.stringify(metrics)}`)
 results.push({scenario:name,passed:true,...metrics})
}
try {
 for(const width of [1440,1024,768,390,320]) {
  await page.setViewportSize({width,height:900})
  for(const route of ['','history','devices']) {
   await page.goto(`${root}/${route}`)
   await page.waitForFunction(()=>document.querySelector('.workspace-label')?.textContent?.includes('1,000'))
   await page.waitForTimeout(250)
   await check(`${width}px ${route||'console'}`)
   if(route==='') {
    await page.screenshot({path:`${out}console-${width}.png`,fullPage:true})
    await page.getByRole('button',{name:'工程视图',exact:true}).click()
    await page.locator('.span-view__scene').waitFor()
    await check(`${width}px 工程视图`)
    if(width===1440) await page.screenshot({path:`${out}engineering.png`,fullPage:true})
   }
   if(route==='history') {
    if(width===1440) await page.screenshot({path:`${out}history-list.png`,fullPage:true})
    await page.locator('.el-table__row').first().click()
    await page.locator('.el-drawer').waitFor()
    await page.waitForTimeout(400)
    const drawer=await page.locator('.el-drawer').boundingBox()
    if(drawer.x< -1 || drawer.x+drawer.width>width+1) throw Error('抽屉超出可视区域')
    await check(`${width}px 历史详情`)
    if(width===390) await page.screenshot({path:`${out}history-mobile.png`,fullPage:true})
   }
   if(route==='devices'&&width===390) await page.screenshot({path:`${out}devices-mobile.png`,fullPage:true})
  }
 }
 if(errors.length) throw Error(errors.join('\n'))
 writeFileSync(`${out}layout-verification.json`,JSON.stringify({checkedAt:new Date().toISOString(),results,errors},null,2))
 console.log(`${results.length} 项布局检查通过；浏览器异常 ${errors.length}`)
} catch(error) {
 await page.screenshot({path:`${out}layout-failure.png`,fullPage:true})
 console.log(await page.evaluate(()=>[...document.querySelectorAll('*')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>`${e.tagName}.${e.className}`).slice(0,30)))
 throw error
} finally {await browser.close()}
