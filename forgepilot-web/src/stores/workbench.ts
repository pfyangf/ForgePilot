import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { codexCapabilities } from '@/domain/capabilities'
import type { WorkRequirement, WorkTask } from '@/types'

/**
 * 骨架里的工作台数据。访问边界按「当前用户是这些项目的成员」预先收过。
 * 默认「我的」：本人创建，或本人是负责人，或任务属于这样的需求。
 * 不是控制面返回的真实数据。
 */
export const currentUserId = 'me'

const requirementsSeed: WorkRequirement[] = [
  {
    id: 'req-analysis',
    title: '结算页支持优惠券',
    status: '待分析',
    projectName: '支付',
    createdBy: currentUserId,
    ownerId: currentUserId,
    draft: '理解：在结算页增加优惠券输入。\n任务建议：改 checkout 服务，改 web 结算页。\n未经代码库验证的推测：可能影响计价接口。',
    suggestions: [
      { id: 'sug-1', title: 'checkout 服务接受优惠券' },
      { id: 'sug-2', title: '结算页展示抵扣' },
    ],
    confirmed: false,
  },
  {
    id: 'req-dev',
    title: '订单导出增加仓库字段',
    status: '开发中',
    projectName: '支付',
    createdBy: 'other',
    ownerId: currentUserId,
    draft: '已确认的分析草稿。',
    suggestions: [],
    confirmed: true,
  },
  {
    id: 'req-other',
    title: '对账日报',
    status: '待开发',
    projectName: '支付',
    createdBy: 'other',
    ownerId: 'other',
    draft: '同事的需求。项目成员在「项目内全部」里能看到。',
    suggestions: [],
    confirmed: true,
  },
]

const tasksSeed: WorkTask[] = [
  {
    id: 'task-running',
    requirementId: 'req-dev',
    title: '导出接口增加字段',
    status: 'RUNNING',
    sessionStatus: 'RUNNING',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-running',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [
      { directoryName: 'order-api', changed: true },
      { directoryName: 'order-web', changed: false },
    ],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['session started', 'editing order-api/src/Export.java'],
    verificationCommand: './mvnw -pl order-api test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: '会话仍在该任务自己的工作区里运行。',
  },
  {
    id: 'task-input',
    requirementId: 'req-dev',
    title: '等待补充字段说明',
    status: 'RUNNING',
    sessionStatus: 'WAITING_INPUT',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-input',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: '声明了交互输入的示例适配器',
    interactiveInput: true,
    nativePause: false,
    interrupted: false,
    checkouts: [{ directoryName: 'order-api', changed: true }],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['waiting for the field list'],
    verificationCommand: './mvnw test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: '只有声明了交互输入的适配器才接受一条消息。',
  },
  {
    id: 'task-verify',
    requirementId: 'req-dev',
    title: '跑项目验证命令',
    status: 'VERIFYING',
    sessionStatus: 'SUCCEEDED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-verify',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [
      { directoryName: 'order-api', changed: true },
      { directoryName: 'order-web', changed: true },
    ],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['agent session finished'],
    verificationCommand: 'cd workspace && ./mvnw test',
    verificationLog: 'Running order-api tests...\n',
    verificationExitCode: null,
    evidenceNote: '验证在新容器中执行，工作区根目录同时看见多个检出。',
  },
  {
    id: 'task-approval',
    requirementId: 'req-dev',
    title: '等待审核的多仓库改动',
    status: 'WAITING_APPROVAL',
    sessionStatus: 'SUCCEEDED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-approval',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [
      { directoryName: 'order-api', changed: true },
      { directoryName: 'order-web', changed: true },
      { directoryName: 'docs', changed: false },
    ],
    mergeRequests: [
      { directoryName: 'order-api', url: 'https://gitlab.example/order-api/-/merge_requests/12', headSha: 'aaa111' },
      { directoryName: 'order-web', url: 'https://gitlab.example/order-web/-/merge_requests/8', headSha: 'bbb222' },
    ],
    unchangedRepos: ['docs'],
    testFileChanges: ['order-api/src/test/ExportTest.java 被修改'],
    logs: ['verification passed'],
    verificationCommand: './mvnw test',
    verificationLog: 'BUILD SUCCESS',
    verificationExitCode: 0,
    evidenceNote: '一次审核覆盖全部 Draft MR。docs 没有改动，所以没有 MR。',
  },
  {
    id: 'task-wait',
    requirementId: 'req-dev',
    title: '回灌次数已用尽',
    status: 'WAITING_USER',
    sessionStatus: 'SUCCEEDED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-wait',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [{ directoryName: 'order-api', changed: true }],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['verification failed twice'],
    verificationCommand: './mvnw test',
    verificationLog: 'Tests run: 4, Failures: 1',
    verificationExitCode: 1,
    evidenceNote: '自动回灌已到上限。可以选择重试、取消或明确放弃。',
  },
  {
    id: 'task-interrupted',
    requirementId: 'req-dev',
    title: '执行节点中断',
    status: 'WAITING_USER',
    sessionStatus: 'INTERRUPTED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-interrupted',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: true,
    checkouts: [{ directoryName: 'order-api', changed: true }],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['node heartbeat lost'],
    verificationCommand: './mvnw test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: 'Session 已标为 INTERRUPTED。已打快照的改动可以在新节点恢复。',
  },
  {
    id: 'task-failed',
    requirementId: 'req-dev',
    title: '已明确放弃',
    status: 'FAILED',
    sessionStatus: 'FAILED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-failed',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [{ directoryName: 'order-api', changed: true }],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['user abandoned the task'],
    verificationCommand: './mvnw test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: 'FAILED 只表示明确放弃或不可恢复的基础设施错误。',
  },
  {
    id: 'task-done',
    requirementId: 'req-dev',
    title: '已完成的导出改动',
    status: 'COMPLETED',
    sessionStatus: 'SUCCEEDED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-done',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [{ directoryName: 'order-api', changed: true }],
    mergeRequests: [
      { directoryName: 'order-api', url: 'https://gitlab.example/order-api/-/merge_requests/4', headSha: 'ccc333' },
    ],
    unchangedRepos: ['order-web'],
    testFileChanges: [],
    logs: ['approved'],
    verificationCommand: './mvnw test',
    verificationLog: 'BUILD SUCCESS',
    verificationExitCode: 0,
    evidenceNote: '审核已通过。平台没有合并 MR。',
  },
  {
    id: 'task-cancelled',
    requirementId: 'req-dev',
    title: '已取消的尝试',
    status: 'CANCELLED',
    sessionStatus: 'CANCELLED',
    createdBy: currentUserId,
    ownerId: currentUserId,
    projectName: '支付',
    branchName: 'fp/task-cancelled',
    providerName: 'OpenAI Responses',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [{ directoryName: 'order-api', changed: false }],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: ['cancelled, workspace kept'],
    verificationCommand: './mvnw test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: '容器已清理，工作区和已收集证据仍保留。',
  },
  {
    id: 'task-other',
    requirementId: 'req-other',
    title: '同事负责的对账任务',
    status: 'PENDING',
    sessionStatus: null,
    createdBy: 'other',
    ownerId: 'other',
    projectName: '支付',
    branchName: 'fp/task-other',
    providerName: '项目默认',
    modelId: 'gpt-5.4',
    agentLabel: 'Codex CLI',
    ...codexCapabilities,
    interrupted: false,
    checkouts: [],
    mergeRequests: [],
    unchangedRepos: [],
    testFileChanges: [],
    logs: [],
    verificationCommand: './mvnw test',
    verificationLog: '',
    verificationExitCode: null,
    evidenceNote: '这条不在「我的」里，但项目成员切换到「项目内全部」可以看到。',
  },
]

