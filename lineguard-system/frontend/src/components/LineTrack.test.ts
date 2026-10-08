// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LineTrack from './LineTrack.vue'
import type { InspectionPoint } from '../types'

// 二维视图最容易出的问题是"错位"：机器人、检测点、地线各算一套坐标，
// 看起来都在线上，实际已经开始漂。这个用例只认一条：所有元素的坐标
// 必须由同一条悬链线给出。做法是把渲染出来的地线路径解析出来，
// 用它反算机器人位置处的曲线高度，再和渲染出来的机器人坐标比。
//
// 这样即使有人只改了地线的控制点、忘了改坐标函数，用例也会红。

const LENGTH = 1000
const POINTS: InspectionPoint[] = [
  { id: 'LG-POINT-350', name: '地线附件检测点 A', positionMeters: 350, defaultInspectionItem: '连接金具外观检查' },
  { id: 'LG-POINT-720', name: '重点复核点 B', positionMeters: 720, defaultInspectionItem: '地线表面与异物检查' },
]

function mountTrack(robotPosition: number, targetPointId?: string) {
  return mount(LineTrack, {
    props: { lengthMeters: LENGTH, points: POINTS, robotPosition, robotOnline: true, targetPointId },
  })
}

/** 从 "translate(x y)" 里取出两个数，避免依赖浮点数被格式化成什么样。 */
function translateOf(transform: string | undefined) {
  const matched = /translate\(([-\d.eE]+)[\s,]+([-\d.eE]+)\)/.exec(transform ?? '')
  if (!matched) throw new Error(`无法解析 transform: ${transform}`)
  return { x: Number(matched[1]), y: Number(matched[2]) }
}

/** 地线与已行进里程都是 "M x0 y0 Q cx cy x1 y1"，只能按终点区分：
 *  地线一定画到右侧杆塔，进度路径画到机器人当前里程。 */
const BEZIER = /^M ([-\d.eE]+) ([-\d.eE]+) Q ([-\d.eE]+) ([-\d.eE]+) ([-\d.eE]+) ([-\d.eE]+)$/

function bezierPaths(track: ReturnType<typeof mountTrack>) {
  return track.findAll('path')
    .map(node => node.attributes('d') ?? '')
    .filter(value => BEZIER.test(value))
    .map(value => {
      const [x0, y0, cx, cy, x1, y1] = value.split(/[MQ ]+/).filter(Boolean).map(Number)
      return { d: value, x0, y0, cx, cy, x1, y1 }
    })
}

/** 解析地线路径，并按二次贝塞尔求任意 t 处的高度。 */
function wireCurve(track: ReturnType<typeof mountTrack>) {
  const paths = bezierPaths(track)
  const wire = paths.reduce((widest, current) => (current.x1 > widest.x1 ? current : widest), paths[0])
  if (!wire || wire.x1 - wire.x0 < 100) throw new Error('没有找到横跨整档的地线路径')
  return {
    ...wire,
    yAtX(x: number) {
      const t = (x - wire.x0) / (wire.x1 - wire.x0)
      const rest = 1 - t
      return rest * rest * wire.y0 + 2 * rest * t * wire.cy + t * t * wire.y1
    },
  }
}

/** 已行进里程与地线同起点、同曲线，只是终点截在机器人当前里程；
 *  三相导线起点不同，按这个差别区分，不依赖渲染样式。 */
function progressPath(track: ReturnType<typeof mountTrack>) {
  const wire = wireCurve(track)
  const progress = bezierPaths(track)
    .find(path => path.d !== wire.d && path.x0 === wire.x0 && path.y0 === wire.y0)
  if (!progress) throw new Error('没有找到已行进里程路径')
  return progress
}

function robotGroup(track: ReturnType<typeof mountTrack>) {
  const group = track.findAll('g').find(node => node.text().includes('Robot'))
  if (!group) throw new Error('没有找到机器人分组')
  return group
}

describe('LineTrack 二维工程视图', () => {
  it('检测点落在渲染出来的地线上', () => {
    const track = mountTrack(0)
    const curve = wireCurve(track)
    for (const point of POINTS) {
      const group = track.findAll('g').find(node => node.text().includes(`${point.positionMeters} m`))
      expect(group, `未渲染 ${point.positionMeters} m 的标记`).toBeTruthy()
      const { x, y } = translateOf(group!.attributes('transform'))
      expect(y).toBeCloseTo(curve.yAtX(x), 6)
    }
  })

  it('机器人跟着同一条曲线走，不会飘出线外', () => {
    const track = mountTrack(350, 'LG-POINT-350')
    const curve = wireCurve(track)
    const { x, y } = translateOf(robotGroup(track).attributes('transform'))
    expect(x).toBeGreaterThan(curve.x0)
    expect(x).toBeLessThan(curve.x1)
    // 机体中心必须正好压在曲线上——这正是"错位"会破坏的那条不变量
    expect(y).toBeCloseTo(curve.yAtX(x), 6)
  })

  it('里程越界时夹到杆塔位置，不会跑出线路', () => {
    const curve = wireCurve(mountTrack(0))
    const beyond = translateOf(robotGroup(mountTrack(LENGTH * 1.5)).attributes('transform'))
    const negative = translateOf(robotGroup(mountTrack(-50)).attributes('transform'))
    expect(beyond.x).toBeCloseTo(curve.x1, 6)
    expect(negative.x).toBeCloseTo(curve.x0, 6)
  })

  it('已行进里程画到机器人所在处', () => {
    const track = mountTrack(350)
    const robot = translateOf(robotGroup(track).attributes('transform'))
    const progress = progressPath(track)
    expect(progress.x1).toBeCloseTo(robot.x, 6)
    expect(progress.y1).toBeCloseTo(robot.y, 6)
  })

  it('标记目标检测点：只有一个目标，其余为普通点', () => {
    const track = mountTrack(350, 'LG-POINT-350')
    const chips = track.findAll('text.scene-chip')
    const target = chips.filter(node => node.classes().includes('scene-chip--target'))
    expect(target).toHaveLength(1)
    expect(target[0].text()).toBe('350 m')
  })
})
