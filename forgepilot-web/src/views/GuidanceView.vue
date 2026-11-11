<template>
  <div class="guidance">
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">规约与文档</h1>
        <p class="page-header__subtitle">Markdown 导入 · 在线编辑 · 实时预览</p>
      </div>
      <div class="page-header__actions">
        <el-select v-model="scope" class="ctrl-select" @change="load">
          <el-option label="全部可见" value="ALL" />
          <el-option label="平台" value="PLATFORM" />
          <el-option label="公司" value="TENANT" />
          <el-option label="项目" value="PROJECT" />
        </el-select>
        <el-select
          v-if="scope === 'PROJECT' || scope === 'ALL'"
          v-model="projectId"
          clearable
          filterable
          placeholder="选择项目"
          class="ctrl-select ctrl-select--wide"
          @change="load"
        >
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button :disabled="!projectId" @click="compose">查看合成</el-button>
        <el-button :disabled="!projectId" :loading="snapshotting" @click="snapshot">冻结快照</el-button>
        <el-button @click="triggerImport">
          <el-icon class="btn-icon"><Upload /></el-icon>
          导入 Markdown
        </el-button>
        <el-button type="primary" @click="startCreate">新建</el-button>
        <input
          ref="fileInput"
          class="hidden-file"
          type="file"
          accept=".md,.markdown,.txt,text/markdown,text/plain"
          @change="onImportFile"
        />
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" :title="error" class="block-alert" />

    <div class="workspace" v-loading="loading">
      <aside class="doc-list">
        <div class="doc-list__header">
          <span>文档</span>
          <span class="doc-list__count">{{ rows.length }}</span>
        </div>
        <button
          v-for="row in rows"
          :key="row.id"
          type="button"
          class="doc-card"
          :class="{ 'doc-card--active': selectedId === row.id && !isCreating }"
          @click="selectDoc(row)"
        >
          <div class="doc-card__title">{{ row.title }}</div>
          <div class="doc-card__tags">
            <span class="tag" :class="scopeTagClass(row.scope)">{{ scopeLabel(row.scope) }}</span>
            <span class="tag" :class="kindTagClass(row.kind)">{{ kindLabel(row.kind) }}</span>
          </div>
        </button>
        <div v-if="!rows.length" class="doc-list__empty">暂无文档</div>
      </aside>

      <section class="editor-panel">
        <div class="editor-panel__toolbar">
          <div class="editor-panel__left">
            <span class="editor-panel__label">在线编辑</span>
            <el-switch v-model="draft.includeInAgent" size="small" active-text="进入 Agent" />
          </div>
          <div class="editor-panel__right">
            <el-button :disabled="!dirty && !isCreating" @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">保存</el-button>
          </div>
        </div>

        <div v-if="!hasEditor" class="editor-empty">
          从左侧选择文档，或点击「新建 / 导入 Markdown」开始编辑。
        </div>

        <template v-else>
          <div class="editor-meta">
            <el-input v-model="draft.title" class="title-input" placeholder="文档标题" maxlength="256" />
            <div class="meta-row">
              <el-select v-model="draft.scope" class="meta-select" :disabled="!isCreating">
                <el-option label="平台" value="PLATFORM" />
                <el-option label="公司" value="TENANT" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
              <el-select
                v-if="draft.scope === 'PROJECT'"
                v-model="draft.projectId"
                class="meta-select meta-select--wide"
                :disabled="!isCreating"
                placeholder="项目"
              >
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-select v-model="draft.kind" class="meta-select" :disabled="!isCreating">
                <el-option label="规约" value="CONVENTION" />
                <el-option label="文档" value="DOCUMENT" />
              </el-select>
            </div>
          </div>

          <div class="split">
            <textarea
              v-model="draft.body"
              class="md-source"
              spellcheck="false"
              placeholder="在此编写 Markdown…"
            />
            <div class="md-preview" v-html="previewHtml" />
          </div>
        </template>
      </section>
    </div>

    <el-card v-if="effective" class="compose-card">
      <template #header>项目合成结果（将注入 Agent 的规约）</template>
      <el-table :data="effective.items" empty-text="暂无合成结果">
        <el-table-column prop="scope" label="生效层次" width="120" />
        <el-table-column prop="title" label="标题" width="160" />
        <el-table-column prop="body" label="正文" />
      </el-table>
      <p v-if="lastSnapshotId" class="snapshot-tip">最近快照：{{ lastSnapshotId }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { apiFetch } from '@/api/http'

interface GuidanceRow {
  id: string
  scope: string
  kind: string
  title: string
  body: string
  includeInAgent: boolean
  projectId?: string | null
}

interface ProjectRow {
  id: string
  name: string
}

const scope = ref('ALL')
const projectId = ref('')
const projects = ref<ProjectRow[]>([])
const rows = ref<GuidanceRow[]>([])
const selectedId = ref<string | null>(null)
const isCreating = ref(false)
const loading = ref(false)
const saving = ref(false)
const snapshotting = ref(false)
const error = ref('')
const effective = ref<{ items: Array<{ scope: string; title: string; body: string }> } | null>(null)
const lastSnapshotId = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const baseline = reactive({
  title: '',
  body: '',
  includeInAgent: true,
})
const draft = reactive({
  scope: 'TENANT',
  projectId: '',
  kind: 'CONVENTION',
  title: '',
  body: '',
  includeInAgent: true,
})

marked.setOptions({ gfm: true, breaks: true })

const hasEditor = computed(() => isCreating.value || selectedId.value != null)

const dirty = computed(() => {
  if (!hasEditor.value) return false
  return (
    draft.title !== baseline.title ||
    draft.body !== baseline.body ||
    draft.includeInAgent !== baseline.includeInAgent
  )
})

const canSave = computed(() => {
  if (!draft.title.trim()) return false
  if (draft.scope === 'PROJECT' && !draft.projectId) return false
  return isCreating.value || dirty.value
})

const previewHtml = computed(() => {
  const raw = marked.parse(draft.body || '') as string
  return DOMPurify.sanitize(raw)
})

function scopeLabel(value: string) {
  if (value === 'PLATFORM') return '平台'
  if (value === 'TENANT') return '公司'
  if (value === 'PROJECT') return '项目'
  return value
}

function kindLabel(value: string) {
  return value === 'CONVENTION' ? '规约' : '文档'
}

function scopeTagClass(value: string) {
  if (value === 'PLATFORM') return 'tag--orange'
  if (value === 'PROJECT') return 'tag--green'
  return 'tag--blue'
}

function kindTagClass(value: string) {
  return value === 'CONVENTION' ? 'tag--blue' : 'tag--green'
}

function rememberBaseline() {
  baseline.title = draft.title
  baseline.body = draft.body
  baseline.includeInAgent = draft.includeInAgent
}

function applyRow(row: GuidanceRow) {
  draft.scope = row.scope
  draft.projectId = row.projectId || ''
  draft.kind = row.kind
  draft.title = row.title
  draft.body = row.body || ''
  draft.includeInAgent = row.includeInAgent
  rememberBaseline()
}

async function ensureDiscard(): Promise<boolean> {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前编辑尚未保存，确定放弃修改？', '未保存的更改', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

async function selectDoc(row: GuidanceRow) {
  if (!(await ensureDiscard())) return
  isCreating.value = false
  selectedId.value = row.id
  applyRow(row)
}

async function startCreate() {
  if (!(await ensureDiscard())) return
  isCreating.value = true
  selectedId.value = null
  draft.scope = scope.value === 'ALL' ? 'TENANT' : scope.value
  draft.projectId = projectId.value || ''
  draft.kind = 'CONVENTION'
  draft.title = ''
  draft.body = ''
  draft.includeInAgent = true
  rememberBaseline()
}

function cancelEdit() {
  if (isCreating.value) {
    isCreating.value = false
    if (rows.value.length) {
      selectDoc(rows.value[0])
    } else {
      selectedId.value = null
      draft.title = ''
      draft.body = ''
      rememberBaseline()
    }
    return
  }
  const current = rows.value.find((row) => row.id === selectedId.value)
  if (current) applyRow(current)
}

function triggerImport() {
  fileInput.value?.click()
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.md') && !lower.endsWith('.markdown') && !lower.endsWith('.txt')) {
    ElMessage.warning('仅支持 .md / .markdown / .txt')
    return
  }
  try {
    const text = await file.text()
    startCreate()
    const bare = file.name.replace(/\.(md|markdown|txt)$/i, '')
    draft.title = bare || '导入文档'
    draft.body = text
    draft.kind = 'DOCUMENT'
    draft.includeInAgent = false
    rememberBaseline()
    baseline.title = ''
    baseline.body = ''
    ElMessage.success(`已导入 ${file.name}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入失败')
  }
}

async function loadProjects() {
  try {
    projects.value = await apiFetch<ProjectRow[]>('/api/v1/projects')
  } catch {
    projects.value = []
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    if (scope.value !== 'ALL') params.set('scope', scope.value)
    if (projectId.value) params.set('projectId', projectId.value)
    rows.value = await apiFetch<GuidanceRow[]>(`/api/v1/guidance?${params.toString()}`)
    if (isCreating.value) return
    if (selectedId.value) {
      const still = rows.value.find((row) => row.id === selectedId.value)
      if (still) {
        applyRow(still)
        return
      }
    }
    if (rows.value.length) {
      selectedId.value = rows.value[0].id
      applyRow(rows.value[0])
    } else {
      selectedId.value = null
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function compose() {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    effective.value = await apiFetch(`/api/v1/projects/${projectId.value}/conventions/effective`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '合成失败')
  }
}

async function snapshot() {
  if (!projectId.value) return
  snapshotting.value = true
  try {
    const snap = await apiFetch<{ id: string; items: Array<{ scope: string; title: string; body: string }> }>(
      `/api/v1/projects/${projectId.value}/conventions/snapshots`,
      { method: 'POST' },
    )
    lastSnapshotId.value = snap.id
    effective.value = { items: snap.items }
    ElMessage.success('已冻结合成快照')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '快照失败')
  } finally {
    snapshotting.value = false
  }
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    const payload = {
      scope: draft.scope,
      projectId: draft.scope === 'PROJECT' ? draft.projectId : null,
      kind: draft.kind,
      title: draft.title.trim(),
      body: draft.body,
      includeInAgent: draft.includeInAgent,
      enabled: true,
    }
    if (isCreating.value) {
      const created = await apiFetch<GuidanceRow>('/api/v1/guidance', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      ElMessage.success('已创建')
      isCreating.value = false
      selectedId.value = created.id
      await load()
    } else if (selectedId.value) {
      await apiFetch(`/api/v1/guidance/${selectedId.value}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
      ElMessage.success('已保存')
      await load()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.guidance {
  min-height: calc(100vh - var(--fp-header-height) - 40px);
  display: flex;
  flex-direction: column;
}

.ctrl-select {
  width: 132px;
}

.ctrl-select--wide {
  width: 180px;
}

.btn-icon {
  margin-right: 4px;
}

.hidden-file {
  display: none;
}

.block-alert {
  margin-bottom: 12px;
}

.workspace {
  flex: 1;
  min-height: 560px;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 12px;
}

.doc-list {
  background: var(--fp-color-surface);
  border: 1px solid var(--fp-color-border);
  border-radius: var(--fp-radius);
  padding: 12px;
  overflow: auto;
}

.doc-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
  color: var(--fp-color-ink);
}

.doc-list__count {
  color: var(--fp-color-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.doc-list__empty {
  padding: 24px 8px;
  text-align: center;
  color: var(--fp-color-muted);
  font-size: 13px;
}

.doc-card {
  width: 100%;
  text-align: left;
  border: 1px solid var(--fp-color-border);
  background: rgba(11, 16, 32, 0.55);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}

.doc-card:hover {
  border-color: rgba(76, 125, 255, 0.45);
}

.doc-card--active {
  background: var(--fp-color-primary-soft);
  border-color: var(--fp-color-primary);
}

.doc-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fp-color-ink);
  margin-bottom: 8px;
}

.doc-card__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
}

.tag--blue {
  background: var(--fp-color-primary-soft);
  color: #8fb0ff;
}

.tag--green {
  background: var(--fp-color-success-soft);
  color: var(--fp-color-success);
}

.tag--orange {
  background: var(--fp-color-warning-soft);
  color: var(--fp-color-warning);
}

.editor-panel {
  background: var(--fp-color-surface);
  border: 1px solid var(--fp-color-border);
  border-radius: var(--fp-radius);
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.editor-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--fp-color-border);
}

.editor-panel__left,
.editor-panel__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.editor-panel__label {
  font-weight: 600;
  color: var(--fp-color-ink);
}

.editor-empty {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--fp-color-muted);
  padding: 40px;
}

.editor-meta {
  padding: 12px;
  border-bottom: 1px solid var(--fp-color-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-input :deep(.el-input__wrapper) {
  font-size: 15px;
  font-weight: 600;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-select {
  width: 120px;
}

.meta-select--wide {
  width: 180px;
}

.split {
  flex: 1;
  min-height: 420px;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.md-source {
  width: 100%;
  height: 100%;
  min-height: 420px;
  border: 0;
  resize: none;
  outline: none;
  padding: 14px 16px;
  background: var(--fp-color-editor);
  color: #e2e8f0;
  font-family: var(--fp-font-mono);
  font-size: 13px;
  line-height: 1.55;
  border-right: 1px solid var(--fp-color-border);
}

.md-preview {
  padding: 16px 18px;
  overflow: auto;
  background: rgba(11, 16, 32, 0.55);
}

.compose-card {
  margin-top: 12px;
}

.snapshot-tip {
  margin: 12px 0 0;
  color: var(--fp-color-text-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .split {
    grid-template-columns: 1fr;
  }

  .md-source {
    min-height: 280px;
    border-right: 0;
    border-bottom: 1px solid var(--fp-color-border);
  }
}
</style>
