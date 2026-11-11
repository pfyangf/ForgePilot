<template>
  <div v-if="detail">
    <div class="fp-crumb">
      <button type="button" @click="router.push('/')">返回</button>
      <span>/</span>
      <button type="button" @click="router.push('/')">工作台</button>
      <span>/</span>
      <span>需求详情</span>
      <span v-if="detail.requirement.code" class="fp-code-chip">{{ detail.requirement.code }}</span>
    </div>
    <el-alert v-if="error" class="block" type="error" show-icon :closable="false" :title="error" />
    <div class="page-header">
      <div class="page-header__titles">
        <div class="fp-title-row">
          <h1 class="page-header__title">{{ detail.requirement.title }}</h1>
          <span v-if="detail.requirement.code" class="fp-code-chip">{{ detail.requirement.code }}</span>
          <span class="fp-status-tag" :class="statusToneClass(detail.requirement.status)">{{ detail.requirement.status }}</span>
        </div>
        <div class="fp-meta-row">
          <span>{{ detail.requirement.projectName }}</span>
          <span>任务 {{ detail.requirement.taskDone }}/{{ detail.requirement.taskTotal }}</span>
          <span>当前环节 {{ gates.currentStage }}</span>
          <el-select v-model="manualStatus" size="small" style="width: 140px" @change="changeStatus">
            <el-option v-for="item in editableStatuses" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
      </div>
    </div>

    <el-card class="block" shadow="never">
      <template #header>
        <div class="row-between">
          <span>研发流程</span>
          <span class="hint" style="margin: 0">已完成环节可点击查看或修改。当前停在 {{ gates.currentStage }}。</span>
        </div>
      </template>
      <div v-for="group in flowGroups" :key="group.name" class="fp-flow-group">
        <div class="fp-flow-group__label">{{ group.name }}</div>
        <button
          v-for="stage in group.stages"
          :key="stage"
          type="button"
          class="fp-flow-chip"
          :class="flowChipClass(stage)"
          :disabled="!isStageReached(stage)"
          @click="focusStage(stage)"
        >
          {{ stage }}
        </button>
      </div>
    </el-card>

    <el-card class="block">
      <template #header>绑定检查（启动开发/修复任务前必须满足）</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="需求评审">{{ yesNo(gates.requirementReviewApproved) }}</el-descriptions-item>
        <el-descriptions-item label="设计文档">{{ yesNo(gates.designDocumentExists) }}</el-descriptions-item>
        <el-descriptions-item label="设计评审">{{ yesNo(gates.designReviewApproved) }}</el-descriptions-item>
        <el-descriptions-item label="默认编码工具">{{ yesNo(gates.codingToolBound) }}</el-descriptions-item>
        <el-descriptions-item label="默认模型">{{ yesNo(gates.modelBound) }}</el-descriptions-item>
        <el-descriptions-item label="关联仓库">{{ yesNo(gates.repoBound) }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="gates.blockers.length" class="hint">
        尚未满足：{{ gates.blockers.join('；') }}
        <el-button v-if="detail.requirement.projectId" link type="primary" @click="router.push(`/projects/${detail.requirement.projectId}`)">
          去项目里绑定
        </el-button>
      </p>
      <p v-else class="hint">开工门禁已满足。TEST / DOCS / REVIEW 任务不受此门禁限制。</p>
    </el-card>

    <el-card v-if="status === '待分析'" id="stage-需求分析" class="block">
      <template #header>
        <div class="row-between">
          <span>分析草稿与任务拆分建议</span>
          <div class="row" style="margin: 0">
            <el-button :loading="generating" @click="generateDraft('REQUIREMENT_ANALYSIS', 'draft')">AI 生成分析草稿</el-button>
          </div>
        </div>
      </template>
      <p>确认后进入「待需求评审」，不会直接改为「待开发」，也不会立刻创建任务。建议任务会保存下来，等设计评审通过后再创建。草稿可先改再确认。</p>
      <el-input v-model="draft" type="textarea" :rows="8" />
      <el-table :data="suggestions" empty-text="还没有拆分建议" class="block">
        <el-table-column prop="title" label="建议任务" />
        <el-table-column label="操作" width="120">
          <template #default="{ $index }">
            <el-button link type="danger" @click="suggestions.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row">
        <el-input v-model="suggestion" placeholder="补充一条任务" />
        <el-button @click="addSuggestion">添加</el-button>
        <el-button type="primary" :loading="saving" @click="confirmAnalysis">确认分析并提交评审</el-button>
      </div>
    </el-card>

    <el-card v-if="status === '待需求评审'" id="stage-需求评审" class="block">
      <template #header>
        <div class="row-between">
          <span>需求评审</span>
          <el-button :loading="generating" @click="generateDraft('REQUIREMENT_REVIEW', 'reviewComment')">AI 生成评审意见</el-button>
        </div>
      </template>
      <p>最近一次结论以本轮为准。通过后进入「设计中」；驳回后回到「待分析」，可改草稿再提交。AI 生成的意见可先修改再提交。</p>
      <pre class="draft-preview">{{ detail.draft }}</pre>
      <el-input v-model="reviewComment" type="textarea" :rows="3" placeholder="评审意见" class="block" />
      <div class="row">
        <el-button type="primary" :loading="saving" @click="reviewRequirement('APPROVED')">评审通过</el-button>
        <el-button type="danger" :loading="saving" @click="reviewRequirement('REJECTED')">驳回</el-button>
      </div>
      <el-table :data="detail.lifecycle.requirementReviews" empty-text="还没有评审记录" class="block">
        <el-table-column prop="decision" label="结论" width="120" />
        <el-table-column prop="comment" label="意见" />
        <el-table-column prop="createdAt" label="时间" width="220" />
      </el-table>
    </el-card>

    <el-card v-if="status === '设计中' || status === '待设计评审' || showDesignReadonly" id="stage-设计文档" class="block">
      <template #header>
        <div class="row-between">
          <span>设计文档</span>
          <div class="row" style="margin: 0">
            <el-button v-if="canEditDesign" :loading="generating" @click="generateDraft('DESIGN_REWRITE', 'designBody')">AI 改写</el-button>
            <el-button v-if="canEditDesign" @click="triggerImport">导入 Markdown</el-button>
            <el-button v-if="status === '设计中' || status === '待设计评审'" type="primary" :loading="saving" @click="saveDesign">
              保存新版本
            </el-button>
            <el-button v-if="status === '设计中'" type="success" :loading="saving" @click="submitDesignReview">
              提交设计评审
            </el-button>
          </div>
        </div>
      </template>
      <input ref="fileInput" class="hidden-file" type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" @change="onImportFile" />
      <el-input v-if="canEditDesign" v-model="designTitle" placeholder="设计文档标题" class="block" />
      <div v-if="canEditDesign" class="split">
        <textarea v-model="designBody" class="md-source" spellcheck="false" placeholder="在此编写页面布局、配色和交互说明…" />
        <div class="md-preview" v-html="designPreview" />
      </div>
      <div v-else>
        <h3>{{ currentDocument?.title }}</h3>
        <div class="md-preview md-preview--full" v-html="designPreview" />
      </div>
      <p v-if="currentDocument" class="hint">当前版本 v{{ currentDocument.versionNo }}{{ currentDocument.pendingReview ? '（待审）' : '' }}</p>
    </el-card>

    <el-card v-if="status === '待设计评审'" id="stage-设计文档评审" class="block">
      <template #header>
        <div class="row-between">
          <span>设计文档评审</span>
          <el-button :loading="generating" @click="generateDraft('DESIGN_REVIEW', 'designReviewComment')">AI 生成评审意见</el-button>
        </div>
      </template>
      <p>可多轮评审，以最近一次为准。通过后进入「待开发」；驳回后回到「设计中」。AI 生成的意见可先修改再提交。</p>
      <el-input v-model="designReviewComment" type="textarea" :rows="3" placeholder="评审意见" />
      <div class="row">
        <el-button type="primary" :loading="saving" @click="reviewDesign('APPROVED')">评审通过</el-button>
        <el-button type="danger" :loading="saving" @click="reviewDesign('REJECTED')">驳回</el-button>
      </div>
      <el-table :data="detail.lifecycle.designReviews" empty-text="还没有设计评审记录" class="block">
        <el-table-column prop="decision" label="结论" width="120" />
        <el-table-column prop="comment" label="意见" />
        <el-table-column prop="createdAt" label="时间" width="220" />
      </el-table>
    </el-card>

    <el-card id="stage-actions" class="block" shadow="never">
      <template #header>各环节动作</template>
      <p class="hint">每个节点都可以按场景使用 AI 生成、配置或修改。已完成环节可查看或再改。</p>
      <div class="fp-node-grid">
        <div v-for="node in nodeActions" :key="node.stage" class="fp-node-item" :class="{ 'is-current': node.stage === gates.currentStage }">
          <div class="fp-node-item__name">{{ node.stage }}</div>
          <div class="row" style="margin: 0">
            <el-button v-for="action in node.actions" :key="action.label" link type="primary" @click="runNodeAction(action)">
              {{ action.label }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-if="showTasks" id="stage-开发" class="block">
      <template #header>
        <div class="row-between">
          <span>需求下的任务</span>
          <el-button type="primary" link @click="openCreateTask">新建任务</el-button>
        </div>
      </template>
      <p>执行、验证和代码审核在任务详情里看。创建任务会冻结当前仓库集合。启动 IMPLEMENT / BUGFIX 前须满足上面的开工门禁。</p>
      <div v-if="detail.suggestedTaskTitles.length" class="hint">
        分析阶段的建议任务：
        <el-button
          v-for="title in detail.suggestedTaskTitles"
          :key="title"
          size="small"
          @click="createFromSuggestion(title)"
        >
          创建「{{ title }}」
        </el-button>
      </div>
      <el-table :data="detail.tasks">
        <el-table-column label="名称" min-width="240">
          <template #default="{ row }">
            <div class="name-cell">
              <span v-if="row.code" class="fp-code-chip">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" label="类型" width="120" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <span class="fp-status-tag" :class="statusToneClass(row.status)">{{ taskStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="branchName" label="工作分支" min-width="200" />
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTask(row)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="showDefects" id="stage-缺陷登记" class="block">
      <template #header>
        <div class="row-between">
          <span>缺陷</span>
          <div class="row" style="margin: 0">
            <el-button :loading="generating" @click="generateDraft('DEFECT_DESCRIPTION', 'newDefectDesc')">AI 生成缺陷描述</el-button>
            <el-button type="primary" link @click="showDefectDialog = true">登记缺陷</el-button>
          </div>
        </div>
      </template>
      <p>缺陷挂在本需求下。BUGFIX 任务必须关联缺陷；启动后进入 IN_FIX，任务完成后为 FIXED，回归验证通过后为 VERIFIED，关闭后结单。OPEN / IN_FIX 缺陷会阻止验收。</p>
      <el-table :data="detail.lifecycle.defects" empty-text="还没有缺陷">
        <el-table-column label="名称" min-width="240">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="fp-code-chip">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-if="row.status === 'FIXED'" link type="primary" @click="defectAction(row.id, 'verify')">回归通过</el-button>
            <el-button v-if="row.status === 'VERIFIED' || row.status === 'FIXED'" link @click="defectAction(row.id, 'close')">关闭</el-button>
            <el-button v-if="row.status === 'OPEN' || row.status === 'IN_FIX'" link type="danger" @click="defectAction(row.id, 'reject')">驳回</el-button>
            <el-button v-if="row.status === 'OPEN' || row.status === 'FIXED'" link @click="createBugfix(row)">建修复任务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="status === '待验收'" id="stage-验收测试" class="block">
      <template #header>
        <div class="row-between">
          <span>验收测试</span>
          <el-button :loading="generating" @click="generateDraft('ACCEPTANCE', 'acceptComment')">AI 生成验收清单</el-button>
        </div>
      </template>
      <p>有未关闭的 OPEN / IN_FIX 缺陷时不能验收通过。本需求下缺陷均已关闭后，可先用 AI 生成验收清单，修改后再提交结论。草稿可编辑。</p>
      <el-input v-model="acceptComment" type="textarea" :rows="8" placeholder="验收清单 / 意见" />
      <div class="row">
        <el-button type="primary" :loading="saving" @click="accept(true)">验收通过</el-button>
        <el-button :loading="saving" @click="accept(false)">验收退回</el-button>
      </div>
    </el-card>

    <el-card v-if="status === '已完成' || status === '已发布'" id="stage-发布" class="block">
      <template #header>
        <div class="row-between">
          <span>发布</span>
          <el-button v-if="status === '已完成'" :loading="generating" @click="generateDraft('RELEASE_NOTES', 'releaseNotes')">AI 生成发布说明</el-button>
        </div>
      </template>
      <p v-if="status === '已完成'">验收通过后才能发布。发布时选择要把工作分支合并进去的目标分支。代码审核通过不会自动合并；合并只发生在发布确认之后。若合并失败，需求不会标记为已发布。</p>
      <div v-if="status === '已完成'" class="release-form">
        <div class="row">
          <el-input v-model="releaseVersion" placeholder="版本号，例如 1.0.0" style="width: 200px" />
          <el-input v-model="releaseNotes" type="textarea" :rows="3" placeholder="发布说明" />
        </div>
        <div v-if="releasePlanError" class="hint">{{ releasePlanError }}</div>
        <div v-if="!releasePlan.repositories.length" class="hint">当前没有需要合并的已推送工作分支，可以直接发布记录。</div>
        <div v-for="repo in releasePlan.repositories" :key="repo.projectRepositoryId" class="repo-bind">
          <div class="repo-bind__name">{{ repo.displayName || repo.localDirectoryName }}</div>
          <div class="hint">工作分支：{{ repo.sourceBranches.join('、') || '—' }}</div>
          <el-select
            v-model="releaseTargets[repo.projectRepositoryId]"
            filterable
            allow-create
            default-first-option
            placeholder="选择要合并的目标分支"
            style="width: 260px"
          >
            <el-option v-for="name in repoBranchOptions(repo)" :key="name" :label="name" :value="name" />
          </el-select>
          <p v-if="repo.branchesError" class="hint">{{ repo.branchesError }}</p>
        </div>
        <el-button type="primary" :loading="saving" @click="publish">确认发布并合并</el-button>
      </div>
      <el-table :data="detail.lifecycle.releases" empty-text="还没有发布记录" class="block">
        <el-table-column prop="version" label="版本" width="140" />
        <el-table-column prop="notes" label="说明" />
        <el-table-column label="目标分支" min-width="220">
          <template #default="{ row }">
            <div v-if="row.targets && row.targets.length">
              <div v-for="(target, index) in row.targets" :key="index">
                {{ target.sourceBranch }} → {{ target.targetBranch }}
                <span v-if="target.mergeSha"> · {{ target.mergeSha.slice(0, 8) }}</span>
              </div>
            </div>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="releasedAt" label="时间" width="220" />
      </el-table>
    </el-card>

    <el-dialog v-model="showCreate" title="新建任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="newTaskTitle" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newTaskType" style="width: 100%">
            <el-option label="开发 IMPLEMENT" value="IMPLEMENT" />
            <el-option label="缺陷修复 BUGFIX" value="BUGFIX" />
            <el-option label="测试 TEST" value="TEST" />
            <el-option label="文档 DOCS" value="DOCS" />
            <el-option label="代码审查 REVIEW" value="REVIEW" />
            <el-option label="重构 REFACTOR" value="REFACTOR" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="newTaskType === 'BUGFIX'" label="关联缺陷">
          <el-select v-model="newTaskDefectId" style="width: 100%" placeholder="必须选择缺陷">
            <el-option v-for="item in openDefects" :key="item.id" :label="`${item.code} ${item.title}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作分支">
          <el-input v-model="newWorkingBranch" placeholder="默认按需求编号生成，可修改" />
          <p class="hint">默认根据需求号生成，例如 fp/REQ-DEMO99-3。若已被占用，会带上任务号后缀。</p>
        </el-form-item>
        <div v-for="repo in repoBindings" :key="repo.projectRepositoryId" class="repo-bind">
          <el-checkbox v-model="repo.included">{{ repo.displayName || repo.localDirectoryName }}</el-checkbox>
          <div class="row">
            <el-select
              v-model="repo.sourceBranch"
              filterable
              allow-create
              default-first-option
              placeholder="源分支"
              style="width: 200px"
            >
              <el-option v-for="name in repo.remoteBranches" :key="name" :label="name" :value="name" />
            </el-select>
            <el-input v-model="repo.workingBranch" placeholder="新建分支名称" />
          </div>
          <p v-if="repo.branchesError" class="hint">{{ repo.branchesError }}</p>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTask">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDefectDialog" title="登记缺陷" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="newDefectTitle" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newDefectDesc" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="newDefectSeverity" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDefectDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createDefect">登记</el-button>
      </template>
    </el-dialog>
  </div>
  <el-empty v-else-if="!loading" description="没有这条需求">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { apiFetch } from '@/api/http'
import { statusTone, taskStatusLabel } from '@/domain/labels'

marked.setOptions({ gfm: true, breaks: true })

const stages = [
  '提出需求',
  '需求分析',
  '需求评审',
  '设计文档',
  '设计文档评审',
  'AI Coding 绑定',
  '仓库绑定',
  '开发',
  '代码评审',
  '测试',
  '缺陷登记',
  '缺陷修改',
  '回归测试',
  '验收测试',
  '发布',
]

const editableStatuses = ['待分析', '待需求评审', '设计中', '待设计评审', '待开发', '开发中', '测试中', '待验收', '已完成', '已取消']

interface ReviewRow {
  id: string
  decision: string
  comment: string
  createdAt: string
}

interface DocumentRow {
  id: string
  title: string
  body: string
  versionNo: number
  current: boolean
  pendingReview: boolean
}

interface DefectRow {
  id: string
  code: string
  title: string
  severity: string
  status: string
}

interface GateRow {
  requirementReviewApproved: boolean
  designDocumentExists: boolean
  designReviewApproved: boolean
  codingToolBound: boolean
  modelBound: boolean
  repoBound: boolean
  canStartImplementOrBugfix: boolean
  blockers: string[]
  currentStageIndex: number
  currentStage: string
}

interface RequirementDetail {
  requirement: {
    id: string
    code: string
    title: string
    status: string
    projectId: string
    projectName: string
    taskDone: number
    taskTotal: number
  }
  draft: string
  confirmed: boolean
  suggestedTaskTitles: string[]
  tasks: Array<{ id: string; code?: string; title: string; status: string; taskType?: string; branchName: string }>
  lifecycle: {
    requirementReviews: ReviewRow[]
    documents: DocumentRow[]
    designReviews: ReviewRow[]
    defects: DefectRow[]
    releases: Array<{
      id: string
      version: string
      notes: string
      releasedAt: string
      targets?: Array<{ sourceBranch: string; targetBranch: string; mergeSha?: string }>
    }>
    gates: GateRow
  }
}

interface ReleaseRepoPlan {
  projectRepositoryId: string
  displayName: string
  localDirectoryName: string
  cloneUrl: string
  defaultTargetBranch: string
  sourceBranches: string[]
  remoteBranches: string[]
  branchesError?: string | null
}

interface RepoBinding {
  projectRepositoryId: string
  displayName: string
  localDirectoryName: string
  sourceBranch: string
  workingBranch: string
  remoteBranches: string[]
  branchesError: string
  included: boolean
}

const props = defineProps<{ id: string }>()
const router = useRouter()
const detail = ref<RequirementDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const draft = ref('')
const suggestion = ref('')
const suggestions = ref<Array<{ title: string }>>([])
const reviewComment = ref('')
const designTitle = ref('页面设计说明')
const designBody = ref('')
const designReviewComment = ref('')
const acceptComment = ref('')
const releaseVersion = ref('')
const releaseNotes = ref('')
const showCreate = ref(false)
const showDefectDialog = ref(false)
const newTaskTitle = ref('')
const newTaskType = ref('IMPLEMENT')
const newTaskDefectId = ref('')
const newDefectTitle = ref('')
const newDefectDesc = ref('')
const newDefectSeverity = ref('MEDIUM')
const manualStatus = ref('待分析')
const fileInput = ref<HTMLInputElement | null>(null)
const generating = ref(false)
const generateHint = ref('')
const newWorkingBranch = ref('')
const repoBindings = ref<RepoBinding[]>([])
const releasePlan = ref<{ repositories: ReleaseRepoPlan[] }>({ repositories: [] })
const releaseTargets = ref<Record<string, string>>({})
const releasePlanError = ref('')
const testPoints = ref('')

const flowGroups = [
  { name: '需求准备', stages: ['提出需求', '需求分析', '需求评审'] },
  { name: '设计与绑定', stages: ['设计文档', '设计文档评审', 'AI Coding 绑定', '仓库绑定'] },
  { name: '研发执行', stages: ['开发', '代码评审', '测试'] },
  { name: '缺陷与交付', stages: ['缺陷登记', '缺陷修改', '回归测试', '验收测试', '发布'] },
]

const status = computed(() => detail.value?.requirement.status || '')
const gates = computed<GateRow>(
  () =>
    detail.value?.lifecycle.gates || {
      requirementReviewApproved: false,
      designDocumentExists: false,
      designReviewApproved: false,
      codingToolBound: false,
      modelBound: false,
      repoBound: false,
      canStartImplementOrBugfix: false,
      blockers: [],
      currentStageIndex: 0,
      currentStage: stages[0],
    },
)
const currentDocument = computed(() => detail.value?.lifecycle.documents.find((item) => item.current) || null)
const canEditDesign = computed(() => status.value === '设计中' || status.value === '待设计评审')
const showDesignReadonly = computed(
  () => gates.value.designDocumentExists && !['待分析', '待需求评审', '设计中', '待设计评审'].includes(status.value),
)
const showTasks = computed(() => status.value !== '待分析')
const showDefects = computed(() => !['待分析', '待需求评审', '设计中', '待设计评审'].includes(status.value))
const openDefects = computed(() =>
  (detail.value?.lifecycle.defects || []).filter((item) => !['CLOSED', 'REJECTED'].includes(item.status)),
)
const designPreview = computed(() => DOMPurify.sanitize(marked.parse(designBody.value || currentDocument.value?.body || '') as string))

const nodeActions = computed(() => [
  { stage: '提出需求', actions: [{ label: '查看', type: 'focus', stage: '提出需求' }] },
  { stage: '需求分析', actions: [{ label: 'AI 分析草稿', type: 'ai', scene: 'REQUIREMENT_ANALYSIS', target: 'draft' }, { label: '修改', type: 'focus', stage: '需求分析' }] },
  { stage: '需求评审', actions: [{ label: 'AI 评审意见', type: 'ai', scene: 'REQUIREMENT_REVIEW', target: 'reviewComment' }, { label: '查看', type: 'focus', stage: '需求评审' }] },
  { stage: '设计文档', actions: [{ label: 'AI 改写', type: 'ai', scene: 'DESIGN_REWRITE', target: 'designBody' }, { label: '导入', type: 'import' }, { label: '编辑', type: 'focus', stage: '设计文档' }] },
  { stage: '设计文档评审', actions: [{ label: 'AI 评审意见', type: 'ai', scene: 'DESIGN_REVIEW', target: 'designReviewComment' }, { label: '查看', type: 'focus', stage: '设计文档评审' }] },
  { stage: 'AI Coding 绑定', actions: [{ label: '配置', type: 'project' }] },
  { stage: '仓库绑定', actions: [{ label: '配置仓库', type: 'project' }] },
  { stage: '开发', actions: [{ label: '打开任务', type: 'focus', stage: '开发' }] },
  { stage: '代码评审', actions: [{ label: '打开待审任务', type: 'review-task' }] },
  { stage: '测试', actions: [{ label: 'AI 测试要点', type: 'ai', scene: 'TEST_POINTS', target: 'testPoints' }] },
  { stage: '缺陷登记', actions: [{ label: 'AI 缺陷描述', type: 'ai', scene: 'DEFECT_DESCRIPTION', target: 'newDefectDesc' }, { label: '登记', type: 'defect' }] },
  { stage: '缺陷修改', actions: [{ label: '查看缺陷', type: 'focus', stage: '缺陷登记' }] },
  { stage: '回归测试', actions: [{ label: '查看缺陷', type: 'focus', stage: '缺陷登记' }] },
  { stage: '验收测试', actions: [{ label: 'AI 验收清单', type: 'ai', scene: 'ACCEPTANCE', target: 'acceptComment' }, { label: '修改', type: 'focus', stage: '验收测试' }] },
  { stage: '发布', actions: [{ label: 'AI 发布说明', type: 'ai', scene: 'RELEASE_NOTES', target: 'releaseNotes' }, { label: '配置版本', type: 'focus', stage: '发布' }] },
])

function statusToneClass(value: string) {
  const tone = statusTone(value)
  return tone ? `is-${tone}` : ''
}

function isStageReached(stage: string) {
  return stages.indexOf(stage) <= (gates.value.currentStageIndex || 0)
}

function flowChipClass(stage: string) {
  if (stage === gates.value.currentStage) return 'is-current'
  if (isStageReached(stage)) return 'is-done'
  return ''
}

function focusStage(stage: string) {
  const id = stage === '缺陷修改' || stage === '回归测试' ? 'stage-缺陷登记' : `stage-${stage}`
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function repoBranchOptions(repo: ReleaseRepoPlan) {
  const names = [repo.defaultTargetBranch, ...(repo.remoteBranches || [])].filter(Boolean)
  return Array.from(new Set(names))
}

type DraftTarget = 'draft' | 'reviewComment' | 'designBody' | 'designReviewComment' | 'acceptComment' | 'releaseNotes' | 'newDefectDesc' | 'testPoints'

async function generateDraft(scene: string, target: DraftTarget) {
  if (!detail.value) return
  generating.value = true
  error.value = ''
  try {
    const result = await apiFetch<{ text: string }>('/api/v1/ai/drafts', {
      method: 'POST',
      body: JSON.stringify({
        scene,
        projectId: detail.value.requirement.projectId,
        requirementId: props.id,
        hint: generateHint.value || undefined,
      }),
    })
    const map: Record<DraftTarget, { set: (value: string) => void }> = {
      draft: { set: (value) => (draft.value = value) },
      reviewComment: { set: (value) => (reviewComment.value = value) },
      designBody: { set: (value) => (designBody.value = value) },
      designReviewComment: { set: (value) => (designReviewComment.value = value) },
      acceptComment: { set: (value) => (acceptComment.value = value) },
      releaseNotes: { set: (value) => (releaseNotes.value = value) },
      newDefectDesc: { set: (value) => (newDefectDesc.value = value) },
      testPoints: { set: (value) => (testPoints.value = value) },
    }
    map[target].set(result.text)
    if (target === 'newDefectDesc') showDefectDialog.value = true
    ElMessage.success('已生成草稿，请确认后再提交')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成失败'
    ElMessage.error(error.value)
  } finally {
    generating.value = false
  }
}

function runNodeAction(action: { label: string; type: string; stage?: string; scene?: string; target?: string }) {
  if (action.type === 'ai' && action.scene && action.target) {
    void generateDraft(action.scene, action.target as DraftTarget)
    if (action.scene === 'ACCEPTANCE') focusStage('验收测试')
    if (action.scene === 'RELEASE_NOTES') focusStage('发布')
    return
  }
  if (action.type === 'import') {
    triggerImport()
    return
  }
  if (action.type === 'project' && detail.value?.requirement.projectId) {
    router.push(`/projects/${detail.value.requirement.projectId}`)
    return
  }
  if (action.type === 'defect') {
    showDefectDialog.value = true
    return
  }
  if (action.type === 'review-task') {
    const pending = detail.value?.tasks.find((item) => item.status === 'WAITING_APPROVAL')
    if (pending) {
      openTask(pending)
      return
    }
    focusStage('开发')
    return
  }
  if (action.stage) focusStage(action.stage)
}

async function loadReleasePlan() {
  releasePlanError.value = ''
  try {
    const plan = await apiFetch<{ repositories: ReleaseRepoPlan[] }>(`/api/v1/requirements/${props.id}/release-plan`)
    releasePlan.value = plan
    const next: Record<string, string> = {}
    for (const repo of plan.repositories) {
      next[repo.projectRepositoryId] = releaseTargets.value[repo.projectRepositoryId] || repo.defaultTargetBranch || 'main'
    }
    releaseTargets.value = next
  } catch (err) {
    releasePlan.value = { repositories: [] }
    releasePlanError.value = err instanceof Error ? err.message : '无法加载发布合并计划'
  }
}

async function loadTaskDefaults() {
  try {
    const defaults = await apiFetch<{
      workingBranch: string
      repositories: Array<{
        projectRepositoryId: string
        displayName: string
        localDirectoryName: string
        defaultBranch: string
        sourceBranch: string
        workingBranch: string
      }>
    }>(`/api/v1/requirements/${props.id}/task-defaults`)
    newWorkingBranch.value = defaults.workingBranch
    const bindings: RepoBinding[] = []
    for (const repo of defaults.repositories) {
      let remoteBranches: string[] = [repo.defaultBranch, repo.sourceBranch].filter(Boolean)
      let branchesError = ''
      try {
        const listed = await apiFetch<{ branches: Array<{ name: string }>; error?: string }>(
          `/api/v1/projects/${detail.value?.requirement.projectId}/repositories/${repo.projectRepositoryId}/branches`,
        )
        remoteBranches = Array.from(new Set((listed.branches || []).map((item) => item.name).concat(remoteBranches)))
        branchesError = listed.error || ''
      } catch (err) {
        branchesError = err instanceof Error ? err.message : '无法列出远端分支，请手工填写分支名'
      }
      bindings.push({
        projectRepositoryId: repo.projectRepositoryId,
        displayName: repo.displayName,
        localDirectoryName: repo.localDirectoryName,
        sourceBranch: repo.sourceBranch || repo.defaultBranch || 'main',
        workingBranch: repo.workingBranch || defaults.workingBranch,
        remoteBranches,
        branchesError,
        included: true,
      })
    }
    repoBindings.value = bindings
  } catch (err) {
    repoBindings.value = []
    ElMessage.error(err instanceof Error ? err.message : '无法加载分支默认值')
  }
}

onMounted(async () => {
  await reload()
})

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const data = await apiFetch<RequirementDetail>(`/api/v1/requirements/${props.id}`)
    detail.value = data
    draft.value = data.draft || ''
    suggestions.value = (data.suggestedTaskTitles || []).map((title) => ({ title }))
    manualStatus.value = data.requirement.status
    const current = data.lifecycle.documents.find((item) => item.current)
    if (current) {
      designTitle.value = current.title
      designBody.value = current.body
    }
    if (data.requirement.status === '已完成' || data.requirement.status === '已发布') {
      await loadReleasePlan()
    }
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function yesNo(value: boolean) {
  return value ? '已满足' : '未满足'
}

function addSuggestion() {
  if (!suggestion.value.trim()) return
  suggestions.value.push({ title: suggestion.value.trim() })
  suggestion.value = ''
}

async function confirmAnalysis() {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/confirm-analysis`, {
      method: 'POST',
      body: JSON.stringify({
        draft: draft.value,
        taskTitles: suggestions.value.map((item) => item.title),
      }),
    })
    ElMessage.success('已提交需求评审')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '确认失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function reviewRequirement(decision: 'APPROVED' | 'REJECTED') {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/reviews`, {
      method: 'POST',
      body: JSON.stringify({ decision, comment: reviewComment.value }),
    })
    ElMessage.success(decision === 'APPROVED' ? '需求评审已通过' : '已驳回，需求回到待分析')
    reviewComment.value = ''
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '评审失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function saveDesign() {
  if (!designTitle.value.trim()) {
    ElMessage.warning('请填写设计文档标题')
    return
  }
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/documents`, {
      method: 'POST',
      body: JSON.stringify({ title: designTitle.value.trim(), body: designBody.value }),
    })
    ElMessage.success('设计文档已保存为新版本')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

function triggerImport() {
  fileInput.value?.click()
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  designBody.value = await file.text()
  if (!designTitle.value.trim()) {
    designTitle.value = file.name.replace(/\.(md|markdown|txt)$/i, '')
  }
  ElMessage.success('已导入，确认后请保存新版本')
}

async function submitDesignReview() {
  const doc = currentDocument.value
  if (!doc) {
    ElMessage.warning('请先保存设计文档')
    return
  }
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/documents/${doc.id}/submit-review`, { method: 'POST' })
    ElMessage.success('已提交设计评审')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '提交失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function reviewDesign(decision: 'APPROVED' | 'REJECTED') {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/design-reviews`, {
      method: 'POST',
      body: JSON.stringify({ decision, comment: designReviewComment.value }),
    })
    ElMessage.success(decision === 'APPROVED' ? '设计评审已通过' : '已驳回，回到设计中')
    designReviewComment.value = ''
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '评审失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

