import { ref } from 'vue'
import { apiFetch } from '@/api/http'
import type { ProviderOption, ToolOption } from '@/domain/catalog'

export function useCatalogOptions() {
  const providers = ref<ProviderOption[]>([])
  const tools = ref<ToolOption[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const [providerRows, toolRows] = await Promise.all([
        apiFetch<ProviderOption[]>('/api/v1/model-providers'),
        apiFetch<ToolOption[]>('/api/v1/coding-tools'),
      ])
      providers.value = providerRows || []
      tools.value = toolRows || []
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载模型与工具失败'
      providers.value = []
      tools.value = []
    } finally {
      loading.value = false
    }
  }

  return { providers, tools, loading, error, load }
}
