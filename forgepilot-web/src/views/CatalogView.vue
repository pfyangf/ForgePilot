<template>
  <div>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="模型与编码工具目录"
      description="由租户管理员维护。接口响应只有 hasSecret，不会回传 apiKey / ciphertext。演示种子使用 PLACEHOLDER 密文，不是生产密钥。"
    />
    <div class="toolbar">
      <el-button type="primary" @click="openCreateProvider">新建 Provider</el-button>
      <el-button type="primary" @click="showTool = true">新建编码工具</el-button>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
      <span v-if="error" class="error">{{ error }}</span>
    </div>

    <el-card>
      <template #header>模型 Provider</template>
      <el-table :data="providers" empty-text="还没有 Provider">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="providerType" label="类型" width="140" />
        <el-table-column prop="upstreamWireApi" label="上游协议" width="140" />
        <el-table-column label="模型码" min-width="160">
          <template #default="{ row }">
            {{ formatModels(row.models) }}
          </template>
        </el-table-column>
        <el-table-column label="密钥" width="90">
          <template #default="{ row }">{{ row.hasSecret ? '已配置' : '无' }}</template>
        </el-table-column>
        <el-table-column label="Codex 可用" width="110">
          <template #default="{ row }">{{ row.eligibleForCodex ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditProvider(row)">编辑</el-button>
            <el-button link type="primary" :loading="testingId === row.id" @click="testProvider(row)">测试</el-button>
            <el-button link type="danger" @click="removeProvider(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>编码工具</template>
      <el-table :data="tools" empty-text="还没有编码工具">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="agentType" label="类型" width="120" />
        <el-table-column prop="modelId" label="默认模型" width="140" />
        <el-table-column prop="modelProviderId" label="默认 Provider" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showProvider" :title="editingProviderId ? '编辑 Provider' : '新建 Provider'" width="640px">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item label="类型" required><el-input v-model="providerForm.providerType" placeholder="OPENAI_COMPAT" /></el-form-item>
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
              <el-input v-model="model.code" placeholder="模型标识，如 gpt-5" />
              <el-input v-model="model.name" placeholder="显示名（可选）" />
              <el-button link type="danger" @click="providerForm.models.splice(index, 1)">移除</el-button>
            </div>
            <el-button @click="providerForm.models.push({ code: '', name: '' })">添加模型码</el-button>
          </div>
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
        <el-form-item label="类型" required><el-input v-model="toolForm.agentType" placeholder="CODEX_CLI" /></el-form-item>
        <el-form-item label="默认 Provider">
          <el-select v-model="toolForm.modelProviderId" clearable style="width: 100%">
            <el-option v-for="p in providers" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认模型"><el-input v-model="toolForm.modelId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTool = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTool">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiFetch } from '@/api/http'

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
  modelId: 'gpt-5',
})

function formatModels(models: ModelCode[] | null | undefined) {
  if (!models || models.length === 0) return '-'
  return models.map((m) => (m.name ? `${m.code} (${m.name})` : m.code)).join(', ')
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
      ElMessage.success('Provider 已更新')
    } else {
      await apiFetch('/api/v1/model-providers', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      ElMessage.success('Provider 已创建')
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
    await ElMessageBox.confirm(`确定删除 Provider「${row.name}」？`, '删除确认', { type: 'warning' })
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
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 16px 0;
}
.block {
  margin-top: 16px;
}
.error {
  color: #b91c1c;
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
</style>
