import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiFetch } from '@/api/http'

export interface ProjectSummary {
  id: string
  tenantId: string
  name: string
  code: string
  description: string | null
  techStack: string | null
  status: string
  feedbackLimit: number | null
  defaultAgentProfileId?: string | null
  defaultModelProviderId?: string | null
  defaultModelId?: string | null
}

export interface RepositorySummary {
  id: string
  projectId: string
  providerType: string
  displayName: string
  cloneUrl: string
  localDirectoryName: string
  defaultBranch: string
}

export interface ProjectDetail {
  project: ProjectSummary
  repositories: RepositorySummary[]
}

export interface RequirementSummary {
  id: string
  code: string
  title: string
  status: string
  priority: string
  projectId: string
  projectName: string
  createdBy: string
  ownerId: string
  draft: string
  confirmed: boolean
  taskDone: number
  taskTotal: number
}

export const useProjectsStore = defineStore('projects', () => {
  const items = ref<ProjectSummary[]>([])
  const detail = ref<ProjectDetail | null>(null)
  const requirements = ref<RequirementSummary[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      items.value = await apiFetch<ProjectSummary[]>('/api/v1/projects')
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载项目失败'
      items.value = []
    } finally {
      loading.value = false
    }
  }

  async function create(payload: { name: string; code: string; description?: string; techStack?: string }) {
    const created = await apiFetch<ProjectSummary>('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    items.value = [created, ...items.value]
    return created
  }

  async function loadDetail(projectId: string) {
    loading.value = true
    error.value = ''
    try {
      detail.value = await apiFetch<ProjectDetail>(`/api/v1/projects/${projectId}`)
      requirements.value = await apiFetch<RequirementSummary[]>(`/api/v1/projects/${projectId}/requirements`)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载项目详情失败'
      detail.value = null
      requirements.value = []
    } finally {
      loading.value = false
    }
  }

  async function addRepository(
    projectId: string,
    payload: {
      displayName: string
      cloneUrl: string
      localDirectoryName: string
      defaultBranch?: string
      providerType?: string
    },
  ) {
    const repo = await apiFetch<RepositorySummary>(`/api/v1/projects/${projectId}/repositories`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    if (detail.value && detail.value.project.id === projectId) {
      detail.value = {
        ...detail.value,
        repositories: [...detail.value.repositories, repo],
      }
    }
    return repo
  }

  async function createRequirement(
    projectId: string,
    payload: { title: string; description?: string; priority?: string },
  ) {
    const created = await apiFetch<RequirementSummary>(`/api/v1/projects/${projectId}/requirements`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    requirements.value = [created, ...requirements.value]
    return created
  }

  return {
    items,
    detail,
    requirements,
    loading,
    error,
    load,
    create,
    loadDetail,
    addRepository,
    createRequirement,
  }
})
