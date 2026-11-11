<template>
  <div class="fp-bind-chain" :class="{ 'is-compact': compact }">
    <div class="fp-bind-card">
      <div class="fp-bind-card__label">编码工具</div>
      <el-select
        :model-value="agentProfileId || ''"
        filterable
        clearable
        :disabled="disabled"
        :placeholder="allowProjectDefault ? '使用项目默认' : '选择编码工具'"
        style="width: 100%"
        @update:model-value="onToolChange"
      >
        <el-option
          v-if="allowProjectDefault"
          value=""
          :label="defaultToolLabel"
        />
        <el-option v-for="tool in enabledTools" :key="tool.id" :label="tool.name" :value="tool.id">
          <div class="fp-bind-option">
            <span>{{ tool.name }}</span>
            <small>{{ agentTypeLabel(tool.agentType) || tool.agentType }}</small>
          </div>
        </el-option>
      </el-select>
      <div class="fp-bind-card__meta">{{ toolMeta }}</div>
    </div>
    <div class="fp-bind-arrow" aria-hidden="true">
      <svg viewBox="0 0 16 16" fill="none">
        <path d="M3 8h10M9.5 4.5L13 8l-3.5 3.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      关联
    </div>
    <div class="fp-bind-card">
      <div class="fp-bind-card__label">模型供应商</div>
      <el-select
        :model-value="modelProviderId || ''"
        filterable
        clearable
        :disabled="disabled"
        :placeholder="allowProjectDefault ? '使用项目默认' : '选择供应商'"
        style="width: 100%"
        @update:model-value="onProviderChange"
      >
        <el-option
          v-if="allowProjectDefault"
          value=""
          :label="defaultProviderLabel"
        />
        <el-option v-for="item in enabledProviders" :key="item.id" :label="item.name" :value="item.id">
          <div class="fp-bind-option">
            <span>{{ item.name }}</span>
            <small>{{ providerTypeLabel(item.providerType) }}</small>
          </div>
        </el-option>
      </el-select>
      <div class="fp-bind-card__meta">{{ providerMeta }}</div>
    </div>
    <div class="fp-bind-arrow" aria-hidden="true">
      <svg viewBox="0 0 16 16" fill="none">
        <path d="M3 8h10M9.5 4.5L13 8l-3.5 3.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      提供
    </div>
    <div class="fp-bind-card">
      <div class="fp-bind-card__label">{{ allowProjectDefault ? '模型' : '默认模型' }}</div>
      <el-select
        :model-value="modelId || ''"
        filterable
        clearable
        :disabled="disabled || (!resolvedProviderId && !allowProjectDefault)"
        :placeholder="modelPlaceholder"
        style="width: 100%"
        @update:model-value="emit('update:modelId', $event || '')"
      >
        <el-option
          v-if="allowProjectDefault && !modelProviderId"
          value=""
          :label="defaultModelLabel"
        />
        <el-option
          v-for="model in modelOptions"
          :key="model.code"
          :label="modelOptionLabel(model)"
          :value="model.code"
        >
          <div class="fp-bind-option">
            <span>{{ model.name || model.code }}</span>
            <small v-if="model.name && model.name !== model.code">{{ model.code }}</small>
          </div>
        </el-option>
      </el-select>
      <div class="fp-bind-card__meta">{{ modelMeta }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  agentTypeLabel,
  displayNameById,
  isModelCompatible,
  modelOptionLabel,
  modelsForProvider,
  providerTypeLabel,
  type ProviderOption,
  type ToolOption,
} from '@/domain/catalog'

const props = withDefaults(
  defineProps<{
    tools: ToolOption[]
    providers: ProviderOption[]
    agentProfileId?: string | null
    modelProviderId?: string | null
    modelId?: string | null
    allowProjectDefault?: boolean
    defaultAgentProfileId?: string | null
    defaultModelProviderId?: string | null
    defaultModelId?: string | null
    disabled?: boolean
    compact?: boolean
  }>(),
  {
    agentProfileId: '',
    modelProviderId: '',
    modelId: '',
    allowProjectDefault: false,
    defaultAgentProfileId: '',
    defaultModelProviderId: '',
    defaultModelId: '',
    disabled: false,
    compact: false,
  },
)

const emit = defineEmits<{
  'update:agentProfileId': [value: string]
  'update:modelProviderId': [value: string]
  'update:modelId': [value: string]
}>()

const enabledTools = computed(() => props.tools.filter((item) => item.enabled !== false))
const enabledProviders = computed(() => props.providers.filter((item) => item.enabled !== false))

const resolvedProviderId = computed(() => props.modelProviderId || (props.allowProjectDefault ? props.defaultModelProviderId : '') || '')
const modelOptions = computed(() => modelsForProvider(props.providers, resolvedProviderId.value))

const defaultToolLabel = computed(() => {
  const name = displayNameById(props.tools, props.defaultAgentProfileId)
  return name ? `使用项目默认 · ${name}` : '使用项目默认'
})
const defaultProviderLabel = computed(() => {
  const name = displayNameById(props.providers, props.defaultModelProviderId)
  return name ? `使用项目默认 · ${name}` : '使用项目默认'
})
const defaultModelLabel = computed(() => {
  const code = props.defaultModelId
  if (!code) return '使用项目默认'
  const model = modelsForProvider(props.providers, props.defaultModelProviderId).find((item) => item.code === code)
  return `使用项目默认 · ${model ? modelOptionLabel(model) : code}`
})

const toolMeta = computed(() => {
  const selected = props.tools.find((item) => item.id === props.agentProfileId)
  if (selected) {
    return `${agentTypeLabel(selected.agentType) || selected.agentType || '编码工具'}${selected.enabled === false ? ' · 已停用' : ' · 已启用'}`
  }
  if (props.allowProjectDefault) return '可改选其他已启用工具'
  return '从已维护的编码工具里选择'
})

const providerMeta = computed(() => {
  const selected = props.providers.find((item) => item.id === props.modelProviderId)
  if (selected) {
    const kind = providerTypeLabel(selected.providerType) || '供应商'
    return `${kind}${selected.hasSecret ? ' · 密钥已配置' : ''}`
  }
  if (props.allowProjectDefault) return '切换后刷新模型列表'
  return '从已维护的供应商里选择'
})

const modelPlaceholder = computed(() => {
  if (!resolvedProviderId.value) {
    return props.allowProjectDefault ? '使用项目默认' : '请先选择供应商'
  }
  return modelOptions.value.length ? '从该供应商的模型里选择' : '请先在供应商中维护模型码'
})

const modelMeta = computed(() => {
  if (!resolvedProviderId.value) {
    return props.allowProjectDefault ? '沿用项目默认，或先选供应商' : '模型列表来自所选供应商'
  }
  if (!modelOptions.value.length) return '该供应商还没有模型码，请到「模型与工具」里添加'
  return '只显示当前供应商已维护的模型，不必手填 ID'
})

function onToolChange(value: string) {
  const next = value || ''
  emit('update:agentProfileId', next)
  const tool = props.tools.find((item) => item.id === next)
  if (tool?.modelProviderId && !props.modelProviderId) {
    onProviderChange(tool.modelProviderId)
    if (tool.modelId) emit('update:modelId', tool.modelId)
  }
}

function onProviderChange(value: string) {
  const next = value || ''
  emit('update:modelProviderId', next)
  const providerId = next || (props.allowProjectDefault ? props.defaultModelProviderId : '') || ''
  if (!isModelCompatible(props.providers, providerId, props.modelId)) {
    emit('update:modelId', '')
  }
}
</script>
