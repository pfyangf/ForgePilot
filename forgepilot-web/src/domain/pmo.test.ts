import { describe, expect, it } from 'vitest'
import {
  draftTitlesFrom,
  isTaskDraft,
  proposalInboxSummary,
  requirementIdsWithTaskDrafts,
  taskTypeLabel,
  type PmoProposal,
} from './pmo'

function proposal(overrides: Partial<PmoProposal> = {}): PmoProposal {
  return {
    proposalId: 'p1',
    kind: 'CREATE_TASK',
    taskType: 'IMPLEMENT',
    title: '实现：登录页改造',
    hint: '确认后只会创建任务',
    primaryActionLabel: '确认创建',
    confirmCreatesTask: true,
    requirementId: 'r1',
    requirementCode: 'REQ-1',
    requirementTitle: '登录页改造',
    projectId: 'p1',
    projectName: '支付',
    ...overrides,
  }
}

describe('pmo proposal helpers', () => {
  it('recognizes confirmable task drafts only', () => {
    expect(isTaskDraft(proposal())).toBe(true)
    expect(isTaskDraft(proposal({ kind: 'BIND_PROJECT', confirmCreatesTask: false, title: '' }))).toBe(false)
    expect(isTaskDraft(proposal({ kind: 'ACCEPT', confirmCreatesTask: false }))).toBe(false)
  })

  it('labels task types in Chinese and writes inbox copy', () => {
    expect(taskTypeLabel('IMPLEMENT')).toBe('实现')
    expect(taskTypeLabel('TEST')).toBe('测试')
    expect(taskTypeLabel('BUGFIX')).toBe('缺陷修复')
    expect(proposalInboxSummary(proposal())).toContain('平台建议')
    expect(proposalInboxSummary(proposal())).toContain('不会自动开工')
    expect(proposalInboxSummary(proposal())).toContain('关联 REQ-1')
  })

  it('collects editable titles and requirement ids for drafts', () => {
    const proposals = [
      proposal(),
      proposal({
        proposalId: 'p2',
        taskType: 'BUGFIX',
        title: '修复：DEF-1',
        requirementId: 'r2',
      }),
      proposal({ proposalId: 'p3', kind: 'BIND_PROJECT', confirmCreatesTask: false, title: '' }),
    ]
    expect(draftTitlesFrom(proposals)).toEqual({
      p1: '实现：登录页改造',
      p2: '修复：DEF-1',
    })
    expect(Array.from(requirementIdsWithTaskDrafts(proposals))).toEqual(['r1', 'r2'])
  })
})
