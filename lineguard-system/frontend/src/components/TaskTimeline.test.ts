// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskTimeline from './TaskTimeline.vue'
import type { TaskEvent, TaskStatus } from '../types'

// 后端的事件类型是内部命名（全大写下划线）。这些字样不该出现在界面标题里——
// 中文界面混着 TASK_CREATED、TELEMETRY 这类字样，读起来像没做完。
// 这个用例盯住两件事：已知类型必须被翻译成中文；状态迁移必须显示状态名。

function event(eventType: string, toStatus: TaskStatus, message: string): TaskEvent {
  return { id: 1, fromStatus: null, toStatus, eventType, message, createdAt: '2026-09-18T00:30:32Z' }
}

const titlesOf = (events: TaskEvent[]) =>
  mount(TaskTimeline, { props: { events } }).findAll('.timeline-content strong').map(node => node.text())

describe('任务时间线标题', () => {
  it('状态迁移显示状态名，不显示内部事件类型', () => {
    const titles = titlesOf([
      event('TASK_CREATED', 'CREATED', '任务已创建'),
      event('STATUS_CHANGED', 'MOVING', 'Robot 已离站，开始沿线移动'),
    ])
    expect(titles).toEqual(['待派发', '前往检测点'])
  })

  it('后端会发出的每种事件类型都有中文名', () => {
    const known = [
      'TELEMETRY', 'DOCK_RETRY', 'FAULT_INJECTED', 'FAULT_CLEARED',
      'SAFE_PARK', 'DUPLICATE_COMMAND', 'STARTUP_RECOVERY',
    ]
    const titles = titlesOf(known.map(type => event(type, 'MOVING', '说明')))
    for (const [index, title] of titles.entries()) {
      expect(title, `${known[index]} 未翻译`).not.toBe(known[index])
      expect(title).toMatch(/[\u4e00-\u9fff]/)
    }
  })

  it('未收录的类型退回原样显示，不会变成空白', () => {
    expect(titlesOf([event('SOME_FUTURE_TYPE', 'MOVING', '说明')])).toEqual(['SOME_FUTURE_TYPE'])
  })
})
