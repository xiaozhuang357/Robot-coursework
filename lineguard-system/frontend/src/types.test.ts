import { describe, expect, it } from 'vitest'
import { commandId, reviewLabels, taskStatusLabels, taskStatusTone } from './types'

describe('LineGuard 状态展示映射', () => {
  it('覆盖核心闭环和人工复核结论', () => {
    expect(taskStatusLabels.AWAITING_REVIEW).toBe('等待复核')
    expect(taskStatusLabels.COMPLETED).toBe('任务完成')
    expect(reviewLabels.DISPATCH_PERSONNEL).toBe('派遣现场人员')
  })

  it('异常状态使用危险色调', () => {
    expect(taskStatusTone('SAFE_PARKED')).toBe('danger')
    expect(taskStatusTone('COMPLETED')).toBe('success')
  })

  it('每条设备命令都带有可追踪前缀', () => {
    expect(commandId('dispatch')).toMatch(/^dispatch-/)
  })
})
