<template>
  <div v-if="task">
    <div class="fp-crumb">
      <button type="button" @click="router.push('/')">返回</button>
      <span>/</span>
      <button type="button" @click="router.push('/')">工作台</button>
      <span>/</span>
      <span>任务详情</span>
      <span v-if="task.code" class="fp-code-chip">{{ task.code }}</span>
    </div>
    <el-alert v-if="store.actionError" class="block" type="error" show-icon :closable="false" :title="store.actionError" />
    <div class="page-header">
      <div class="page-header__titles">
        <div class="fp-title-row">
          <h1 class="page-header__title">{{ task.title }}</h1>
          <span v-if="task.code" class="fp-code-chip">{{ task.code }}</span>
          <span v-if="task.taskType" class="fp-status-tag is-primary">{{ task.taskType }}</span>
          <span class="fp-status-tag" :class="statusToneClass(task.status)">{{ taskStatusLabel(task.status) }}</span>
        </div>
        <div class="fp-meta-row">
          <span>
            关联需求
            <button v-if="task.requirementId" type="button" class="link" @click="router.push(`/requirements/${task.requirementId}`)">
              <span v-if="task.requirementCode" class="fp-code-chip fp-code-chip--req">{{ task.requirementCode }}</span>
              <span v-else>打开需求</span>
            </button>
          </span>
          <span>{{ task.projectName }}</span>
          <span v-if="task.agentLabel">工具 {{ task.agentLabel }}</span>
          <span v-if="task.modelId">模型 {{ task.modelId }}</span>
          <span v-if="task.branchName" class="meta-branch">工作分支 <BranchPill :name="task.branchName" /></span>
        </div>
      </div>
    </div>

    <el-card class="block">
      <template #header>创建时冻结的仓库集合</template>
      <el-table :data="task.frozenRepositories || []" empty-text="没有冻结记录">
        <el-table-column prop="directoryName" label="检出目录" width="160" />
        <el-table-column prop="baseBranch" label="基线分支" width="140" />
        <el-table-column prop="deliveryState" label="交付状态" width="120" />
        <el-table-column prop="cloneUrl" label="地址" />
      </el-table>
    </el-card>

    <el-card v-if="task.status === 'RUNNING'" class="block">
      <template #header>执行中的会话（Task ≠ Session）</template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="Codex 不声明向运行中进程发消息。需要继续时请取消后附说明重试（同一工作区新 Session）。不会假装编码已完成。高频日志走 Redis/SSE；归档走 MinIO（默认内存假存储）。"
      />
      <pre class="log">{{ task.logs.join('\n') || '暂无日志（可轮询 /session）' }}</pre>
      <div class="row">
        <el-input v-model="note" placeholder="说明 / 仅 WAITING_INPUT 时可作输入" />
        <el-button :loading="saving" @click="sendInput">发送输入</el-button>
        <el-button :loading="saving" @click="retryAgent">附说明重试</el-button>
        <el-button type="danger" :loading="saving" @click="cancel">取消</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'VERIFYING'" class="block">
      <template #header>验证</template>
      <el-alert type="info" :closable="false" show-icon title="平台正在跑项目配置的验证命令（本机子进程；容器验证器可替换）。不会假装 Codex 已执行。" />
    </el-card>

    <div v-else-if="task.status === 'WAITING_APPROVAL'" class="implement-grid block">
      <el-card shadow="never">
        <template #header>Draft PR 与验证证据</template>
        <p>由平台交付创建。审核通过只把任务标为已完成，不会自动合并。</p>
        <el-alert
          v-if="task.testFileChanges?.length"
          class="block"
          type="warning"
          show-icon
          :closable="false"
          :title="'检测到测试文件变更：' + task.testFileChanges.join('；')"
        />
        <el-table :data="task.mergeRequests" empty-text="还没有 Draft 合并请求">
          <el-table-column prop="directoryName" label="仓库目录" />
          <el-table-column label="合并请求">
            <template #default="{ row }">
              <a v-if="row.url" :href="row.url" target="_blank" rel="noreferrer">{{ row.url }}</a>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="headSha" label="Head" width="140" />
        </el-table>
        <p class="hint">{{ task.evidenceNote }}</p>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="row-between">
            <span>审核意见</span>
            <el-button :loading="generating" @click="generateDraft('CODE_REVIEW')">AI 生成审核意见</el-button>
          </div>
        </template>
        <p class="hint">先 AI 生成，再修改后提交。审核通过不会自动合并 Draft PR。</p>
        <el-input v-model="note" type="textarea" :rows="10" placeholder="审核意见（AI 生成草稿，请修改后提交）" />
        <div class="row">
          <el-button type="primary" :loading="saving" @click="approve">审核通过</el-button>
          <el-button :loading="saving" @click="requestChanges">要求修改</el-button>
        </div>
      </el-card>
    </div>

    <el-card v-else-if="task.status === 'WAITING_USER'" class="block">
      <template #header>{{ task.interrupted ? '节点中断' : '等待人工处理' }}</template>
      <el-alert type="info" :closable="false" show-icon :title="task.unsupported?.retryReason || '重试需要执行面'" />
      <div class="row">
        <el-input v-model="note" placeholder="说明（取消/放弃时可选）" />
        <el-button type="primary" :loading="saving" @click="verify">再次验证</el-button>
        <el-button :loading="saving" @click="retryDelivery">重试交付</el-button>
        <el-button :loading="saving" @click="retryAgent">Agent 重试</el-button>
        <el-button :loading="saving" @click="cancel">取消</el-button>
        <el-button type="danger" :loading="saving" @click="abandon">明确放弃</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'COMPLETED'" class="block">
      <template #header>交付记录</template>
      <p>任务已完成。关联的 MR 若存在会保持打开，平台不会合并。</p>
    </el-card>

    <div v-else-if="task.status === 'PENDING' || task.status === 'PREPARING'" class="implement-grid block">
      <el-card shadow="never">
        <template #header>
          <div class="row-between">
            <span>任务说明</span>
            <div class="row" style="margin: 0">
              <el-button :loading="generating" @click="generateDraft('TASK_BRIEF')">AI 生成任务说明</el-button>
              <el-button :loading="saving" @click="saveTask">保存说明</el-button>
            </div>
          </div>
        </template>
        <p class="hint">只是编码说明，不会单独作为上下文。启动 Agent Session 时会把右侧文档包一并写入本次会话并冻结。</p>
        <el-input v-model="description" type="textarea" :rows="12" placeholder="目标、实现要点、验收对照" />
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="row-between">
            <span>提供给 AI Coding 的文档</span>
            <el-tag v-if="documentPack.frozen" type="success" size="small">已冻结</el-tag>
            <el-tag v-else size="small">启动时写入</el-tag>
          </div>
        </template>
        <p class="hint">启动时把任务相关文档一并交给编码工具，而不只是任务说明。一般文档仍按是否「进入 Agent」追加。启动后再改不会影响已经在跑的 Agent。</p>
        <div class="fp-docpack">
          <div v-for="item in documentPack.items" :key="item.kind + (item.sourceRef || '')" class="fp-docpack__item">
            <div class="fp-docpack__head">
              <strong>{{ item.title || item.typeLabel }}</strong>
              <span class="fp-docpack__kind">{{ item.typeLabel }}{{ item.included === false ? ' · 未纳入' : ' · 已纳入' }}</span>
            </div>
            <pre class="pack-body">{{ item.body || '（暂无正文）' }}</pre>
          </div>
          <el-empty v-if="!documentPack.items.length" description="正在准备文档包" :image-size="60" />
        </div>
      </el-card>
      <el-card class="span-2" shadow="never">
        <template #header>配置</template>
        <p class="hint">选择仓库时必须指定源分支和新建工作分支。默认按需求号生成，例如 fp/REQ-DEMO99-3；若冲突会带上任务号后缀。Agent 不得自行另起分支名。</p>
        <BindChain
          :tools="tools"
          :providers="providers"
          allow-project-default
          :default-agent-profile-id="projectDefaults.defaultAgentProfileId"
          :default-model-provider-id="projectDefaults.defaultModelProviderId"
          :default-model-id="projectDefaults.defaultModelId"
          v-model:agent-profile-id="config.agentProfileId"
          v-model:model-provider-id="config.modelProviderId"
          v-model:model-id="config.modelId"
        />
        <div class="config-grid" style="margin-top: 12px">
          <el-form-item label="工作分支">
            <el-input v-model="config.workingBranch" placeholder="fp/需求编号" />
          </el-form-item>
        </div>
        <div v-for="repo in repoBindings" :key="repo.projectRepositoryId" class="repo-bind">
          <div class="repo-bind__name">{{ repo.displayName || repo.directoryName }}</div>
          <div class="row">
            <el-select v-model="repo.sourceBranch" filterable allow-create default-first-option placeholder="源分支" style="width: 220px">
              <el-option v-for="name in repo.remoteBranches" :key="name" :label="name" :value="name" />
            </el-select>
            <el-input v-model="repo.workingBranch" placeholder="新建分支名称" />
          </div>
          <p v-if="repo.branchesError" class="hint">{{ repo.branchesError }}</p>
        </div>
        <div class="row">
          <el-button :loading="saving" @click="saveTask">保存配置</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="task.unsupported?.startOrRetryBlocked"
            @click="startAgent"
          >启动 Agent Session</el-button>
          <el-button :loading="saving" @click="verify">平台验证</el-button>
          <el-button type="danger" :loading="saving" @click="cancel">取消</el-button>
        </div>
        <el-alert
          class="block"
          :type="task.unsupported?.startOrRetryBlocked ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="task.unsupported?.startOrRetryBlocked
            ? task.unsupported.startOrRetryReason
            : '可启动 Session。若本机无编码工具或执行节点不可达，会返回 NOT_AVAILABLE 并进入待处理，不会假装成功。'"
        />
      </el-card>
    </div>

    <el-card v-else class="block">
      <template #header>证据</template>
      <p>状态 {{ task.status }}。这里只展示已经收集的说明。</p>
      <pre class="log">{{ task.logs.join('\n') || '没有日志' }}</pre>
      <div v-if="task.status !== 'FAILED' && task.status !== 'CANCELLED'" class="row">
        <el-button type="danger" :loading="saving" @click="cancel">取消</el-button>
      </div>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="row">
          <span>会话日志与归档</span>
          <el-button link type="primary" :loading="saving" @click="refreshArchives">刷新归档</el-button>
          <el-button
            v-if="task.sessionId"
            link
            type="primary"
            :loading="saving"
            @click="archiveCurrent"
          >归档当前会话</el-button>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="上方卡片继续轮询/展示实时日志块。归档列表来自对象存储指针（默认内存假存储；生产 MinIO）。"
      />
      <el-table :data="archivedLogs" empty-text="尚无归档" class="block">
        <el-table-column prop="sessionId" label="Session" width="280" />
        <el-table-column prop="objectKey" label="对象键" />
        <el-table-column prop="byteSize" label="字节" width="100" />
        <el-table-column prop="createdAt" label="时间" width="200" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadArchive(row.sessionId)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
  <el-empty v-else description="没有这条任务">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { apiFetch, getAccessToken } from '@/api/http'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BindChain from '@/components/BindChain.vue'
