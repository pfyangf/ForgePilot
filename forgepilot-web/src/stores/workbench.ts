import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiFetch } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { isTaskDraft, proposalInboxSummary, requirementIdsWithTaskDrafts, type PmoProposal } from '@/domain/pmo'
import type { WorkRequirement, WorkTask } from '@/types'

interface WorkbenchCounts {
  requirements: number
  tasks: number
  running: number
  verifying: number
  approval: number
  waitingUser: number
}

interface ApiRequirement {
  id: string
  code?: string
  title: string
  status: string
  projectId: string
  projectName: string
  createdBy: string
  ownerId: string
  draft: string
  confirmed: boolean
  taskDone: number
  taskTotal: number
}

interface ApiTask {
  id: string
  code?: string
  requirementId: string
  requirementCode?: string
  title: string
  description?: string
  taskType?: string
  defectId?: string | null
  status: string
  sessionStatus: string | null
  createdBy: string
  ownerId: string
  projectId: string
  projectName: string
  branchName: string
  agentLabel: string
  modelProviderId?: string
  modelId?: string
  agentProfileId?: string
  interactiveInput: boolean
  interrupted: boolean
}

interface WorkbenchSnapshot {
  scope: string
  requirements: ApiRequirement[]
  tasks: ApiTask[]
  counts: WorkbenchCounts
  proposals?: PmoProposal[]
}

interface RequirementDetailResponse {
  requirement: ApiRequirement & { code?: string; priority?: string }
  draft: string
  confirmed: boolean
  confirmedBy: string | null
  tasks: Array<{
    id: string
    code?: string
    requirementId: string
    requirementCode?: string
    title: string
    status: string
    taskType?: string
    projectId: string
    projectName: string
    branchName: string
    createdBy: string
    ownerId: string
    frozenRepoCount: number
  }>
}

interface TaskDetailResponse {
  task: {
    id: string
    code?: string
    requirementId: string
    requirementCode?: string
    title: string
    status: string
    taskType?: string
    defectId?: string | null
    projectId: string
    projectName: string
    branchName: string
    createdBy: string
    ownerId: string
    description?: string
    modelProviderId?: string
    modelId?: string
    agentProfileId?: string
    frozenRepoCount: number
  }
  frozenRepositories: Array<{
    id: string
    projectRepositoryId: string
    displayName: string | null
    localDirectoryName: string | null
    cloneUrl: string | null
    providerType?: string
    defaultBranch?: string
    baseBranch: string
    workingBranch?: string
    deliveryState: string
  }>
  mergeRequests?: Array<{
    projectRepositoryId: string
    localDirectoryName: string
    url: string
    externalId: string
    headSha: string
    branchName: string
    state: string
  }>
  testFileChanges?: string[]
  testFileChangeHook?: string
  unsupported: {
    startOrRetryBlocked: boolean
    startOrRetryReason: string
    requestChangesBlocked: boolean
    requestChangesReason: string
    retryBlocked: boolean
    retryReason: string
  }
}

function toRequirement(item: ApiRequirement): WorkRequirement {
  return {
    id: item.id,
    code: item.code,
    title: item.title,
    status: item.status,
    projectId: item.projectId,
    projectName: item.projectName,
    createdBy: item.createdBy,
    ownerId: item.ownerId,
    draft: item.draft || '',
    suggestions: [],
    confirmed: item.confirmed,
    taskDone: item.taskDone,
    taskTotal: item.taskTotal,
  }
}