function openCreateTask() {
  newTaskTitle.value = ''
  newTaskType.value = 'IMPLEMENT'
  newTaskDefectId.value = ''
  showCreate.value = true
  void loadTaskDefaults()
}

async function createFromSuggestion(title: string) {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/tasks`, {
      method: 'POST',
      body: JSON.stringify({ title, taskType: 'IMPLEMENT', priority: 'MEDIUM' }),
    })
    ElMessage.success('任务已创建')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function createTask() {
  if (!newTaskTitle.value.trim()) {
    ElMessage.warning('请填写任务标题')
    return
  }
  if (newTaskType.value === 'BUGFIX' && !newTaskDefectId.value) {
    ElMessage.warning('BUGFIX 必须关联缺陷')
    return
  }
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/tasks`, {
      method: 'POST',
      body: JSON.stringify({
        title: newTaskTitle.value.trim(),
        taskType: newTaskType.value,
        priority: 'MEDIUM',
        defectId: newTaskType.value === 'BUGFIX' ? newTaskDefectId.value : undefined,
        workingBranch: newWorkingBranch.value || undefined,
        repositories: repoBindings.value
          .filter((item) => item.included)
          .map((item) => ({
            projectRepositoryId: item.projectRepositoryId,
            sourceBranch: item.sourceBranch,
            workingBranch: item.workingBranch || newWorkingBranch.value,
          })),
      }),
    })
    showCreate.value = false
    ElMessage.success('任务已创建并冻结仓库集合')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