import BranchPill from '@/components/BranchPill.vue'
import { useCatalogOptions } from '@/composables/useCatalogOptions'
import { useWorkbenchStore } from '@/stores/workbench'
import { statusTone, taskStatusLabel } from '@/domain/labels'

const props = defineProps<{ id: string }>()
const store = useWorkbenchStore()
const router = useRouter()
const task = computed(() => store.taskById(props.id))
const note = ref('')
const saving = ref(false)
const generating = ref(false)
const description = ref('')
const config = ref({
  title: '',
  workingBranch: '',
  modelProviderId: '',
  modelId: '',
  agentProfileId: '',
  priority: 'MEDIUM',
})
const catalog = useCatalogOptions()
const providers = catalog.providers
const tools = catalog.tools
const projectDefaults = ref({
  defaultAgentProfileId: '',
  defaultModelProviderId: '',
  defaultModelId: '',
})
const documentPack = ref<{ frozen: boolean; sessionId?: string; items: Array<{ kind: string; typeLabel: string; title: string; sourceRef?: string; body: string; included?: boolean }> }>({
  frozen: false,
  items: [],
})
const repoBindings = ref<Array<{
  projectRepositoryId: string
  displayName: string
  directoryName: string
  sourceBranch: string
  workingBranch: string
  remoteBranches: string[]
  branchesError: string
}>>([])
const archivedLogs = ref<{ id: string; sessionId: string; objectKey: string; byteSize: number; createdAt: string }[]>([])
let pollTimer: number | undefined

