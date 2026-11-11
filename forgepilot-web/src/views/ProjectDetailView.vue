<template>
  <div v-if="store.detail">
    <div class="fp-crumb">
      <button type="button" @click="router.push('/projects')">项目</button>
      <span>/</span>
      <span>{{ store.detail.project.name }}</span>
    </div>
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">{{ store.detail.project.name }}</h1>
        <p class="page-header__subtitle">先维护工具和模型，再在这里关联项目默认值。任务可以沿用，也可以在启动编码前改选。</p>
      </div>
    </div>
    <el-descriptions :column="2" border class="block">
      <el-descriptions-item label="编码">{{ store.detail.project.code }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ store.detail.project.status }}</el-descriptions-item>
      <el-descriptions-item label="技术栈">{{ store.detail.project.techStack || '未填' }}</el-descriptions-item>
      <el-descriptions-item label="回灌上限">{{ store.detail.project.feedbackLimit }}</el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">{{ store.detail.project.description || '无' }}</el-descriptions-item>
    </el-descriptions>

    <el-card class="block">
      <template #header>
        <div class="row-between">
          <span>默认绑定：编码工具 → 模型供应商 → 模型</span>
          <el-button type="primary" :loading="saving" @click="saveDefaults">保存默认值</el-button>
        </div>
      </template>
      <BindChain
        :tools="tools"
        :providers="providers"
        v-model:agent-profile-id="defaultsForm.defaultAgentProfileId"
        v-model:model-provider-id="defaultsForm.defaultModelProviderId"
        v-model:model-id="defaultsForm.defaultModelId"
      />
      <p class="hint">任务启动时可选择「使用项目默认」。换成别的供应商时，模型列表会跟着变，不用手填模型 ID。</p>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="row-between">
          <span>关联仓库</span>
          <el-button type="primary" link @click="showRepo = true">添加仓库</el-button>
        </div>
      </template>
      <el-table :data="store.detail.repositories" empty-text="还没有仓库，创建任务前至少需要一条">
        <el-table-column prop="displayName" label="名称" />
        <el-table-column prop="localDirectoryName" label="检出目录" width="140" />
        <el-table-column prop="defaultBranch" label="基线分支" width="120" />
        <el-table-column prop="providerType" label="托管" width="100" />
        <el-table-column prop="cloneUrl" label="地址" />
      </el-table>
    </el-card>


    <el-card class="block">
      <template #header>
        <div class="row-between">
          <span>项目成员</span>
          <el-button type="primary" link @click="showMember = true">邀请/添加</el-button>
        </div>
      </template>
      <el-alert
        class="block"
        type="info"
        :closable="false"
        show-icon
        title="仅本公司用户。角色为 project-admin 或 member。平台管理员不挂租户、不出现在项目工作台。"
      />
      <el-table :data="members" empty-text="还没有成员（或无权查看）">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="role" label="角色" width="140" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMember(row.userId)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="row-between">
          <span>需求</span>
          <el-button type="primary" link @click="showReq = true">新建需求</el-button>
        </div>
      </template>
      <el-table :data="store.requirements" empty-text="还没有需求" @row-click="openRequirement">
        <el-table-column label="名称" min-width="240">
          <template #default="{ row }">
            <div class="name-cell">
              <span v-if="row.code" class="fp-code-chip fp-code-chip--req">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="fp-status-tag" :class="'is-' + (row.status === '已发布' || row.status === '已完成' ? 'success' : row.status === '待验收' ? 'warning' : 'primary')">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column label="任务" width="100">
          <template #default="{ row }">{{ row.taskDone }}/{{ row.taskTotal }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showRepo" title="关联仓库" width="520px">
      <el-form label-width="110px">
        <el-form-item label="显示名称" required>
          <el-input v-model="repoForm.displayName" />
        </el-form-item>
        <el-form-item label="Clone URL" required>
          <el-input v-model="repoForm.cloneUrl" placeholder="https://github.com/owner/repo.git 或 https://gitlab.example.com/group/repo.git" />
        </el-form-item>
        <el-form-item label="检出目录名" required>
          <el-input v-model="repoForm.localDirectoryName" placeholder="如 pay-api，不能含路径" />
        </el-form-item>
        <el-form-item label="基线分支">
          <el-input v-model="repoForm.defaultBranch" placeholder="默认 main" />
        </el-form-item>
        <el-form-item label="托管类型">
          <el-select v-model="repoForm.providerType" style="width: 100%">
            <el-option label="GitLab" value="GITLAB" />
            <el-option label="GitHub" value="GITHUB" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRepo = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addRepo">保存</el-button>
      </template>
    </el-dialog>


    <el-dialog v-model="showMember" title="邀请项目成员" width="520px">
      <el-form label-width="100px">
        <el-form-item label="用户名" required>
          <el-input v-model="memberForm.username" placeholder="已有公司用户或新建" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="memberForm.displayName" />
        </el-form-item>
        <el-form-item label="初始口令">
          <el-input v-model="memberForm.password" type="password" placeholder="新建用户时必填，≥8 位" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" style="width: 100%">
            <el-option label="成员" value="member" />
            <el-option label="项目管理员" value="project-admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMember = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="inviteMember">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showReq" title="新建需求" width="520px">
      <el-form label-width="88px">
        <el-form-item label="标题" required>
          <el-input v-model="reqForm.title" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="reqForm.description" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="reqForm.priority" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReq = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addRequirement">创建</el-button>
      </template>
    </el-dialog>
  </div>
  <el-empty v-else-if="showEmpty" description="没有这个项目或无权查看">
    <el-button @click="backToProjects">返回项目列表</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch } from '@/api/http'