function createBugfix(row: DefectRow) {
  newTaskTitle.value = `修复 ${row.code}`
  newTaskType.value = 'BUGFIX'
  newTaskDefectId.value = row.id
  showCreate.value = true
  void loadTaskDefaults()
}

async function createDefect() {
  if (!newDefectTitle.value.trim()) {
    ElMessage.warning('请填写缺陷标题')
    return
  }
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/defects`, {
      method: 'POST',
      body: JSON.stringify({
        title: newDefectTitle.value.trim(),
        description: newDefectDesc.value,
        severity: newDefectSeverity.value,
      }),
    })
    showDefectDialog.value = false
    newDefectTitle.value = ''
    newDefectDesc.value = ''
    newDefectSeverity.value = 'MEDIUM'
    ElMessage.success('缺陷已登记')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登记失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function defectAction(id: string, action: 'verify' | 'close' | 'reject') {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/defects/${id}/${action}`, { method: 'POST', body: JSON.stringify({ note: '' }) })
    ElMessage.success('已更新缺陷状态')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function accept(accepted: boolean) {
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/accept`, {
      method: 'POST',
      body: JSON.stringify({ accepted, comment: acceptComment.value }),
    })
    ElMessage.success(accepted ? '验收通过' : '已退回')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验收失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!releaseVersion.value.trim() || !detail.value) {
    ElMessage.warning('请填写版本号')
    return
  }
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/projects/${detail.value.requirement.projectId}/releases`, {
      method: 'POST',
      body: JSON.stringify({
        version: releaseVersion.value.trim(),
        notes: releaseNotes.value,
        requirementIds: [props.id],
        targets: releasePlan.value.repositories.map((repo) => ({
          projectRepositoryId: repo.projectRepositoryId,
          targetBranch: releaseTargets.value[repo.projectRepositoryId] || repo.defaultTargetBranch || 'main',
        })),
      }),
    })
    ElMessage.success('已发布')
    await reload()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '发布失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

async function changeStatus(value: string) {
  if (!detail.value || value === detail.value.requirement.status) return
  saving.value = true
  error.value = ''
  try {
    await apiFetch(`/api/v1/requirements/${props.id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status: value, note: '人工调整' }),
    })
    ElMessage.success('状态已更新')
    await reload()
  } catch (err) {
    manualStatus.value = detail.value.requirement.status
    error.value = err instanceof Error ? err.message : '状态更新失败'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
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
}
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.status-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.hint {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
}
.steps-wrap {
  overflow-x: auto;
  padding-bottom: 8px;
}
.steps-wrap :deep(.el-step__title) {
  font-size: 12px;
  line-height: 1.3;
}
.draft-preview {
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
}
.split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  min-height: 280px;
  margin-top: 12px;
}
.md-source {
  width: 100%;
  min-height: 280px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  resize: vertical;
}
.md-preview {
  min-height: 280px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: auto;
  background: #fff;
}
.md-preview--full {
  min-height: 160px;
}
.hidden-file {
  display: none;
}
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.repo-bind {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--fp-color-border);
  border-radius: var(--fp-radius);
}
.repo-bind__name {
  font-weight: 600;
  margin-bottom: 6px;
}
.release-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
