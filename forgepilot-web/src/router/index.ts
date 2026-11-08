import { createRouter, createWebHistory } from 'vue-router'
import ActivityView from '@/views/ActivityView.vue'
import CodingListView from '@/views/CodingListView.vue'
import DeliveryListView from '@/views/DeliveryListView.vue'
import GuidanceView from '@/views/GuidanceView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import RequirementDetailView from '@/views/RequirementDetailView.vue'
import TaskDetailView from '@/views/TaskDetailView.vue'
import WorkbenchView from '@/views/WorkbenchView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'workbench', component: WorkbenchView },
    { path: '/requirements/:id', name: 'requirement', component: RequirementDetailView, props: true },
    { path: '/tasks/:id', name: 'task', component: TaskDetailView, props: true },
    { path: '/projects', name: 'projects', component: PlaceholderView, meta: { title: '项目管理', note: '项目属于一家公司，并关联多条仓库。任务启动时冻结这组仓库和各仓库自己的基线分支。' } },
    {
      path: '/guidance',
      name: 'guidance',
      component: GuidanceView,
      meta: {
        title: '规约与文档',
        note: '平台、公司、项目三层。这不是文档库或知识库检索。只有对应管理员能改自己那一层。',
      },
    },
    { path: '/activity', name: 'activity', component: ActivityView },
    { path: '/coding', name: 'coding', component: CodingListView },
    { path: '/delivery', name: 'delivery', component: DeliveryListView },
    {
      path: '/admin',
      name: 'admin',
      component: PlaceholderView,
      meta: {
        title: '平台管理',
        note: '模型目录由控制面维护。骨架上的 /api/v1/model-providers 返回 501，响应里没有密钥。',
      },
    },
  ],
})

export default router
