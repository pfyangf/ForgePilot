<template>
  <div>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="项目管理"
      description="项目属于当前公司租户。租户管理员可新建项目；项目管理员可关联多条仓库。任务创建时会冻结当时的仓库集合和各仓库基线分支。"
    />
    <div class="toolbar">
      <el-button type="primary" @click="showCreate = true">新建项目</el-button>
      <el-button :loading="store.loading" @click="store.load()">刷新</el-button>
      <span v-if="store.error" class="error">{{ store.error }}</span>
    </div>
    <el-card v-loading="store.loading">
      <el-table :data="store.items" empty-text="当前租户下没有可见项目" @row-click="openProject">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="techStack" label="技术栈" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-card>

    <el-dialog v-model="showCreate" title="新建项目" width="480px">
      <el-form label-width="88px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="createForm.code" placeholder="字母数字下划线" />
        </el-form-item>
        <el-form-item label="技术栈">
          <el-input v-model="createForm.techStack" placeholder="如 Java, Vue" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useProjectsStore } from '@/stores/projects'

const store = useProjectsStore()
const router = useRouter()
const showCreate = ref(false)
const saving = ref(false)
const createForm = reactive({
  name: '',
  code: '',
  description: '',
  techStack: '',
})

onMounted(() => {
  void store.load()
})

function openProject(row: { id: string }) {
  router.push(`/projects/${row.id}`)
}

async function createProject() {
  if (!createForm.name.trim() || !createForm.code.trim()) {
    ElMessage.warning('请填写名称和编码')
    return
  }
  saving.value = true
  try {
    const created = await store.create({
      name: createForm.name.trim(),
      code: createForm.code.trim(),
      description: createForm.description.trim() || undefined,
      techStack: createForm.techStack.trim() || undefined,
    })
    showCreate.value = false
    createForm.name = ''
    createForm.code = ''
    createForm.description = ''
    createForm.techStack = ''
    ElMessage.success('项目已创建')
    await router.push(`/projects/${created.id}`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 16px 0;
}
.error {
  color: #b91c1c;
}
</style>
