import { createRouter, createWebHistory } from 'vue-router'
import ActivityView from '@/views/ActivityView.vue'
import CodingListView from '@/views/CodingListView.vue'
import DeliveryListView from '@/views/DeliveryListView.vue'
import GuidanceView from '@/views/GuidanceView.vue'
import LoginView from '@/views/LoginView.vue'
import CatalogView from '@/views/CatalogView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import NodesView from '@/views/NodesView.vue'
import ProjectDetailView from '@/views/ProjectDetailView.vue'
import ProjectsView from '@/views/ProjectsView.vue'
import RequirementDetailView from '@/views/RequirementDetailView.vue'
import TaskDetailView from '@/views/TaskDetailView.vue'
import WorkbenchView from '@/views/WorkbenchView.vue'
import { getAccessToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'workbench', component: WorkbenchView },
    { path: '/requirements/:id', name: 'requirement', component: RequirementDetailView, props: true },
    { path: '/tasks/:id', name: 'task', component: TaskDetailView, props: true },
    { path: '/projects', name: 'projects', component: ProjectsView },
    { path: '/projects/:id', name: 'project-detail', component: ProjectDetailView, props: true },
    { path: '/guidance', name: 'guidance', component: GuidanceView },
    { path: '/catalog', name: 'catalog', component: CatalogView },
    { path: '/activity', name: 'activity', component: ActivityView },
    { path: '/coding', name: 'coding', component: CodingListView },
    { path: '/delivery', name: 'delivery', component: DeliveryListView },
    { path: '/nodes', name: 'nodes', component: NodesView },
    {
      path: '/admin',
      name: 'admin',
      component: PlaceholderView,
      meta: {
        title: '平台管理',
        note: '平台级入口。公司模型/工具目录请到「模型与工具」；平台规约在「规约与文档」里用平台层维护。节点状态见「执行节点」。',
      },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!getAccessToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
