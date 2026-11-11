<template>
  <div class="page-stack workbench">
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">{{ headingCount ? `今天有 ${headingCount} 件事等你处理` : '今天没有需要你处理的事' }}</h1>
        <p class="page-header__subtitle">平台托管推进研发流程，你只需在评审和验收节点确认。</p>
      </div>
      <div class="page-header__actions">
        <el-input
          v-model="store.searchQuery"
          clearable
          style="width: 240px"
          placeholder="编号、需求或任务"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button :loading="store.loading" @click="store.load()">
          <el-icon class="btn-icon"><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" @click="router.push('/projects')">
          <el-icon class="btn-icon"><Plus /></el-icon>
          提出需求
        </el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-radio-group :model-value="store.scope" @change="onScopeChange">
        <el-radio-button value="waiting">
          待我处理
          <span v-if="store.waitingActionCount" class="tab-badge">{{ store.waitingActionCount }}</span>
        </el-radio-button>
        <el-radio-button value="mine">我的</el-radio-button>
        <el-radio-button value="member">项目内全部</el-radio-button>
      </el-radio-group>
      <span v-if="store.error" class="error">{{ store.error }}</span>
    </div>

    <div class="fp-hero-stats">
      <button
        v-for="tile in tiles"
        :key="tile.key"
        type="button"
        class="fp-hero-stat"
        :class="{ 'is-selected': store.selectedStat === tile.key }"
        @click="toggleStat(tile.key)"
      >
        <span>
          <el-icon><component :is="tile.icon" /></el-icon>
          {{ tile.label }}
        </span>
        <strong>{{ tile.value }}</strong>
      </button>
    </div>

    <section v-if="pipelineRequirements.length" class="fp-card" aria-label="进行中的研发">
      <div class="fp-card__head">
        <span class="fp-card__title">进行中的研发</span>
        <span class="fp-card__hint">每个需求都是一条可视化流水线，当前节点高亮，点击即可打开。</span>
      </div>
      <div class="fp-card__body fp-pipe-list">
        <button
          v-for="row in pipelineRequirements"
          :key="row.id"
          type="button"
          class="fp-pipe-card"
          :class="{ 'is-current': isCurrentCard(row) }"
          @click="openRequirement(row)"
        >
          <div class="fp-pipe-card__top">
            <div>
              <div class="fp-pipe-card__title">
                {{ row.title }}
                <span v-if="row.code" class="fp-code-chip fp-code-chip--req">{{ row.code }}</span>
              </div>
              <div class="fp-pipe-card__meta">
                <span>{{ row.projectName }}</span>
                <span v-if="workingBranch(row)" class="meta-branch">
                  工作分支
                  <BranchPill :name="workingBranch(row)" />
                </span>
                <span v-if="activeTask(row)">
                  <span class="fp-code-chip fp-code-chip--task">{{ activeTask(row)?.code || '任务' }}</span>
                  {{ taskStatusLabel(activeTask(row)?.status || '') }}
                </span>
              </div>
            </div>
            <span class="fp-status-tag" :class="toneClass(row.status)">{{ row.status }}</span>
          </div>
          <MiniFlow :status="String(row.status)" />
        </button>
      </div>
    </section>

    <section v-if="visibleInbox.length" class="fp-card inbox-card" aria-label="需要你处理">
      <div class="fp-card__head">
        <span class="fp-card__title">需要你处理</span>
        <span class="fp-card__count">{{ visibleInbox.length }} 项</span>
      </div>
      <div class="fp-inbox">
        <div v-for="item in visibleInbox" :key="item.kind + item.id" class="fp-inbox__row">
          <div class="fp-inbox__icon" :class="inboxTone(item.action)">
            <el-icon><component :is="inboxIcon(item.action)" /></el-icon>
          </div>
          <div>
            <div class="fp-inbox__title">
              <span>{{ item.title }}</span>
              <span v-if="item.code" class="fp-code-chip" :class="codeClass(item)">{{ item.code }}</span>
            </div>
            <div class="fp-inbox__summary">{{ item.summary }}</div>
          </div>
          <div class="fp-inbox__actions">
            <el-button :type="item.action === '去处理' ? 'default' : item.action === '去验收' ? 'warning' : 'primary'" @click="openInbox(item)">
              {{ item.action }}
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <el-card v-if="showLists" v-loading="store.loading" shadow="never">
      <template #header>
        <div class="card-head">
          <span>需求</span>
          <span class="muted">{{ store.filteredRequirements.length }}</span>
        </div>
      </template>
      <el-table :data="store.filteredRequirements" empty-text="这个筛选下没有需求">
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <div class="name-cell">
              <span v-if="row.code" class="fp-code-chip fp-code-chip--req">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="fp-status-tag" :class="toneClass(row.status)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务进度" width="160">
          <template #default="{ row }">
            <div class="fp-progress">
              <div class="fp-progress__bar" :class="progressClass(row)">
                <span :style="{ width: progressPercent(row.taskDone || 0, row.taskTotal || 0) + '%' }" />
              </div>
              <span>{{ progressText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '待验收'" link type="primary" @click="openRequirement(row)">去验收</el-button>
            <el-button v-else link type="primary" @click="openRequirement(row)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="showLists" v-loading="store.loading" shadow="never">
      <template #header>
        <div class="card-head">
          <span>任务</span>
          <span class="muted">{{ store.filteredTasks.length }}</span>
        </div>
      </template>
      <el-table :data="store.filteredTasks" empty-text="这个筛选下没有任务">
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <div class="name-cell">
              <span v-if="row.code" class="fp-code-chip fp-code-chip--task">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="fp-status-tag" :class="toneClass(row.status)">{{ taskStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentLabel" label="Agent" width="140" />
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'WAITING_APPROVAL'" link type="primary" @click="openTask(row)">去审核</el-button>
            <el-button v-else-if="row.status === 'WAITING_USER'" link type="primary" @click="openTask(row)">去处理</el-button>
            <el-button v-else link type="primary" @click="openTask(row)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, Plus, Refresh, Search, Stamp, VideoPlay, Warning } from '@element-plus/icons-vue'
import BranchPill from '@/components/BranchPill.vue'
import MiniFlow from '@/components/MiniFlow.vue'
import { isInProgressRequirement } from '@/domain/pipeline'
import { progressPercent, statusTone, taskStatusLabel } from '@/domain/labels'
import { useAuthStore } from '@/stores/auth'
import { useWorkbenchStore } from '@/stores/workbench'
import type { WorkRequirement, WorkTask } from '@/types'

const store = useWorkbenchStore()
const auth = useAuthStore()
const router = useRouter()

const headingCount = computed(() => store.waitingActionCount || store.inboxItems.length)

const tiles = computed(() => [
  { key: 'waiting', label: '待我处理', value: store.waitingActionCount || store.inboxItems.length, icon: Warning },
  { key: 'inProgress', label: '进行中的需求', value: inProgressAll.value.length, icon: VideoPlay },
  { key: 'approval', label: '待代码评审', value: store.counts.approval, icon: Stamp },
  { key: 'running', label: '执行中', value: store.counts.running, icon: CircleCheck },
])

const inProgressAll = computed(() =>
  store.visibleRequirements.filter((item) => isInProgressRequirement(String(item.status))),
)

const pipelineRequirements = computed(() => {
  const query = store.searchQuery.trim().toLowerCase()
  let list = inProgressAll.value
  if (query) {
    list = list.filter((item) =>
      [item.code, item.title, item.projectName, item.status].some((part) => (part || '').toLowerCase().includes(query)),
    )
  }
  if (store.selectedStat === 'approval') {
    const ids = new Set(store.filteredTasks.map((item) => item.requirementId))
    list = list.filter((item) => ids.has(item.id) || item.status === '开发中')
  } else if (store.selectedStat === 'running') {
    const ids = new Set(store.filteredTasks.map((item) => item.requirementId))
    list = list.filter((item) => ids.has(item.id))
  }
  return list
})

const visibleInbox = computed(() => {
  if (store.selectedStat === 'approval') {
    return store.inboxItems.filter((item) => item.action === '去审核')
  }
  if (store.selectedStat === 'running') return []
  if (store.selectedStat && store.selectedStat !== 'waiting') return store.inboxItems
  return store.inboxItems
})

const showLists = computed(() => {
  return Boolean(store.searchQuery.trim() || (store.selectedStat && store.selectedStat !== 'waiting' && store.selectedStat !== 'inProgress') || store.scope !== 'waiting')
})

function progressText(row: { id: string; taskDone?: number; taskTotal?: number }) {
  const progress = store.progress(row.id)
  return progress.total === 0 ? '尚未拆任务' : `${progress.done}/${progress.total}`
}

function progressClass(row: { id: string }) {
  const progress = store.progress(row.id)
  if (!progress.total || progress.done === 0) return 'is-empty'
  return progress.done >= progress.total ? '' : 'is-partial'
}

function toneClass(status: string) {
  const tone = statusTone(status)
  return tone ? `is-${tone}` : ''
}

function workingBranch(row: WorkRequirement) {
  const task = store.tasksFor(row.id).find((item) => item.branchName)
  return task?.branchName || ''
}

function activeTask(row: WorkRequirement): WorkTask | undefined {
  const tasks = store.tasksFor(row.id)
  return (
    tasks.find((item) => item.status === 'WAITING_APPROVAL') ||
    tasks.find((item) => item.status === 'RUNNING' || item.status === 'VERIFYING' || item.status === 'WAITING_USER') ||
    tasks.find((item) => item.status !== 'COMPLETED' && item.status !== 'CANCELLED')
  )
}

function isCurrentCard(row: WorkRequirement) {
  return row.status === '待验收' || activeTask(row)?.status === 'WAITING_APPROVAL'
}

function inboxIcon(action: string) {
  if (action === '去验收') return CircleCheck
  if (action === '去审核') return Stamp
  return Warning
}

function inboxTone(action: string) {
  if (action === '去验收') return 'is-warning'
  if (action === '去审核') return ''
  return ''
}

function codeClass(item: { kind: string; action: string }) {
  if (item.kind === 'task') return 'fp-code-chip--task'
  if (item.action === '去验收') return 'fp-code-chip--req'
  return 'fp-code-chip--req'
}

function openRequirement(row: { id: string }) {
  router.push(`/requirements/${row.id}`)
}

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}

function openInbox(item: { kind: string; id: string }) {
  if (item.kind === 'task') {
    router.push(`/tasks/${item.id}`)
    return
  }
  router.push(`/requirements/${item.id}`)
}

function toggleStat(key: string) {
  store.selectedStat = store.selectedStat === key ? '' : key
  if (key === 'waiting' && store.scope !== 'waiting') {
    void store.load('waiting')
  }
}

function onScopeChange(value: string | number | boolean | undefined) {
  store.selectedStat = ''
  void store.load(value as 'mine' | 'member' | 'waiting')
}

onMounted(() => {
  if (auth.isAuthenticated) {
    void store.load('waiting')
  }
})

watch(
  () => auth.isAuthenticated,
  (ok) => {
    if (ok) {
      void store.load('waiting')
    }
  },
)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: var(--fp-space-3);
  align-items: center;
  flex-wrap: wrap;
}
.error {
  color: var(--fp-color-danger);
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--fp-color-primary);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
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
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-icon {
  margin-right: 4px;
}
.meta-branch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