function statusToneClass(status: string) {
  const tone = statusTone(status)
  return tone ? `is-${tone}` : ''
}

function syncForm() {
  const current = task.value
  if (!current) return
  description.value = current.description || ''
  config.value = {
    title: current.title,
    workingBranch: current.branchName || '',
    modelProviderId: current.modelProviderId || '',
    modelId: current.modelId || '',
    agentProfileId: current.agentProfileId || '',
    priority: 'MEDIUM',
  }
}

async function loadCatalog() {
  await catalog.load()
  const projectId = task.value?.projectId
  if (!projectId) return
  try {
    const detail = await apiFetch<{ project: { defaultAgentProfileId?: string; defaultModelProviderId?: string; defaultModelId?: string } }>(
      `/api/v1/projects/${projectId}`,
    )
    projectDefaults.value = {
      defaultAgentProfileId: detail.project.defaultAgentProfileId || '',
      defaultModelProviderId: detail.project.defaultModelProviderId || '',
      defaultModelId: detail.project.defaultModelId || '',
    }
  } catch {
    projectDefaults.value = { defaultAgentProfileId: '', defaultModelProviderId: '', defaultModelId: '' }
  }
}

async function loadDocumentPack() {
  try {
    documentPack.value = await apiFetch(`/api/v1/tasks/${props.id}/document-pack`)
  } catch {
    documentPack.value = { frozen: false, items: [] }
  }
}

