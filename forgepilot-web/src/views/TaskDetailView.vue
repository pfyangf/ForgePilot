<template>
  <div v-if="task">
    <el-page-header @back="router.push('/')" :content="task.title" />
    <el-descriptions :column="2" border class="block">
      <el-descriptions-item label="状态">{{ task.status }}</el-descriptions-item>
      <el-descriptions-item label="会话">{{ task.sessionStatus ?? '无' }}</el-descriptions-item>
      <el-descriptions-item label="分支">{{ task.branchName }}</el-descriptions-item>
      <el-descriptions-item label="模型">{{ task.providerName }} / {{ task.modelId }}</el-descriptions-item>
      <el-descriptions-item label="Agent">{{ task.agentLabel }}</el-descriptions-item>
      <el-descriptions-item label="检出">
        <span v-for="checkout in task.checkouts" :key="checkout.directoryName" class="chip">
          {{ checkout.directoryName }}{{ checkout.changed ? '（有改动）' : '' }}
        </span>
      </el-descriptions-item>
    </el-descriptions>
    <el-alert class="block" type="warning" show-icon :closable="false" :title="task.evidenceNote" />
    <el-card class="block">
      <template #header>这次会话会拿到的规约</template>
      <p>平台、公司、项目三层合成。同标题以更具体的一层为准，其余规则都保留。会话开始时冻结，中途改文档不会改已启动的 Agent。</p>
      <ul>
        <li>提交说明：公司要求带工单号</li>
        <li>模块边界：web 不直接访问支付库</li>
      </ul>
    </el-card>

    <el-card v-if="task.status === 'RUNNING'" class="block">
      <template #header>执行中的会话</template>
      <p>日志是普通文本，不是终端。这个页面也没有编辑器。</p>
      <pre class="log">{{ task.logs.join('\n') }}</pre>
      <div v-if="task.sessionStatus === 'WAITING_INPUT' && task.interactiveInput" class="row">
        <el-input v-model="message" placeholder="发一条消息" />
        <el-button type="primary" @click="send">发送</el-button>
      </div>
      <p v-else>当前适配器不能把消息送进仍在运行的进程。</p>
      <p v-if="!task.nativePause">未声明原生暂停，不提供从原进程恢复。</p>
      <div class="row">
        <el-input v-model="note" placeholder="重试说明，仍使用当前工作区" />
        <el-button @click="retry">附说明重试</el-button>
        <el-button type="danger" @click="store.cancelTask(task.id)">取消</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'VERIFYING'" class="block">
      <template #header>验证</template>
      <p>命令在工作区根目录执行：{{ task.verificationCommand }}</p>
      <p>退出码：{{ task.verificationExitCode === null ? '仍在运行' : task.verificationExitCode }}</p>
      <pre class="log">{{ task.verificationLog || '等待输出' }}</pre>
    </el-card>

    <el-card v-else-if="task.status === 'WAITING_APPROVAL'" class="block">
      <template #header>任务级审核</template>
      <p>下面这些 Draft MR 属于同一次审核。没有改动的仓库不会出现在这里。平台不会自动合并。</p>
      <el-table :data="task.mergeRequests" empty-text="没有 Draft MR">
        <el-table-column prop="directoryName" label="仓库目录" />
        <el-table-column prop="url" label="MR" />
        <el-table-column prop="headSha" label="Head" width="140" />
      </el-table>
      <p>未创建 MR 的检出：{{ task.unchangedRepos.join('、') || '无' }}</p>
      <el-alert v-if="task.testFileChanges.length" type="error" show-icon :closable="false" title="测试文件有改动" :description="task.testFileChanges.join('；')" />
      <pre class="diff"><span class="diff-del">- export(order)</span>
<span class="diff-add">+ export(order, repository)</span></pre>
      <div class="row">
        <el-input v-model="note" placeholder="要求修改时的说明" />
        <el-button type="primary" @click="store.approveTask(task.id)">审核通过</el-button>
        <el-button @click="store.requestChanges(task.id, note)">要求修改</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'WAITING_USER'" class="block">
      <template #header>{{ task.interrupted ? '节点中断' : '等待人工处理' }}</template>
      <pre class="log">{{ task.verificationLog || task.logs.join('\n') }}</pre>
      <div class="row">
        <el-input v-model="note" placeholder="在同一工作区重试的说明" />
        <el-button type="primary" @click="retry">重试</el-button>
        <el-button @click="store.cancelTask(task.id)">取消</el-button>
        <el-button type="danger" @click="store.abandonTask(task.id)">明确放弃</el-button>
      </div>
    </el-card>

    <el-card v-else-if="task.status === 'COMPLETED'" class="block">
      <template #header>交付记录</template>
      <p>任务已完成。关联的 MR 保持打开，平台没有合并。</p>
      <el-table :data="task.mergeRequests">
        <el-table-column prop="directoryName" label="仓库目录" />
        <el-table-column prop="url" label="MR" />
      </el-table>
      <pre class="log">{{ task.logs.join('\n') }}</pre>
    </el-card>

    <el-card v-else class="block">
      <template #header>证据</template>
      <p>状态 {{ task.status }}。这里只展示已经收集的说明和日志。</p>
      <pre class="log">{{ task.logs.join('\n') || '没有日志' }}</pre>
    </el-card>
  </div>
  <el-empty v-else description="没有这条任务">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const props = defineProps<{ id: string }>()
const store = useWorkbenchStore()
const router = useRouter()
const task = computed(() => store.taskById(props.id))

onMounted(() => {
  if (!task.value) {
    void store.load('member')
  }
})
const message = ref('')
const note = ref('')

function send() {
  store.sendMessage(props.id, message.value)
  message.value = ''
}

function retry() {
  store.retryTask(props.id, note.value)
  note.value = ''
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
.chip {
  margin-right: 8px;
}
</style>
