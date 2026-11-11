<template>
  <div class="page-stack workbench">
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">工作台</h1>
        <p class="page-header__subtitle">待评审、待验收和等待你处理的任务会集中在这里。</p>
      </div>
      <div class="page-header__actions">
        <el-input
          v-model="store.searchQuery"
          clearable
          style="width: 240px"
          placeholder="搜索需求或任务"
        />
        <el-tooltip content="打开页面本身不写操作记录。列表按当前登录用户的租户与项目成员关系加载。" placement="bottom">
          <el-button circle>
            <span class="tip-icon">i</span>
          </el-button>
        </el-tooltip>
        <el-button :loading="store.loading" @click="store.load()">刷新</el-button>
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

    <section v-if="store.scope === 'waiting' && store.inboxItems.length" class="inbox-card">
      <div class="inbox-card__head">需要你处理 {{ store.inboxItems.length }} 项</div>
      <div class="fp-inbox">
        <div v-for="item in store.inboxItems" :key="item.kind + item.id" class="fp-inbox__row">
          <div>
            <div class="fp-inbox__title">
              <span class="fp-code-chip" v-if="item.code">{{ item.code }}</span>
              <span>{{ item.title }}</span>
            </div>
            <div class="fp-inbox__summary">{{ item.summary }}</div>
          </div>
          <div class="fp-inbox__actions">
            <el-tag v-if="item.action === '去验收'" size="small" type="warning" effect="plain">待验收</el-tag>
            <el-tag v-else-if="item.action === '去审核'" size="small" type="primary" effect="plain">待审核</el-tag>
            <el-tag v-else size="small" effect="plain">待处理</el-tag>
            <el-button
              :type="item.action === '去处理' ? 'default' : 'primary'"
              :plain="item.action === '去验收'"
              @click="openInbox(item)"
            >
              {{ item.action }}
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <div class="fp-stat-tiles">
      <button
        v-for="tile in tiles"
        :key="tile.key"
        type="button"
        class="fp-stat-tile"
        :class="{ 'is-selected': store.selectedStat === tile.key }"
        @click="toggleStat(tile.key)"
      >
        <div class="fp-stat-tile__label">{{ tile.label }}</div>
        <div class="fp-stat-tile__value">{{ tile.value }}</div>
      </button>
    </div>

    <el-card v-loading="store.loading" shadow="never">
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
              <span v-if="row.code" class="fp-code-chip">{{ row.code }}</span>
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

    <el-card v-loading="store.loading" shadow="never">
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
              <span v-if="row.code" class="fp-code-chip">{{ row.code }}</span>
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
import { useAuthStore } from '@/stores/auth'
import { useWorkbenchStore } from '@/stores/workbench'
import { progressPercent, statusTone, taskStatusLabel } from '@/domain/labels'

const store = useWorkbenchStore()
const auth = useAuthStore()
const router = useRouter()

const tiles = computed(() => [
  { key: 'requirements', label: '需求', value: store.counts.requirements },
  { key: 'tasks', label: '任务', value: store.counts.tasks },
  { key: 'running', label: '执行中', value: store.counts.running },
  { key: 'verifying', label: '验证中', value: store.counts.verifying },
  { key: 'approval', label: '待审核', value: store.counts.approval },
  { key: 'waitingUser', label: '待处理', value: store.counts.waitingUser },
])

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
.inbox-card__head {
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--fp-color-ink);
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
.tip-icon {
  font-style: italic;
  font-weight: 700;
}
</style>
