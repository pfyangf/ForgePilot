export type PmoKind =
  | 'CREATE_TASK'
  | 'BIND_PROJECT'
  | 'REVIEW_CODE'
  | 'VERIFY_REGRESSION'
  | 'CONFIRM_ANALYSIS'
  | 'REVIEW_REQUIREMENT'
  | 'SUBMIT_DESIGN'
  | 'REVIEW_DESIGN'
  | 'ACCEPT'
  | 'RELEASE'
  | 'VIEW_RELEASE'

export interface PmoProposal {
  proposalId: string
  kind: PmoKind | string
  taskType?: string | null
  defectId?: string | null
  targetTaskId?: string | null
  title: string
  hint: string
  primaryActionLabel: string
  confirmCreatesTask: boolean
  requirementId: string
  requirementCode?: string | null
  requirementTitle?: string | null
  projectId: string
  projectName: string
}

export function isTaskDraft(proposal: PmoProposal): boolean {
  return proposal.kind === 'CREATE_TASK' && proposal.confirmCreatesTask
}

export function taskTypeLabel(taskType?: string | null): string {
  switch (taskType) {
    case 'IMPLEMENT':
      return '实现'
    case 'TEST':
      return '测试'
    case 'BUGFIX':
      return '缺陷修复'
    case 'REFACTOR':
      return '重构'
    case 'DOCS':
      return '文档'
    case 'REVIEW':
      return '评审'
    default:
      return taskType || '任务'
  }
}

export function proposalInboxSummary(proposal: PmoProposal): string {
  const project = proposal.projectName || '项目'
  const code = proposal.requirementCode ? ` · 关联 ${proposal.requirementCode}` : ''
  return `${project}${code} · 平台建议，确认后只会创建任务，不会自动开工`
}

export function draftTitlesFrom(proposals: PmoProposal[]): Record<string, string> {
  const titles: Record<string, string> = {}
  for (const proposal of proposals) {
    if (isTaskDraft(proposal)) {
      titles[proposal.proposalId] = proposal.title
    }
  }
  return titles
}

export function requirementIdsWithTaskDrafts(proposals: PmoProposal[]): Set<string> {
  return new Set(proposals.filter(isTaskDraft).map((item) => item.requirementId))
}
