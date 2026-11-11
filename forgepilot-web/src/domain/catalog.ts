export interface ModelCode {
  code: string
  name?: string | null
}

export interface ProviderOption {
  id: string
  name: string
  providerType?: string
  baseUrl?: string
  enabled?: boolean
  upstreamWireApi?: string
  hasSecret?: boolean
  eligibleForCodex?: boolean
  models?: ModelCode[] | null
}

export interface ToolOption {
  id: string
  name: string
  agentType?: string
  modelProviderId?: string | null
  modelId?: string | null
  enabled?: boolean
}

export const PROVIDER_TYPES = [{ value: 'OPENAI_COMPAT', label: 'OpenAI 兼容' }] as const

export const AGENT_TYPES = [{ value: 'CODEX_CLI', label: 'Codex CLI' }] as const

export function providerTypeLabel(value: string | null | undefined) {
  if (!value) return ''
  return PROVIDER_TYPES.find((item) => item.value === value)?.label || value
}

export function agentTypeLabel(value: string | null | undefined) {
  if (!value) return ''
  return AGENT_TYPES.find((item) => item.value === value)?.label || value
}

export function modelsForProvider(providers: ProviderOption[], providerId: string | null | undefined) {
  if (!providerId) return [] as ModelCode[]
  const provider = providers.find((item) => item.id === providerId)
  return (provider?.models || []).filter((item) => item.code)
}

export function modelLabel(model: ModelCode | null | undefined) {
  if (!model?.code) return ''
  return model.name ? `${model.name}` : model.code
}

export function modelOptionLabel(model: ModelCode) {
  return model.name && model.name !== model.code ? `${model.name}（${model.code}）` : model.code
}

export function findModel(providers: ProviderOption[], providerId: string | null | undefined, modelId: string | null | undefined) {
  if (!modelId) return null
  return modelsForProvider(providers, providerId).find((item) => item.code === modelId) || { code: modelId, name: null }
}

export function isModelCompatible(
  providers: ProviderOption[],
  providerId: string | null | undefined,
  modelId: string | null | undefined,
) {
  if (!modelId) return true
  if (!providerId) return true
  const models = modelsForProvider(providers, providerId)
  if (!models.length) return true
  return models.some((item) => item.code === modelId)
}

export function displayNameById<T extends { id: string; name: string }>(items: T[], id: string | null | undefined) {
  if (!id) return ''
  return items.find((item) => item.id === id)?.name || ''
}
