import { describe, expect, it } from 'vitest'
import model from './lineguard-parts.json'

// 演示三维场景直接渲染这份构件表，所以这里校验的是"导出契约"：
// 字段齐不齐、锥台朝向对不对。锥台方向搞反时 V 槽会变成朝外张开的凸棱，
// 图上看着还在，但机构已经不成立——这种错误只能靠断言拦住。
type Part = {
  kind: string
  name: string
  material: string | null
  pos: number[]
  size?: number[]
  quat?: number[]
  axis?: string
  radius?: number
  tube?: number
  rPos?: number
  rNeg?: number
  depth?: number
}

const robot = model.robot as Part[]
const dock = model.dock as Part[]
const materials = model.materials as Record<
  string, { color: number[]; metalness: number; roughness: number }>
const AXES = ['x', 'y', 'z']

const near = (left: number, right: number, tolerance = 1e-6) =>
  Math.abs(left - right) <= tolerance

function checkPartShape(parts: Part[]) {
  for (const part of parts) {
    expect(['box', 'cylinder', 'cone', 'torus', 'sphere']).toContain(part.kind)
    expect(part.pos).toHaveLength(3)
    expect(part.material).toBeTruthy()
    expect(Object.keys(materials)).toContain(part.material as string)

    if (part.kind === 'box') {
      expect(part.size).toHaveLength(3)
      for (const edge of part.size as number[]) expect(edge).toBeGreaterThan(0)
      expect(part.quat).toHaveLength(4)
      const [x, y, z, w] = part.quat as number[]
      expect(Math.hypot(x, y, z, w)).toBeCloseTo(1, 6)
      continue
    }

    if (part.kind === 'sphere') {
      expect(part.radius as number).toBeGreaterThan(0)
      continue
    }

    expect(AXES).toContain(part.axis)
    if (part.kind === 'torus') {
      expect(part.radius as number).toBeGreaterThan(part.tube as number)
      expect(part.tube as number).toBeGreaterThan(0)
      continue
    }

    expect(part.depth as number).toBeGreaterThan(0)
    expect(part.rPos as number).toBeGreaterThan(0)
    expect(part.rNeg as number).toBeGreaterThan(0)
    if (part.kind === 'cylinder') {
      expect(part.rPos).toBe(part.rNeg)
    } else {
      expect(part.rPos).not.toBe(part.rNeg)
    }
  }
}

describe('lineguard-parts.json 导出契约', () => {
  it('带有生成来源与源码指纹', () => {
    expect(model._generated_by).toContain('export_parts.py')
    expect(model._source_hash).toMatch(/^[0-9a-f]{16}$/)
    expect(model.wireRadius).toBeGreaterThan(0)
  })

  it('Robot 与 Dock 的构件字段都与其类型匹配', () => {
    expect(robot.length).toBeGreaterThan(40)
    expect(dock.length).toBeGreaterThan(10)
    checkPartShape(robot)
    checkPartShape(dock)
  })

  it('轮槽开口朝外：离地线远的一端必须是大端', () => {
    const flanges = robot.filter(part => part.kind === 'cone' && part.name.includes('Flange'))
    // 两个夹持站点、每站上下各一对法兰
    expect(flanges).toHaveLength(8)
    for (const flange of flanges) {
      const offset = flange.pos[2]
      expect(flange.axis).toBe('z')
      expect(Math.abs(offset)).toBeGreaterThan(0)
      // z > 0 的一侧，+z 端离地线更远，应当是大端；z < 0 的一侧相反
      if (offset > 0) {
        expect(flange.rPos as number).toBeGreaterThan(flange.rNeg as number)
      } else {
        expect(flange.rPos as number).toBeLessThan(flange.rNeg as number)
      }
    }
  })

  it('上下夹持轮关于地线对称，轮槽底半径等于线半径加余量', () => {
    const bottoms = robot.filter(part => part.kind === 'cylinder' && part.name.endsWith('_Bottom'))
    // 两个站点 × 上下两轮
    expect(bottoms).toHaveLength(4)
    for (const roller of bottoms) {
      expect(roller.axis).toBe('z')
      // 轮槽底正好压在地线表面上：轴心距 = 线半径 + 槽底半径
      const expected = model.wireRadius + (roller.rPos as number)
      expect(near(Math.abs(roller.pos[1]), expected, 1e-6)).toBe(true)
    }

    const upper = bottoms.filter(roller => roller.pos[1] > 0).map(roller => roller.pos[1])
    const lower = bottoms.filter(roller => roller.pos[1] < 0).map(roller => roller.pos[1])
    expect(upper).toHaveLength(lower.length)
    upper.sort((a, b) => a - b)
    lower.sort((a, b) => b - a)
    upper.forEach((value, index) => expect(near(value, -lower[index])).toBe(true))
  })

  it('Dock 的导向轨关于地线对称，且与 Robot 的机体宽度不打架', () => {
    const rails = dock.filter(part => part.name.startsWith('DockRail'))
    expect(rails).toHaveLength(2)
    const outer = rails.map(rail => rail.pos[2])
    expect(near(outer[0], -outer[1])).toBe(true)

    // 导轨内侧面必须让开机体的最大半宽，否则演示里会穿模
    const railHalf = Math.max(...rails.map(rail => (rail.size as number[])[2])) / 2
    const railInner = Math.abs(outer[0]) - railHalf
    const bodyHalf = Math.max(...robot
      .filter(part => part.name === 'Chassis')
      .map(part => (part.size as number[])[2])) / 2
    expect(railInner).toBeGreaterThan(bodyHalf)
  })

  it('材质数值都在可用区间内', () => {
    for (const spec of Object.values(materials)) {
      expect(spec.color).toHaveLength(3)
      for (const channel of spec.color) {
        expect(channel).toBeGreaterThanOrEqual(0)
        expect(channel).toBeLessThanOrEqual(1)
      }
      expect(spec.metalness).toBeGreaterThanOrEqual(0)
      expect(spec.metalness).toBeLessThanOrEqual(1)
      expect(spec.roughness).toBeGreaterThanOrEqual(0)
      expect(spec.roughness).toBeLessThanOrEqual(1)
    }
  })
})
