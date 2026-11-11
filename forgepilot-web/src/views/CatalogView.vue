<template>
  <div class="catalog">
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">先维护，再关联</h1>
        <p class="page-header__subtitle">工具、供应商和模型都从已有清单里选，页面上不再手填模型 ID。</p>
      </div>
      <div class="page-header__actions">
        <el-button @click="showTool = true">新建编码工具</el-button>
        <el-button type="primary" @click="openCreateProvider">新建供应商</el-button>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </div>
    </div>
    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      class="block"
    />

    <div class="catalog-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>模型供应商</span>
            <span class="muted">{{ providers.length }}</span>
          </div>
        </template>
        <div v-if="!providers.length" class="empty">还没有供应商</div>
        <div v-for="row in providers" :key="row.id" class="provider-item">
          <div class="provider-item__mark">{{ initials(row.name) }}</div>
          <div class="provider-item__body">
            <div class="provider-item__top">
              <strong>{{ row.name }}</strong>
              <span class="fp-status-tag" :class="row.enabled ? 'is-success' : 'is-muted'">{{ row.enabled ? '已启用' : '已停用' }}</span>
            </div>
            <div class="provider-item__meta">
              {{ providerTypeLabel(row.providerType) }}
              · {{ row.upstreamWireApi }}
              · {{ row.hasSecret ? '密钥已配置' : '未配置密钥' }}
            </div>
            <div class="chip-row">
              <span v-for="model in row.models || []" :key="model.code" class="chip" :title="model.name || model.code">
                {{ model.name && model.name !== model.code ? model.name + ' · ' + model.code : model.code }}
              </span>
              <el-button link type="primary" @click="openEditProvider(row)">添加模型码</el-button>
            </div>
            <div class="row-actions">
              <el-button link type="primary" @click="openEditProvider(row)">编辑</el-button>
              <el-button link type="primary" :loading="testingId === row.id" @click="testProvider(row)">测试接口</el-button>
              <el-button link type="danger" @click="removeProvider(row)">删除</el-button>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>编码工具</span>
            <span class="muted">{{ tools.length }}</span>
          </div>
        </template>
        <div v-if="!tools.length" class="empty">还没有编码工具</div>
        <div v-for="row in tools" :key="row.id" class="tool-item">
          <div>
            <strong>{{ row.name }}</strong>
            <div class="provider-item__meta">从清单选择类型和默认模型，不必手填枚举或模型 ID</div>
            <div class="chip-row">
              <span class="chip">{{ agentTypeLabel(row.agentType) || row.agentType }}</span>
              <span v-if="providerName(row.modelProviderId) || row.modelId" class="chip">
                {{ [providerName(row.modelProviderId), row.modelId].filter(Boolean).join(' / ') }}
              </span>
              <span class="fp-status-tag" :class="row.enabled ? 'is-success' : 'is-muted'">{{ row.enabled ? '已启用' : '已停用' }}</span>
            </div>
          </div>
        </div>
        <p class="hint">项目和任务里的「默认模型」只会列出已关联供应商下的模型。新增模型时，在供应商里维护码和显示名即可。</p>
      </el-card>
    </div>

    <el-dialog v-model="showProvider" :title="editingProviderId ? '编辑供应商' : '新建供应商'" width="640px">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="providerForm.providerType" style="width: 100%">
            <el-option v-for="item in PROVIDER_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" required><el-input v-model="providerForm.baseUrl" /></el-form-item>
        <el-form-item label="上游协议" required>
          <el-select v-model="providerForm.upstreamWireApi" style="width: 100%">
            <el-option label="responses" value="responses" />
            <el-option label="chat_completions" value="chat_completions" />
            <el-option label="native" value="native" />
          </el-select>
        </el-form-item>
        <el-form-item label="翻译网关"><el-input v-model="providerForm.translationGateway" placeholder="非 Responses 时填写" /></el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="providerForm.apiKey"
            type="password"
            show-password
            :placeholder="editingProviderId ? '留空则不改密钥' : '不会出现在列表响应里'"
          />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="providerForm.enabled" /></el-form-item>
        <el-form-item label="模型码">
          <div class="models">
            <div v-for="(model, index) in providerForm.models" :key="index" class="model-row">
              <el-input v-model="model.code" placeholder="模型标识，如 deepseek-chat" />
              <el-input v-model="model.name" placeholder="显示名，如 DeepSeek V3" />
              <el-button link type="danger" @click="providerForm.models.splice(index, 1)">移除</el-button>
            </div>
            <el-button @click="providerForm.models.push({ code: '', name: '' })">添加模型码</el-button>
          </div>
          <p class="hint">模型码在这里维护。项目和任务里只从这份清单选择，不会再手填 ID。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProvider = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTool" title="新建编码工具" width="560px">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="toolForm.name" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="toolForm.agentType" style="width: 100%">
            <el-option v-for="item in AGENT_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认供应商">
          <el-select v-model="toolForm.modelProviderId" clearable filterable style="width: 100%" @change="onToolProviderChange">
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认模型">
          <el-select
            v-model="toolForm.modelId"
            clearable
            filterable
            style="width: 100%"
            :disabled="!toolForm.modelProviderId"
            :placeholder="toolForm.modelProviderId ? '从该供应商的模型里选择' : '请先选择供应商'"
          >
            <el-option
              v-for="model in toolModelOptions"
              :key="model.code"
              :label="modelOptionLabel(model)"
              :value="model.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTool = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTool">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiFetch } from '@/api/http'
