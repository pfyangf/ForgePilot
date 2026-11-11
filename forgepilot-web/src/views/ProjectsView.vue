<template>
  <div>
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">项目</h1>
        <p class="page-header__subtitle">在项目里关联仓库、默认工具和模型，再提出需求交给平台托管推进。</p>
      </div>
    </div>
    <div class="toolbar">
      <el-button type="primary" @click="showCreate = true">新建项目</el-button>
      <el-button :loading="store.loading" @click="store.load()">刷新</el-button>
      <span v-if="store.error" class="error">{{ store.error }}</span>
    </div>
    <el-card v-loading="store.loading">
      <el-table :data="store.items" empty-text="当前租户下没有可见项目" @row-click="openProject">
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <span>{{ row.name }}</span>
              <span class="fp-code-chip">{{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="techStack" label="技术栈" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="fp-status-tag is-primary">{{ row.status }}</span>
          </template>
        </el-table-column>
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
  color: var(--fp-color-danger);
}
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

