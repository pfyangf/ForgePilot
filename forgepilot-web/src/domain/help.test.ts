import { describe, expect, it } from 'vitest'
import { HELP } from './help'

describe('help copy', () => {
  it('explains that confirming a PMO draft only creates a task', () => {
    expect(HELP.pmoConfirm).toContain('只会创建待处理任务')
    expect(HELP.pmoConfirm).toContain('不会自动开工')
    expect(HELP.pmoConfirm).toContain('不会自动通过评审、验收或发布')
  })

  it('covers binding, release, review, inbox and theme in concise Chinese', () => {
    expect(HELP.repoBranch).toContain('源分支')
    expect(HELP.repoBranch).toContain('工作分支')
    expect(HELP.releaseTarget).toContain('不会自动合并')
    expect(HELP.toolModelBind).toContain('IMPLEMENT / BUGFIX')
    expect(HELP.reviewDecision).toContain('以本轮结论为准')
    expect(HELP.inbox).toContain('当前需要你确认')
    expect(HELP.themeSwitch).toContain('默认是深色主题')
    expect(HELP.stageActions).toContain('只按当前环节')
    for (const text of Object.values(HELP)) {
      expect(text).not.toMatch(/[あ-んア-ン]/)
    }
  })
})