import {
  AGENT_TYPES,
  PROVIDER_TYPES,
  agentTypeLabel,
  isModelCompatible,
  modelOptionLabel,
  modelsForProvider,
  providerTypeLabel,
} from '@/domain/catalog'

interface ModelCode {
  code: string
  name: string | null
}

interface ProviderRow {
  id: string
  name: string
  providerType: string
  baseUrl: string
  enabled: boolean
  apiVersion: string | null
  upstreamWireApi: string
  translationGateway: string | null
  hasSecret: boolean
  eligibleForCodex: boolean
  models: ModelCode[] | null
}

interface ToolRow {
  id: string
  name: string
  agentType: string
  modelProviderId: string | null
  modelId: string | null
  enabled: boolean
}

interface TestResult {
  ok: boolean
  status: number
  latencyMs: number
  message: string
  detail: string | null
}

const providers = ref<ProviderRow[]>([])
const tools = ref<ToolRow[]>([])
const loading = ref(false)
const saving = ref(false)
const testingId = ref('')
const error = ref('')
const showProvider = ref(false)
const showTool = ref(false)
const editingProviderId = ref<string | null>(null)
const providerForm = reactive({
  name: '',
  providerType: 'OPENAI_COMPAT',
  baseUrl: 'http://127.0.0.1:18080/v1',
  upstreamWireApi: 'responses',
  translationGateway: '',
  apiKey: '',
  enabled: true,
  models: [] as { code: string; name: string }[],
})
const toolForm = reactive({
  name: 'Codex CLI',
  agentType: 'CODEX_CLI',
  modelProviderId: '',
  modelId: '',
})

const toolModelOptions = computed(() => modelsForProvider(providers.value, toolForm.modelProviderId))

function initials(name: string) {
  const trimmed = (name || '').trim()
  if (!trimmed) return 'P'
  const parts = trimmed.split(/\s+/)
  if (parts.length === 1) return trimmed.slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

function providerName(id: string | null) {
  if (!id) return ''
  return providers.value.find((item) => item.id === id)?.name || ''
}

function onToolProviderChange() {
  if (!isModelCompatible(providers.value, toolForm.modelProviderId, toolForm.modelId)) {
    toolForm.modelId = ''
  }
}

function resetProviderForm() {
  providerForm.name = ''
  providerForm.providerType = 'OPENAI_COMPAT'
  providerForm.baseUrl = 'http://127.0.0.1:18080/v1'
  providerForm.upstreamWireApi = 'responses'
  providerForm.translationGateway = ''
  providerForm.apiKey = ''
  providerForm.enabled = true
  providerForm.models = []
}

function openCreateProvider() {
  editingProviderId.value = null
  resetProviderForm()
  showProvider.value = true
}

function openEditProvider(row: ProviderRow) {
  editingProviderId.value = row.id
  providerForm.name = row.name
  providerForm.providerType = row.providerType
  providerForm.baseUrl = row.baseUrl
  providerForm.upstreamWireApi = row.upstreamWireApi
  providerForm.translationGateway = row.translationGateway || ''
  providerForm.apiKey = ''
  providerForm.enabled = row.enabled
  providerForm.models = (row.models || []).map((m) => ({ code: m.code, name: m.name || '' }))
  showProvider.value = true
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    providers.value = await apiFetch<ProviderRow[]>('/api/v1/model-providers')
    tools.value = await apiFetch<ToolRow[]>('/api/v1/coding-tools')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败（需要租户管理员）'
    providers.value = []
    tools.value = []
  } finally {
    loading.value = false
  }
}

