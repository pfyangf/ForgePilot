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
    evidenceNote: '详情页的执行动作仍是占位；列表与进度来自控制面。',
  }
}

export const useWorkbenchStore = defineStore('workbench', () => {
  const auth = useAuthStore()
  const requirements = ref<WorkRequirement[]>([])
  const tasks = ref<WorkTask[]>([])
  const scope = ref<'mine' | 'member' | 'waiting'>('waiting')
  const loading = ref(false)
  const error = ref('')
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

  function confirmRequirement(id: string) {
    const requirement = requirementById(id)
    if (!requirement || requirement.confirmed) {
      return
    }
    requirement.confirmed = true
    requirement.status = '待开发'
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

  function appendLog(task: WorkTask, line: string) {
    task.logs.push(line)
  }

  function sendMessage(id: string, message: string) {
    const task = taskById(id)
    if (!task || task.sessionStatus !== 'WAITING_INPUT' || !task.interactiveInput || message.trim() === '') {
      return
    }
    task.sessionStatus = 'RUNNING'
    appendLog(task, `user: ${message.trim()}`)
  }

  function cancelTask(id: string) {
    const task = taskById(id)
    if (!task || task.status === 'COMPLETED' || task.status === 'FAILED' || task.status === 'CANCELLED') {
      return
    }
    task.status = 'CANCELLED'
    task.sessionStatus = 'CANCELLED'
    appendLog(task, 'cancelled, workspace kept')
  }

  function retryTask(id: string, note: string) {
    const task = taskById(id)
    if (!task) {
      return
    }
    if (task.status !== 'WAITING_USER' && task.status !== 'RUNNING' && task.status !== 'WAITING_APPROVAL') {
      return
    }
    task.status = 'RUNNING'
    task.sessionStatus = 'RUNNING'
    task.interrupted = false
    appendLog(task, `new session on the same workspace: ${note.trim() || 'no note'}`)
  }

  function abandonTask(id: string) {
    const task = taskById(id)
    if (!task || task.status !== 'WAITING_USER') {
      return
    }
    task.status = 'FAILED'
    appendLog(task, 'user abandoned the task')
    task.evidenceNote = '用户明确放弃，任务进入 FAILED。'
  }

  function approveTask(id: string) {
    const task = taskById(id)
    if (!task || task.status !== 'WAITING_APPROVAL') {
      return
    }
    task.status = 'COMPLETED'
    appendLog(task, 'approved, MRs left unmerged')
    task.evidenceNote = '审核已通过。平台没有合并 MR。'
  }

  function requestChanges(id: string, note: string) {
    const task = taskById(id)
    if (!task || task.status !== 'WAITING_APPROVAL') {
      return
    }
    task.status = 'RUNNING'
    task.sessionStatus = 'RUNNING'
    appendLog(task, `changes requested, new session: ${note.trim() || 'no note'}`)
  }

  return {
    scope,
    loading,
    error,
    retryNote,
    visibleRequirements,
    visibleTasks,
    counts,
    currentUserId: computed(() => auth.user?.userId ?? ''),
    load,
    requirementById,
    taskById,
    tasksFor,
    progress,
    confirmRequirement,
    addSuggestion,
    removeSuggestion,
    sendMessage,
    cancelTask,
    retryTask,
    abandonTask,
    approveTask,
    requestChanges,
  }
})