import BindChain from '@/components/BindChain.vue'
import { useCatalogOptions } from '@/composables/useCatalogOptions'
import { useProjectsStore } from '@/stores/projects'

const props = defineProps<{ id: string }>()
const store = useProjectsStore()
const router = useRouter()
const catalog = useCatalogOptions()
const providers = catalog.providers
const tools = catalog.tools
const showEmpty = computed(() => !store.detail && !store.loading)
function backToProjects() {
  void router.push('/projects')
}
const showRepo = ref(false)
const showReq = ref(false)
const showMember = ref(false)
const members = ref<{ membershipId: string; userId: string; username: string; displayName: string; role: string }[]>([])
const memberForm = reactive({ username: '', displayName: '', password: '', role: 'member' })
const saving = ref(false)
const repoForm = reactive({
  displayName: '',
  cloneUrl: '',
  localDirectoryName: '',
  defaultBranch: 'main',
  providerType: 'GITLAB',
})
function detectProvider(url: string): 'GITHUB' | 'GITLAB' {
  const value = url.trim().toLowerCase()
  if (value.includes('github.com')) {
    return 'GITHUB'
  }
  return 'GITLAB'
}

watch(
  () => repoForm.cloneUrl,
  (url) => {
    if (url && url.trim()) {
      repoForm.providerType = detectProvider(url)
    }
  },
)

const reqForm = reactive({
  title: '',
  description: '',
  priority: 'MEDIUM',
})
const defaultsForm = reactive({
  defaultAgentProfileId: '',
  defaultModelProviderId: '',
  defaultModelId: '',
})

async function refresh() {
  await Promise.all([store.loadDetail(props.id), catalog.load()])
  if (store.detail) {
    defaultsForm.defaultAgentProfileId = store.detail.project.defaultAgentProfileId || ''
    defaultsForm.defaultModelProviderId = store.detail.project.defaultModelProviderId || ''
    defaultsForm.defaultModelId = store.detail.project.defaultModelId || ''
  }
  try {
    members.value = await apiFetch(`/api/v1/projects/${props.id}/members`)
  } catch {
    members.value = []
  }
}

async function inviteMember() {
  if (!memberForm.username.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  saving.value = true
  try {
    await apiFetch(`/api/v1/projects/${props.id}/members`, {
      method: 'POST',
      body: JSON.stringify({
        username: memberForm.username.trim(),
        displayName: memberForm.displayName.trim() || undefined,
        password: memberForm.password || undefined,
        role: memberForm.role,
      }),
    })
    showMember.value = false
    memberForm.username = ''
    memberForm.displayName = ''
    memberForm.password = ''
    memberForm.role = 'member'
    ElMessage.success('成员已保存')
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '邀请失败')
  } finally {
    saving.value = false
  }
}

async function removeMember(userId: string) {
  saving.value = true
  try {
    await apiFetch(`/api/v1/projects/${props.id}/members/${userId}`, { method: 'DELETE' })
    ElMessage.success('已移除')
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除失败')
  } finally {
    saving.value = false
  }
}

async function saveDefaults() {
  saving.value = true
  try {
    await apiFetch(`/api/v1/projects/${props.id}/defaults`, {
      method: 'PATCH',
      body: JSON.stringify({
        defaultAgentProfileId: defaultsForm.defaultAgentProfileId || null,
        defaultModelProviderId: defaultsForm.defaultModelProviderId || null,
        defaultModelId: defaultsForm.defaultModelId || null,
      }),
    })
    ElMessage.success('默认值已保存')
    await refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void refresh()
})

watch(
  () => props.id,
  () => {
    void refresh()
  },
)

function openRequirement(row: { id: string }) {
  router.push(`/requirements/${row.id}`)
}

async function addRepo() {
  if (!repoForm.displayName.trim() || !repoForm.cloneUrl.trim() || !repoForm.localDirectoryName.trim()) {
    ElMessage.warning('请填写名称、地址和检出目录')
    return
  }
  saving.value = true
  try {
    await store.addRepository(props.id, { ...repoForm })
    showRepo.value = false
    repoForm.displayName = ''
    repoForm.cloneUrl = ''
    repoForm.localDirectoryName = ''
    repoForm.defaultBranch = 'main'
    repoForm.providerType = 'GITLAB'
    ElMessage.success('仓库已关联')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function addRequirement() {
  if (!reqForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  saving.value = true
  try {
    const created = await store.createRequirement(props.id, {
      title: reqForm.title.trim(),
      description: reqForm.description.trim() || undefined,
      priority: reqForm.priority,
    })
    showReq.value = false
    reqForm.title = ''
    reqForm.description = ''
    reqForm.priority = 'MEDIUM'
    ElMessage.success('需求已创建')
    await router.push(`/requirements/${created.id}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.block {
  margin-top: 16px;
}
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hint {
  color: var(--fp-color-text-secondary);
  margin: 12px 0 0;
}
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
