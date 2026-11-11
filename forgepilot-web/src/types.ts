export type RequirementStatus = '待分析' | '待需求评审' | '设计中' | '待设计评审' | '待开发' | '开发中' | '测试中' | '待验收' | '已完成' | '已取消' | '已发布'

export type TaskStatus =
  | 'PENDING'
  | 'PREPARING'
  | 'RUNNING'
  | 'WAITING_USER'
  | 'VERIFYING'
  | 'WAITING_APPROVAL'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export type SessionStatus = 'RUNNING' | 'WAITING_INPUT' | 'INTERRUPTED' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | string

export interface SuggestedTask {
  id: string
  title: string
}

export interface WorkRequirement {
  id: string
  code?: string
  title: string
  status: RequirementStatus | string
  projectId?: string
  projectName: string
  createdBy: string
  ownerId: string
  draft: string
  suggestions: SuggestedTask[]
  confirmed: boolean
  taskDone?: number
  taskTotal?: number
  suggestedTaskTitles?: string[]
  taskType?: string
  defectId?: string
}

export interface CheckoutInfo {
  directoryName: string
  changed: boolean
}

export interface DraftMr {
  directoryName: string
  url: string
  headSha: string
  externalId?: string
  branchName?: string
  state?: string
}

export interface WorkTask {
  id: string
  code?: string
  requirementId: string
  requirementCode?: string
  title: string
  description?: string
  taskType?: string
  defectId?: string | null
  status: TaskStatus | string
  sessionStatus: SessionStatus | null
  sessionId?: string | null
  createdBy: string
  ownerId: string
  projectId?: string
  projectName: string
  branchName: string
  providerName: string
  modelProviderId?: string
  modelId: string
  agentProfileId?: string
  agentLabel: string
  interactiveInput: boolean
  nativePause: boolean
  interrupted: boolean
  checkouts: CheckoutInfo[]
  mergeRequests: DraftMr[]
  unchangedRepos: string[]
  testFileChanges: string[]
  logs: string[]
  verificationCommand: string
  verificationLog: string
  verificationExitCode: number | null
  evidenceNote: string
  frozenRepositories?: Array<{
    projectRepositoryId?: string
    directoryName: string
    displayName?: string
    providerType?: string
    defaultBranch?: string
    baseBranch: string
    workingBranch?: string
    deliveryState: string
    cloneUrl: string
  }>
  unsupported?: {
    startOrRetryBlocked: boolean
    startOrRetryReason: string
    requestChangesBlocked: boolean
    requestChangesReason: string
    retryBlocked: boolean
    retryReason: string
  }
}