export const useWorkbenchStore = defineStore('workbench', () => {
  const requirements = ref<WorkRequirement[]>(requirementsSeed.map((item) => ({ ...item, suggestions: item.suggestions.map((task) => ({ ...task })) })))
  const tasks = ref<WorkTask[]>(tasksSeed.map((item) => ({
    ...item,
    checkouts: item.checkouts.map((checkout) => ({ ...checkout })),
    mergeRequests: item.mergeRequests.map((mr) => ({ ...mr })),
    logs: [...item.logs],
    testFileChanges: [...item.testFileChanges],
    unchangedRepos: [...item.unchangedRepos],
  })))
  const scope = ref<'mine' | 'member' | 'waiting'>('mine')
  const retryNote = ref('')

  function isMyRequirement(requirement: WorkRequirement) {
    return requirement.createdBy === currentUserId || requirement.ownerId === currentUserId
  }

  function isMyTask(task: WorkTask) {
    const parent = requirements.value.find((item) => item.id === task.requirementId)
    return task.createdBy === currentUserId || task.ownerId === currentUserId || (parent != null && isMyRequirement(parent))
  }

  function isWaitingOnMe(task: WorkTask) {
    return isMyTask(task) && (task.status === 'WAITING_APPROVAL' || task.status === 'WAITING_USER' || task.sessionStatus === 'WAITING_INPUT')
  }

  const visibleRequirements = computed(() => {
    if (scope.value === 'waiting') {
      const ids = new Set(tasks.value.filter(isWaitingOnMe).map((task) => task.requirementId))
      return requirements.value.filter((item) => ids.has(item.id))
    }
    return scope.value === 'mine' ? requirements.value.filter(isMyRequirement) : requirements.value
  })
  const visibleTasks = computed(() => {
    if (scope.value === 'waiting') {
      return tasks.value.filter(isWaitingOnMe)
    }
    return scope.value === 'mine' ? tasks.value.filter(isMyTask) : tasks.value
  })

  const counts = computed(() => ({
    requirements: visibleRequirements.value.length,
    tasks: visibleTasks.value.length,
    running: visibleTasks.value.filter((task) => task.status === 'RUNNING' || task.status === 'PREPARING').length,
    verifying: visibleTasks.value.filter((task) => task.status === 'VERIFYING').length,
    approval: visibleTasks.value.filter((task) => task.status === 'WAITING_APPROVAL').length,
    waitingUser: visibleTasks.value.filter((task) => task.status === 'WAITING_USER').length,
  }))

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
    retryNote,
    visibleRequirements,
    visibleTasks,
    counts,
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