function toTask(item: ApiTask): WorkTask {
  return {
    id: item.id,
    code: item.code,
    requirementId: item.requirementId,
    requirementCode: item.requirementCode,
    title: item.title,
    description: item.description || '',
    taskType: item.taskType,
    defectId: item.defectId,
    status: item.status,
    sessionStatus: item.sessionStatus,
    sessionId: null,
    createdBy: item.createdBy,
    ownerId: item.ownerId,
    projectId: item.projectId,
    projectName: item.projectName,
    branchName: item.branchName || '',
    providerName: '项目默认',
    modelProviderId: item.modelProviderId,
    modelId: item.modelId || '',
    agentProfileId: item.agentProfileId,
    agentLabel: item.agentLabel || 'Codex CLI',
    interactiveInput: item.interactiveInput,
    nativePause: false,
    interrupted: item.interrupted,
    checkouts: [],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: [],
    verificationCommand: '',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: '执行、验证、推送 MR 尚未接通。可做的人工动作会走真实接口。',
    frozenRepositories: [],
    unsupported: {
      startOrRetryBlocked: false,
      startOrRetryReason: '可通过 start 启动 Session',
      requestChangesBlocked: false,
      requestChangesReason: '可通过 request-changes 开新 Session',
      retryBlocked: false,
      retryReason: '可通过 retry 在同一工作区开新 Session',
    },
  }
}

