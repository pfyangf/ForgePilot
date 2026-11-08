import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiFetch } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
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
  requirementId: string
  title: string
  status: string
  sessionStatus: string | null
  createdBy: string
  ownerId: string
  projectId: string
  projectName: string
  branchName: string
  agentLabel: string
  interactiveInput: boolean
  interrupted: boolean
}

interface WorkbenchSnapshot {
  scope: string
  requirements: ApiRequirement[]
  tasks: ApiTask[]
  counts: WorkbenchCounts
}

interface RequirementDetailResponse {
  requirement: ApiRequirement & { code?: string; priority?: string }
  draft: string
  confirmed: boolean
  confirmedBy: string | null
  tasks: Array<{
    id: string
    requirementId: string
    title: string
    status: string
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
    requirementId: string
    title: string
    status: string
    projectId: string
    projectName: string
    branchName: string
    createdBy: string
    ownerId: string
    description?: string
    frozenRepoCount: number
  }
  frozenRepositories: Array<{
    id: string
    projectRepositoryId: string
    displayName: string | null
    localDirectoryName: string | null
    cloneUrl: string | null
    baseBranch: string
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
    requirementId: item.requirementId,
    title: item.title,
    status: item.status,
    sessionStatus: item.sessionStatus,
    createdBy: item.createdBy,
    ownerId: item.ownerId,
    projectId: item.projectId,
    projectName: item.projectName,
    branchName: item.branchName || '',
    providerName: '项目默认',
    modelId: '',
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
      startOrRetryBlocked: true,
      startOrRetryReason: 'Codex / 执行节点尚未接通',
      requestChangesBlocked: true,
      requestChangesReason: '要求修改需要新 Session，尚未实现',
      retryBlocked: true,
      retryReason: '附说明重试需要执行面，尚未实现',
    },
  }
}

export const useWorkbenchStore = defineStore('workbench', () => {
  const auth = useAuthStore()
  const requirements = ref<WorkRequirement[]>([])
  const tasks = ref<WorkTask[]>([])
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

  const visibleRequirements = computed(() => requirements.value)
  const visibleTasks = computed(() => tasks.value)

  async function load(nextScope: 'mine' | 'member' | 'waiting' = scope.value) {
    scope.value = nextScope
    loading.value = true
    error.value = ''
    try {
      const snapshot = await apiFetch<WorkbenchSnapshot>(`/api/v1/workbench?scope=${nextScope}`)
      requirements.value = snapshot.requirements.map(toRequirement)
      tasks.value = snapshot.tasks.map(toTask)
      counts.value = snapshot.counts
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载工作台失败'
      requirements.value = []
      tasks.value = []
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
            requirementId: task.requirementId,
            title: task.title,
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
      requirementId: detail.task.requirementId,
      title: detail.task.title,
      status: detail.task.status,
      sessionStatus: null,
      createdBy: detail.task.createdBy,
      ownerId: detail.task.ownerId,
      projectId: detail.task.projectId,
      projectName: detail.task.projectName,
      branchName: detail.task.branchName,
      agentLabel: 'Codex CLI',
      interactiveInput: false,
      interrupted: false,
    })
    mapped.frozenRepositories = detail.frozenRepositories.map((repo) => ({
      directoryName: repo.localDirectoryName || repo.displayName || repo.projectRepositoryId,
      baseBranch: repo.baseBranch,
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
        ? '审核通过只改任务状态，不会自动合并。Draft MR 由平台 verify/retry-delivery 创建。' +
          (detail.testFileChangeHook ? ` 测试文件变更钩子：${detail.testFileChangeHook}` : '')
        : '平台验证/交付已接通；Codex Agent / Session / 模型代理 / 容器验证仍未实现。'
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

  async function createTask(requirementId: string, title: string) {
    actionError.value = ''
    try {
      await apiFetch(`/api/v1/requirements/${requirementId}/tasks`, {
        method: 'POST',
        body: JSON.stringify({
          title,
          taskType: 'IMPLEMENT',
          priority: 'MEDIUM',
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

  function sendMessage(_id: string, _message: string) {
    actionError.value = '当前适配器不能把消息送进仍在运行的进程，且执行面尚未接通'
  }

  function retryTask(_id: string, _note: string) {
    actionError.value = '附说明重试需要 Codex / 执行节点，尚未实现'
  }

  function requestChanges(_id: string, _note: string) {
    actionError.value = '要求修改需要在同一工作区开新 Session，尚未实现'
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

  return {
    scope,
    loading,
    error,
    actionError,
    retryNote,
    visibleRequirements,
    visibleTasks,
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
    verifyTask,
    retryDelivery,
  }
})
