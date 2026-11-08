<template>
  <div v-if="task">
    <el-page-header @back="router.push('/')" :content="task.title" />
    <el-alert v-if="store.actionError" class="block" type="error" show-icon :closable="false" :title="store.actionError" />
    <el-descriptions :column="2" border class="block">
      <el-descriptions-item label="状态">{{ task.status }}</el-descriptions-item>
      <el-descriptions-item label="会话">{{ task.sessionStatus ?? '无（Task ≠ Session）' }}</el-descriptions-item>
      <el-descriptions-item label="分支">{{ task.branchName }}</el-descriptions-item>
      <el-descriptions-item label="项目">{{ task.projectName }}</el-descriptions-item>
      <el-descriptions-item label="Agent">{{ task.agentLabel }}</el-descriptions-item>
      <el-descriptions-item label="冻结仓库数">{{ task.frozenRepositories?.length ?? 0 }}</el-descriptions-item>
    </el-descriptions>
    <el-alert class="block" type="warning" show-icon :closable="false" :title="task.evidenceNote" />

    <el-card class="block">
      <template #header>创建时冻结的仓库集合</template>
      <el-table :data="task.frozenRepositories || []" empty-text="没有冻结记录">
        <el-table-column prop="directoryName" label="检出目录" width="160" />
        <el-table-column prop="baseBranch" label="基线分支" width="140" />
        <el-table-column prop="deliveryState" label="交付状态" width="120" />
        <el-table-column prop="cloneUrl" label="地址" />
      </el-table>
    </el-card>

    <el-card v-if="task.status === 'RUNNING'" class="block">
      <template #header>执行中的会话</template>
      <el-alert type="info" :closable="false" show-icon :title="task.unsupported?.startOrRetryReason || '执行面尚未接通'" />
      <pre class="log">{{ task.logs.join('\n') || '没有实时日志（Codex 尚未接通）' }}</pre>
      <div class="row">
        <el-button type="danger" :loading="saving" @click="cancel">取消任务</el-button>
        <el-button disabled>附说明重试（未实现）</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'VERIFYING'" class="block">
      <template #header>验证</template>
      <el-alert type="info" :closable="false" show-icon title="平台正在跑项目配置的验证命令（本机子进程；容器验证器可替换）。不会假装 Codex 已执行。" />
    </el-card>

    <el-card v-else-if="task.status === 'WAITING_APPROVAL'" class="block">
      <template #header>任务级审核</template>
      <p>审核通过只把任务标为 COMPLETED，不会自动合并。以下 Draft MR 由平台交付创建。</p>
      <el-alert
        v-if="task.testFileChanges?.length"
        class="block"
        type="warning"
        show-icon
        :closable="false"
        :title="'检测到测试文件变更：' + task.testFileChanges.join('；')"
      />
      <el-table :data="task.mergeRequests" empty-text="还没有 Draft MR">
        <el-table-column prop="directoryName" label="仓库目录" />
        <el-table-column prop="url" label="MR" />
        <el-table-column prop="headSha" label="Head" width="140" />
      </el-table>
      <div class="row">
        <el-input v-model="note" placeholder="审核说明（可选）" />
        <el-button type="primary" :loading="saving" @click="approve">审核通过</el-button>
        <el-button disabled>要求修改（未实现）</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'WAITING_USER'" class="block">
      <template #header>{{ task.interrupted ? '节点中断' : '等待人工处理' }}</template>
      <el-alert type="info" :closable="false" show-icon :title="task.unsupported?.retryReason || '重试需要执行面'" />
      <div class="row">
        <el-input v-model="note" placeholder="说明（取消/放弃时可选）" />
        <el-button type="primary" :loading="saving" @click="verify">再次验证</el-button>
        <el-button :loading="saving" @click="retryDelivery">重试交付</el-button>
        <el-button disabled>Agent 重试（未实现）</el-button>
        <el-button :loading="saving" @click="cancel">取消</el-button>
        <el-button type="danger" :loading="saving" @click="abandon">明确放弃</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'COMPLETED'" class="block">
      <template #header>交付记录</template>
      <p>任务已完成。关联的 MR 若存在会保持打开，平台不会合并。</p>
    </el-card>

    <el-card v-else-if="task.status === 'PENDING' || task.status === 'PREPARING'" class="block">
      <template #header>待执行</template>
      <el-alert type="info" :closable="false" show-icon title="启动执行需要 Codex / 执行节点，尚未实现。可以取消任务。" />
      <div class="row">
        <el-button type="primary" :loading="saving" @click="verify">平台验证</el-button>
        <el-button disabled>启动 Agent（未实现）</el-button>
        <el-button type="danger" :loading="saving" @click="cancel">取消</el-button>
      </div>
    </el-card>

    <el-card v-else class="block">
      <template #header>证据</template>
      <p>状态 {{ task.status }}。这里只展示已经收集的说明。</p>
      <pre class="log">{{ task.logs.join('\n') || '没有日志' }}</pre>
      <div v-if="task.status !== 'FAILED' && task.status !== 'CANCELLED'" class="row">
        <el-button type="danger" :loading="saving" @click="cancel">取消</el-button>
      </div>
    </el-card>
  </div>
  <el-empty v-else description="没有这条任务">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useWorkbenchStore } from '@/stores/workbench'

const props = defineProps<{ id: string }>()
const store = useWorkbenchStore()
const router = useRouter()
const task = computed(() => store.taskById(props.id))
const note = ref('')
const saving = ref(false)

onMounted(async () => {
  try {
    await store.loadTaskDetail(props.id)
  } catch {
    await store.load('member')
  }
})

async function cancel() {
  saving.value = true
  try {
    await store.cancelTask(props.id, note.value)
    ElMessage.success('任务已取消')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
  } finally {
    saving.value = false
  }
}

async function abandon() {
  saving.value = true
  try {
    await store.abandonTask(props.id, note.value)
    ElMessage.success('已明确放弃')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    saving.value = false
  }
}

async function approve() {
  saving.value = true
  try {
    await store.approveTask(props.id, note.value)
    ElMessage.success('审核已通过')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '审核失败')
  } finally {
    saving.value = false
  }
}

async function verify() {
  saving.value = true
  try {
    await store.verifyTask(props.id)
    ElMessage.success('验证/交付流程已执行')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '验证失败')
  } finally {
    saving.value = false
  }
}

async function retryDelivery() {
  saving.value = true
  try {
    await store.retryDelivery(props.id)
    ElMessage.success('已重试交付')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  } finally {
    saving.value = false
  }
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
}
</style>