export const useWorkbenchStore = defineStore('workbench', () => {
  const auth = useAuthStore()
  const requirements = ref<WorkRequirement[]>([])
  const tasks = ref<WorkTask[]>([])
  const proposals = ref<PmoProposal[]>([])
  const scope = ref<'mine' | 'member' | 'waiting'>('waiting')
  const loading = ref(false)
  const error = ref('')
  const actionError = ref('')
  const counts = ref<WorkbenchCounts>({
    requirements: 0,
    tasks: 0,
    running: 0,
    verifying: 0,
    approval: 0,
    waitingUser: 0,
  })
  const retryNote = ref('')
  const searchQuery = ref('')
  const selectedStat = ref<string>('')
  const waitingActionCount = ref(0)

  const visibleRequirements = computed(() => requirements.value)
  const visibleTasks = computed(() => tasks.value)

  function matchesQuery(parts: Array<string | undefined | null>) {
    const query = searchQuery.value.trim().toLowerCase()
    if (!query) return true
    return parts.some((part) => (part || '').toLowerCase().includes(query))
  }

  const inboxItems = computed(() => {
    const items: Array<{
      kind: 'requirement' | 'task' | 'proposal'
      id: string
      code: string
      title: string
      summary: string
      action: '去验收' | '去审核' | '去处理' | '确认创建'
      requirementId?: string
      proposalId?: string
      taskType?: string
    }> = []
    for (const requirement of requirements.value) {
      if (requirement.status === '待验收') {
        items.push({
          kind: 'requirement',
          id: requirement.id,
          code: requirement.code || '',
          title: requirement.title,
          summary: `${requirement.projectName} · ${requirement.status}`,
          action: '去验收',
        })
      }
    }
    for (const task of tasks.value) {
      if (task.status === 'WAITING_APPROVAL') {
        items.push({
          kind: 'task',
          id: task.id,
          code: task.code || '',
          title: task.title,
          summary: `${task.projectName}${task.requirementCode ? ' · 关联 ' + task.requirementCode : ''} · ${task.agentLabel || ''}`,
          action: '去审核',
        })
      } else if (task.status === 'WAITING_USER') {
        items.push({
          kind: 'task',
          id: task.id,
          code: task.code || '',
          title: task.title,
          summary: `${task.projectName}${task.requirementCode ? ' · 关联 ' + task.requirementCode : ''} · 等待处理`,
          action: '去处理',
        })
      }
    }
    const draftedRequirementIds = requirementIdsWithTaskDrafts(proposals.value)
    if (scope.value === 'waiting') {
      for (const requirement of requirements.value) {
        if (['待分析', '待需求评审', '设计中', '待设计评审', '待开发'].includes(String(requirement.status))) {
          if (requirement.status === '待开发' && draftedRequirementIds.has(requirement.id)) {
            continue
          }
          items.push({
            kind: 'requirement',
            id: requirement.id,
            code: requirement.code || '',
            title: requirement.title,
            summary: `${requirement.projectName} · ${requirement.status}`,
            action: '去处理',
          })
        }
      }
    }
    for (const proposal of proposals.value) {
      if (!isTaskDraft(proposal)) continue
      items.push({
        kind: 'proposal',
        id: proposal.proposalId,
        code: proposal.requirementCode || '',
        title: proposal.title,
        summary: proposalInboxSummary(proposal),
        action: '确认创建',
        requirementId: proposal.requirementId,
        proposalId: proposal.proposalId,
        taskType: proposal.taskType || undefined,
      })
    }
    return items
  })

  const filteredRequirements = computed(() => {
    const list = requirements.value.filter((item) =>
      matchesQuery([item.code, item.title, item.projectName, item.status]),
    )
    if (!selectedStat.value || selectedStat.value === 'requirements') return list
    if (selectedStat.value === 'tasks') return []
    const related = new Set(
      filteredTaskRows().map((item) => item.requirementId),
    )
    return list.filter((item) => related.has(item.id))
  })

  function filteredTaskRows() {
    const list = tasks.value.filter((item) =>
      matchesQuery([item.code, item.title, item.projectName, item.status, item.requirementCode, item.agentLabel]),
    )
    if (!selectedStat.value || selectedStat.value === 'tasks') return list
    if (selectedStat.value === 'requirements') return []
    if (selectedStat.value === 'running') return list.filter((item) => item.status === 'RUNNING')
    if (selectedStat.value === 'verifying') return list.filter((item) => item.status === 'VERIFYING')
    if (selectedStat.value === 'approval') return list.filter((item) => item.status === 'WAITING_APPROVAL')
    if (selectedStat.value === 'waitingUser') return list.filter((item) => item.status === 'WAITING_USER')
    return list
  }

  const filteredTasks = computed(() => filteredTaskRows())

  async function load(nextScope: 'mine' | 'member' | 'waiting' = scope.value) {
    scope.value = nextScope
    loading.value = true
    error.value = ''
    try {
      const snapshot = await apiFetch<WorkbenchSnapshot>(`/api/v1/workbench?scope=${nextScope}`)
      requirements.value = snapshot.requirements.map(toRequirement)
      tasks.value = snapshot.tasks.map(toTask)
      counts.value = snapshot.counts
      proposals.value = (snapshot.proposals || []).filter(isTaskDraft)
      if (nextScope === 'waiting') {
        const draftedRequirementIds = requirementIdsWithTaskDrafts(proposals.value)
        waitingActionCount.value =
          snapshot.requirements.filter((item) => {
            if (!['待分析', '待需求评审', '设计中', '待设计评审', '待开发', '待验收'].includes(item.status)) {
              return false
            }
            return !(item.status === '待开发' && draftedRequirementIds.has(item.id))
          }).length +
          snapshot.tasks.filter((item) => ['WAITING_APPROVAL', 'WAITING_USER'].includes(item.status)).length +
          proposals.value.length
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载工作台失败'
      requirements.value = []
      tasks.value = []
      proposals.value = []
    } finally {
      loading.value = false
    }
  }

  function requirementById(id: string) {
    return requirements.value.find((item) => item.id === id)
  }

  function taskById(id: string) {
    return tasks.value.find((item) => item.id === id)
  }

  function tasksFor(requirementId: string) {
    return tasks.value.filter((task) => task.requirementId === requirementId)
  }

  function progress(requirementId: string) {
    const requirement = requirementById(requirementId)
    if (requirement?.taskTotal != null) {
      return { done: requirement.taskDone ?? 0, total: requirement.taskTotal }
    }
    const related = tasksFor(requirementId).filter((task) => task.status !== 'CANCELLED')
    const done = related.filter((task) => task.status === 'COMPLETED').length
    return { done, total: related.length }
  }

  async function loadRequirementDetail(id: string) {
    actionError.value = ''
    const detail = await apiFetch<RequirementDetailResponse>(`/api/v1/requirements/${id}`)
    const mapped = toRequirement({
      ...detail.requirement,
      draft: detail.draft,
      confirmed: detail.confirmed,
    })
    mapped.suggestions = []
    const idx = requirements.value.findIndex((item) => item.id === id)
    if (idx >= 0) {
      requirements.value[idx] = mapped
    } else {
      requirements.value.push(mapped)
    }
    for (const task of detail.tasks) {
      const existing = tasks.value.find((item) => item.id === task.id)
      if (!existing) {
        tasks.value.push(
          toTask({
            id: task.id,
            code: task.code,
            requirementId: task.requirementId,
            requirementCode: task.requirementCode,
            title: task.title,
            taskType: task.taskType,
            status: task.status,
            sessionStatus: null,
            createdBy: task.createdBy,
            ownerId: task.ownerId,
            projectId: task.projectId,
            projectName: task.projectName,
            branchName: task.branchName,
            agentLabel: 'Codex CLI',
            interactiveInput: false,
            interrupted: false,
          }),
        )
      }
    }
    return mapped
  }

  async function loadTaskDetail(id: string) {
    actionError.value = ''
    const detail = await apiFetch<TaskDetailResponse>(`/api/v1/tasks/${id}`)
    const mapped = toTask({
      id: detail.task.id,
      code: detail.task.code,
      requirementId: detail.task.requirementId,
      requirementCode: detail.task.requirementCode,
      title: detail.task.title,
      description: detail.task.description,
      taskType: detail.task.taskType,
      defectId: detail.task.defectId,
      status: detail.task.status,
      sessionStatus: null,
      createdBy: detail.task.createdBy,
      ownerId: detail.task.ownerId,
      projectId: detail.task.projectId,
      projectName: detail.task.projectName,
      branchName: detail.task.branchName,
      agentLabel: 'Codex CLI',
      modelProviderId: detail.task.modelProviderId,
      modelId: detail.task.modelId,
      agentProfileId: detail.task.agentProfileId,
      interactiveInput: false,
      interrupted: false,
    })
    mapped.frozenRepositories = detail.frozenRepositories.map((repo) => ({
      projectRepositoryId: repo.projectRepositoryId,
      directoryName: repo.localDirectoryName || repo.displayName || repo.projectRepositoryId,
      displayName: repo.displayName || repo.localDirectoryName || '',
      providerType: repo.providerType,
      defaultBranch: repo.defaultBranch,
      baseBranch: repo.baseBranch,
      workingBranch: repo.workingBranch,
      deliveryState: repo.deliveryState,
      cloneUrl: repo.cloneUrl || '',
    }))
    mapped.checkouts = mapped.frozenRepositories.map((repo) => ({
      directoryName: repo.directoryName,
      changed: repo.deliveryState !== 'NONE' && repo.deliveryState !== 'FAILED',
    }))
    mapped.mergeRequests = (detail.mergeRequests || []).map((mr) => ({
      directoryName: mr.localDirectoryName,
      url: mr.url,
      headSha: mr.headSha,
    }))
    mapped.testFileChanges = detail.testFileChanges || []
    mapped.unsupported = detail.unsupported
    mapped.evidenceNote =
      detail.task.status === 'WAITING_APPROVAL'
        ? '审核通过只改任务状态，不会自动合并。Draft 合并请求由平台 verify/retry-delivery 创建。' +
          (detail.testFileChangeHook ? ` 测试文件变更钩子：${detail.testFileChangeHook}` : '')
        : '平台验证/交付与 Session 启动已接通；无 Codex 时 start 返回 NOT_AVAILABLE，不会假装编码完成。'
    try {
      const session = await apiFetch<{
        id: string
        status: string
        logs: string[]
        errorSummary?: string
      }>(`/api/v1/tasks/${id}/session`)
      if (session) {
        mapped.sessionId = session.id
        mapped.sessionStatus = session.status
        mapped.logs = session.logs || mapped.logs
        if (session.errorSummary) {
          mapped.logs = [session.errorSummary, ...mapped.logs]
        }
        try {
          const stream = await apiFetch<{ chunks: Array<{ seq: number; body: string }> }>(
            `/api/v1/tasks/${id}/sessions/${session.id}/logs?afterSeq=0&limit=200`,
          )
          const extra = (stream.chunks || []).map((c) => c.body)
          if (extra.length) {
            mapped.logs = [...extra, ...mapped.logs]
          }
        } catch {
          // stream optional
        }
      }
    } catch {
      // session endpoint optional when none exists
    }
    const idx = tasks.value.findIndex((item) => item.id === id)
    if (idx >= 0) {
      tasks.value[idx] = mapped
    } else {
      tasks.value.push(mapped)
    }
    return mapped
  }

  async function confirmRequirement(id: string) {
    const requirement = requirementById(id)
    if (!requirement || requirement.confirmed) {
      return
    }
    actionError.value = ''
    try {
      const titles = requirement.suggestions.map((item) => item.title)
      await apiFetch(`/api/v1/requirements/${id}/confirm-analysis`, {
        method: 'POST',
        body: JSON.stringify({ draft: requirement.draft, taskTitles: titles }),
      })
      await loadRequirementDetail(id)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '确认失败'
      throw err
    }
  }

  function addSuggestion(id: string, title: string) {
    const requirement = requirementById(id)
    if (!requirement || requirement.confirmed || title.trim() === '') {
      return
    }
    requirement.suggestions.push({ id: `sug-${Date.now()}`, title: title.trim() })
  }

  function removeSuggestion(requirementId: string, suggestionId: string) {
    const requirement = requirementById(requirementId)
    if (!requirement || requirement.confirmed) {
      return
    }
    requirement.suggestions = requirement.suggestions.filter((item) => item.id !== suggestionId)
  }

  async function createTask(
    requirementId: string,
    title: string,
    taskType = 'IMPLEMENT',
    defectId?: string,
  ) {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/requirements/${requirementId}/tasks`, {
        method: 'POST',
        body: JSON.stringify({
          title,
          taskType,
          priority: 'MEDIUM',
          defectId,
        }),
      })
      await loadRequirementDetail(requirementId)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '创建任务失败'
      throw err
    }
  }

  async function cancelTask(id: string, note = '') {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/tasks/${id}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      })
      await loadTaskDetail(id)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '取消失败'
      throw err
    }
  }

  async function abandonTask(id: string, note = '') {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/tasks/${id}/abandon`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      })
      await loadTaskDetail(id)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '放弃失败'
      throw err
    }
  }

  async function approveTask(id: string, note = '') {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/tasks/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      })
      await loadTaskDetail(id)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '审核失败'
      throw err
    }
  }

  async function sendMessage(id: string, message: string) {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/tasks/${id}/input`, {
        method: 'POST',
        body: JSON.stringify({ note: message }),
      })
      await loadTaskDetail(id)
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '发送失败（Codex 可能不支持运行中输入）'
      throw err
    }
  }

  async function retryTask(id: string, note: string) {
    actionError.value = ''
    try {
      const result = await apiFetch<{ taskStatus: string; sessionStatus: string | null; message: string }>(
        `/api/v1/tasks/${id}/retry`,
        { method: 'POST', body: JSON.stringify({ note }) },
      )
      await loadTaskDetail(id)
      await load(scope.value)
      if (result.taskStatus !== 'RUNNING') {
        actionError.value = result.message || 'Session 未进入 RUNNING（可能 NOT_AVAILABLE）'
      }
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '重试失败'
      throw err
    }
  }

  async function requestChanges(id: string, note: string) {
    actionError.value = ''
    try {
      const result = await apiFetch<{ taskStatus: string; message: string }>(
        `/api/v1/tasks/${id}/request-changes`,
        { method: 'POST', body: JSON.stringify({ note }) },
      )
      await loadTaskDetail(id)
      await load(scope.value)
      if (result.taskStatus !== 'RUNNING') {
        actionError.value = result.message || '要求修改未启动成功'
      }
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '要求修改失败'
      throw err
    }
  }

  async function startTask(id: string, note?: string) {
    actionError.value = ''
    try {
      const result = await apiFetch<{ taskStatus: string; sessionStatus: string | null; message: string; logs: string[] }>(
        `/api/v1/tasks/${id}/start`,
        { method: 'POST', body: JSON.stringify({ note: note || '' }) },
      )
      const mapped = await loadTaskDetail(id)
      mapped.logs = [...(result.logs || []), ...(mapped.logs || [])]
      mapped.sessionStatus = result.sessionStatus
      if (result.taskStatus !== 'RUNNING') {
        actionError.value = result.message || '启动未成功（不会假装 Codex 已完成）'
      }
      await load(scope.value)
      return result
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '启动失败'
      throw err
    }
  }

  async function verifyTask(id: string) {
    actionError.value = ''
    try {
      const result = await apiFetch<{
        status: string
        verificationPassed: boolean
        mergeRequests: Array<{ localDirectoryName: string; url: string; headSha: string }>
        testFileChanges: string[]
        message: string
      }>(`/api/v1/tasks/${id}/verify`, { method: 'POST' })
      const mapped = await loadTaskDetail(id)
      mapped.mergeRequests = (result.mergeRequests || []).map((mr) => ({
        directoryName: mr.localDirectoryName,
        url: mr.url,
        headSha: mr.headSha,
      }))
      mapped.testFileChanges = result.testFileChanges || []
      mapped.logs = [result.message, ...(mapped.logs || [])]
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '验证失败'
      throw err
    }
  }

  async function retryDelivery(id: string) {
    actionError.value = ''
    try {
      const result = await apiFetch<{
        status: string
        mergeRequests: Array<{ localDirectoryName: string; url: string; headSha: string }>
        testFileChanges: string[]
        message: string
      }>(`/api/v1/tasks/${id}/retry-delivery`, { method: 'POST' })
      const mapped = await loadTaskDetail(id)
      mapped.mergeRequests = (result.mergeRequests || []).map((mr) => ({
        directoryName: mr.localDirectoryName,
        url: mr.url,
        headSha: mr.headSha,
      }))
      mapped.testFileChanges = result.testFileChanges || []
      mapped.logs = [result.message, ...(mapped.logs || [])]
      await load(scope.value)
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '重试交付失败'
      throw err
    }
  }

  async function confirmProposal(proposal: Pick<PmoProposal, 'proposalId' | 'requirementId' | 'title'>, title?: string) {
    actionError.value = ''
    const result = await apiFetch<{ task?: { id: string; title: string; status: string } }>(
      `/api/v1/requirements/${proposal.requirementId}/proposals/${proposal.proposalId}/confirm`,
      {
        method: 'POST',
        body: JSON.stringify({ title: (title ?? proposal.title).trim() }),
      },
    )
    await load(scope.value)
    return result
  }

  async function returnProposal(proposal: Pick<PmoProposal, 'proposalId' | 'requirementId'>, comment?: string) {
    actionError.value = ''
    await apiFetch(`/api/v1/requirements/${proposal.requirementId}/proposals/${proposal.proposalId}/return`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment || undefined }),
    })
    await load(scope.value)
  }

  return {
    scope,
    loading,
    error,
    actionError,
    retryNote,
    visibleRequirements,
    visibleTasks,
    filteredRequirements,
    filteredTasks,
    inboxItems,
    proposals,
    searchQuery,
    selectedStat,
    waitingActionCount,
    counts,
    currentUserId: computed(() => auth.user?.userId ?? ''),
    load,
    loadRequirementDetail,
    loadTaskDetail,
    requirementById,
    taskById,
    tasksFor,
    progress,
    confirmRequirement,
    addSuggestion,
    removeSuggestion,
    createTask,
    sendMessage,
    cancelTask,
    retryTask,
    abandonTask,
    approveTask,
    requestChanges,
    startTask,
    verifyTask,
    retryDelivery,
    confirmProposal,
    returnProposal,
  }
})