async function loadRepoBindings() {
  const current = task.value
  if (!current?.projectId) return
  const rows = current.frozenRepositories || []
  const next = []
  for (const repo of rows) {
    const projectRepositoryId = repo.projectRepositoryId || ''
    let remoteBranches = [repo.defaultBranch, repo.baseBranch].filter((item): item is string => !!item)
    let branchesError = ''
    if (projectRepositoryId) {
      try {
        const listed = await apiFetch<{ branches: Array<{ name: string }>; error?: string }>(
          `/api/v1/projects/${current.projectId}/repositories/${projectRepositoryId}/branches`,
        )
        remoteBranches = Array.from(new Set((listed.branches || []).map((item) => item.name).concat(remoteBranches)))
        branchesError = listed.error || ''
      } catch (err) {
        branchesError = err instanceof Error ? err.message : '无法列出远端分支，请手工填写分支名'
      }
    }
    next.push({
      projectRepositoryId,
      displayName: repo.displayName || repo.directoryName,
      directoryName: repo.directoryName,
      sourceBranch: repo.baseBranch || repo.defaultBranch || 'main',
      workingBranch: repo.workingBranch || current.branchName || '',
      remoteBranches,
      branchesError,
    })
  }
  repoBindings.value = next
}

async function generateDraft(scene: 'TASK_BRIEF' | 'CODE_REVIEW') {
  const current = task.value
  if (!current) return
  generating.value = true
  try {
    const result = await apiFetch<{ text: string }>('/api/v1/ai/drafts', {
      method: 'POST',
      body: JSON.stringify({
        scene,
        projectId: current.projectId,
        requirementId: current.requirementId,
        taskId: current.id,
      }),
    })
    if (scene === 'TASK_BRIEF') {
      description.value = result.text
    } else {
      note.value = result.text
    }
    ElMessage.success('已生成草稿，请确认后再提交')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '生成失败')
  } finally {
    generating.value = false
  }
}