function providerPayload() {
  const models = providerForm.models
    .map((m) => ({ code: m.code.trim(), name: m.name.trim() || null }))
    .filter((m) => m.code)
  return {
    name: providerForm.name,
    providerType: providerForm.providerType,
    baseUrl: providerForm.baseUrl,
    upstreamWireApi: providerForm.upstreamWireApi,
    translationGateway: providerForm.translationGateway || null,
    apiKey: providerForm.apiKey || null,
    enabled: providerForm.enabled,
    models,
  }
}

async function saveProvider() {
  saving.value = true
  try {
    const body = providerPayload()
    if (editingProviderId.value) {
      await apiFetch(`/api/v1/model-providers/${editingProviderId.value}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      ElMessage.success('供应商已更新')
    } else {
      await apiFetch('/api/v1/model-providers', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      ElMessage.success('供应商已创建')
    }
    showProvider.value = false
    providerForm.apiKey = ''
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function testProvider(row: ProviderRow) {
  testingId.value = row.id
  try {
    const modelCode = row.models && row.models.length > 0 ? row.models[0].code : undefined
    const result = await apiFetch<TestResult>(`/api/v1/model-providers/${row.id}/test`, {
      method: 'POST',
      body: JSON.stringify(modelCode ? { modelCode } : {}),
    })
    const tip = `${result.message}（${result.latencyMs} ms）`
    if (result.ok) ElMessage.success(tip)
    else ElMessage.error(tip)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '测试失败')
  } finally {
    testingId.value = ''
  }
}

async function removeProvider(row: ProviderRow) {
  try {
    await ElMessageBox.confirm(`确定删除供应商「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await apiFetch(`/api/v1/model-providers/${row.id}`, { method: 'DELETE' })
    ElMessage.success('已删除')
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

async function createTool() {
  saving.value = true
  try {
    await apiFetch('/api/v1/coding-tools', {
      method: 'POST',
      body: JSON.stringify({
        name: toolForm.name,
        agentType: toolForm.agentType,
        modelProviderId: toolForm.modelProviderId || null,
        modelId: toolForm.modelId || null,
        enabled: true,
        interrupt: true,
        eventStream: true,
        toolEvents: true,
        structuredResult: true,
      }),
    })
    showTool.value = false
    ElMessage.success('编码工具已创建')
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void refresh()
})
</script>

<style scoped>
.block {
  margin-top: 12px;
}
.catalog-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 14px;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.muted {
  color: var(--fp-color-muted);
  font-weight: 400;
}
.provider-item,
.tool-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.provider-item:first-of-type,
.tool-item:first-of-type {
  border-top: 0;
  padding-top: 0;
}
.provider-item__mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--fp-color-primary-soft);
  color: #8fb0ff;
  font-weight: 700;
  font-size: 12px;
  flex: none;
}
.provider-item__body {
  flex: 1;
  min-width: 0;
}
.provider-item__top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}
.provider-item__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--fp-color-text-secondary);
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  align-items: center;
}
.chip {
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #121a2c;
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-family: var(--fp-font-mono);
  font-size: 11px;
  line-height: 22px;
  color: #d7deee;
}
.row-actions {
  margin-top: 6px;
}
.hint {
  margin: 8px 0 0;
  color: var(--fp-color-text-secondary);
  font-size: 12px;
}
.empty {
  color: var(--fp-color-muted);
  font-size: 13px;
}
.models {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.model-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: center;
}
@media (max-width: 1100px) {
  .catalog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
