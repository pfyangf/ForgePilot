<template>
  <div v-if="store.detail">
    <el-page-header @back="router.push('/projects')" :content="store.detail.project.name" />
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
          <span>需求</span>
          <el-button type="primary" link @click="showReq = true">新建需求</el-button>
        </div>
      </template>
      <el-table :data="store.requirements" empty-text="还没有需求" @row-click="openRequirement">
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="title" label="名称" />
        <el-table-column prop="status" label="状态" width="120" />
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
          <el-input v-model="repoForm.cloneUrl" placeholder="https://gitlab.example.com/group/repo.git" />
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
            <el-option label="GitHub（仅登记）" value="GITHUB" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRepo = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addRepo">保存</el-button>
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
import { useProjectsStore } from '@/stores/projects'

const props = defineProps<{ id: string }>()
const store = useProjectsStore()
const router = useRouter()
const showEmpty = computed(() => !store.detail && !store.loading)
function backToProjects() {
  void router.push('/projects')
}
const showRepo = ref(false)
const showReq = ref(false)
const saving = ref(false)
const repoForm = reactive({
  displayName: '',
  cloneUrl: '',
  localDirectoryName: '',
  defaultBranch: 'main',
  providerType: 'GITLAB',
})
const reqForm = reactive({
  title: '',
  description: '',
  priority: 'MEDIUM',
})

async function refresh() {
  await store.loadDetail(props.id)
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
</style>