async function saveTask() {
  saving.value = true
  try {
    await apiFetch(`/api/v1/tasks/${props.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        title: config.value.title || undefined,
        description: description.value,
        priority: config.value.priority,
        workingBranch: config.value.workingBranch || undefined,
        modelProviderId: config.value.modelProviderId || null,
        modelId: config.value.modelId || null,
        agentProfileId: config.value.agentProfileId || null,
        repositories: repoBindings.value.map((item) => ({
          projectRepositoryId: item.projectRepositoryId,
          sourceBranch: item.sourceBranch,
          workingBranch: item.workingBranch || config.value.workingBranch,
        })),
      }),
    })
    await store.loadTaskDetail(props.id)
    await loadDocumentPack()
    ElMessage.success('任务配置已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    await store.loadTaskDetail(props.id)
  } catch {
    await store.load('member')
  }
  syncForm()
  await Promise.all([refreshArchives(), loadCatalog(), loadDocumentPack(), loadRepoBindings()])
  pollTimer = window.setInterval(() => {
    void store.loadTaskDetail(props.id).catch(() => undefined)
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

watch(
  () => task.value && `${task.value.id}:${task.value.status}:${task.value.description || ''}:${task.value.branchName}`,
  () => {
    syncForm()
  },
)

async function refreshArchives() {
  try {
    archivedLogs.value = await apiFetch(`/api/v1/tasks/${props.id}/archived-logs`)
  } catch {
    archivedLogs.value = []
  }
}

async function archiveCurrent() {
  const sid = task.value?.sessionId
  if (!sid) {
    ElMessage.warning('当前任务没有 Session')
    return
  }
  saving.value = true
  try {
    await apiFetch(`/api/v1/tasks/${props.id}/sessions/${sid}/archive-logs`, { method: 'POST' })
    ElMessage.success('已请求归档')
    await refreshArchives()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '归档失败')
  } finally {
    saving.value = false
  }
}

async function downloadArchive(sessionId: string) {
  try {
    const token = getAccessToken()
    const res = await fetch(`/api/v1/tasks/${props.id}/sessions/${sessionId}/archived-log`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${sessionId}.log`
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '下载失败')
  }
}

async function cancel() {
  saving.value = true
  try {
    await store.cancelTask(props.id, note.value)
    ElMessage.success('任务已取消')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
  } finally {
    saving.value = false
  }
}

async function abandon() {
  saving.value = true
  try {
    await store.abandonTask(props.id, note.value)
    ElMessage.success('已明确放弃')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    saving.value = false
  }
}

async function approve() {
  saving.value = true
  try {
    await store.approveTask(props.id, note.value)
    ElMessage.success('审核已通过')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '审核失败')
  } finally {
    saving.value = false
  }
}

async function verify() {
  saving.value = true
  try {
    await store.verifyTask(props.id)
    ElMessage.success('验证/交付流程已执行')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '验证失败')
  } finally {
    saving.value = false
  }
}

async function retryDelivery() {
  saving.value = true
  try {
    await store.retryDelivery(props.id)
    ElMessage.success('已重试交付')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  } finally {
    saving.value = false
  }
}

async function startAgent() {
  saving.value = true
  try {
    await apiFetch(`/api/v1/tasks/${props.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        description: description.value,
        workingBranch: config.value.workingBranch || undefined,
        modelProviderId: config.value.modelProviderId || null,
        modelId: config.value.modelId || null,
        agentProfileId: config.value.agentProfileId || null,
        repositories: repoBindings.value.map((item) => ({
          projectRepositoryId: item.projectRepositoryId,
          sourceBranch: item.sourceBranch,
          workingBranch: item.workingBranch || config.value.workingBranch,
        })),
      }),
    })
    await store.startTask(props.id, note.value)
    await loadDocumentPack()
    ElMessage.success('已请求启动 Session（若 NOT_AVAILABLE 请看错误提示）')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '启动失败')
  } finally {
    saving.value = false
  }
}

async function retryAgent() {
  saving.value = true
  try {
    await store.retryTask(props.id, note.value)
    ElMessage.success('已请求同工作区重试')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  } finally {
    saving.value = false
  }
}

async function sendInput() {
  saving.value = true
  try {
    await store.sendMessage(props.id, note.value)
    ElMessage.success('已发送')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '发送失败')
  } finally {
    saving.value = false
  }
}

async function requestChanges() {
  saving.value = true
  try {
    await store.requestChanges(props.id, note.value)
    ElMessage.success('已请求修改（新 Session）')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '要求修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.block {
  margin-top: 16px;
}
.row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.hint {
  color: var(--fp-color-text-secondary);
  font-size: 13px;
}
.link {
  border: 0;
  background: none;
  padding: 0;
  color: var(--fp-color-primary);
  cursor: pointer;
}
.implement-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.span-2 {
  grid-column: 1 / -1;
}
.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}
.repo-bind {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--fp-color-border);
  border-radius: var(--fp-radius);
}
.repo-bind__name {
  font-weight: 600;
  margin-bottom: 8px;
}
.pack-body {
  margin: 8px 0 0;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--fp-color-text-secondary);
}
.meta-branch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
@media (max-width: 1100px) {
  .implement-grid,
  .config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
