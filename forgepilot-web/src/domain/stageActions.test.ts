import { describe, expect, it } from 'vitest'
import { stageActionAudience, visibleNodeActions } from './stageActions'

describe('contextual stage actions', () => {
  it('does not dump every stage action at once', () => {
    const panel = visibleNodeActions({ currentStage: '需求分析', status: '待分析' })
    expect(panel.audience).toBe('current')
    expect(panel.nodes).toHaveLength(1)
    expect(panel.nodes[0].stage).toBe('需求分析')
    expect(panel.nodes[0].actions.some((item) => item.label.includes('分析'))).toBe(true)
    expect(panel.nodes.flatMap((item) => item.actions).some((item) => item.label.includes('发布'))).toBe(false)
  })

  it('hides future-stage buttons and keeps look-back actions modest', () => {
    expect(stageActionAudience('发布', '需求分析', '待分析')).toBe('upcoming')
    expect(stageActionAudience('提出需求', '需求分析', '待分析')).toBe('lookback')
    const future = visibleNodeActions({
      currentStage: '需求分析',
      status: '待分析',
      focusedStage: '发布',
    })
    expect(future.audience).toBe('upcoming')
    expect(future.nodes).toEqual([])
    expect(future.hint).toContain('不展示这里的按钮')

    const past = visibleNodeActions({
      currentStage: '验收测试',
      status: '待验收',
      focusedStage: '需求分析',
    })
    expect(past.audience).toBe('lookback')
    expect(past.nodes[0].actions.every((item) => item.type === 'focus')).toBe(true)
    expect(past.nodes[0].actions.some((item) => item.type === 'ai')).toBe(false)
  })

  it('shows current acceptance or release actions only for that environment', () => {
    const accept = visibleNodeActions({ currentStage: '验收测试', status: '待验收' })
    expect(accept.nodes[0].actions.some((item) => item.scene === 'ACCEPTANCE')).toBe(true)
    const release = visibleNodeActions({ currentStage: '发布', status: '已完成' })
    expect(release.nodes[0].actions.some((item) => item.scene === 'RELEASE_NOTES')).toBe(true)
    const done = visibleNodeActions({ currentStage: '发布', status: '已发布', focusedStage: '发布' })
    expect(done.audience).toBe('lookback')
  })
})
