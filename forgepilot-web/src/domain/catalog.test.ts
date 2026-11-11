import { describe, expect, it } from 'vitest'
import { isModelCompatible, modelOptionLabel, modelsForProvider, providerTypeLabel } from './catalog'

const providers = [
  {
    id: 'p1',
    name: 'OrcaRouter',
    providerType: 'OPENAI_COMPAT',
    models: [
      { code: 'deepseek-chat', name: 'DeepSeek V3' },
      { code: 'gpt-4.1-mini', name: null },
    ],
  },
  { id: 'p2', name: 'Empty', models: [] },
]

describe('catalog binding helpers', () => {
  it('lists models for the selected provider and labels them with display names', () => {
    expect(modelsForProvider(providers, 'p1').map((item) => item.code)).toEqual(['deepseek-chat', 'gpt-4.1-mini'])
    expect(modelOptionLabel({ code: 'deepseek-chat', name: 'DeepSeek V3' })).toBe('DeepSeek V3（deepseek-chat）')
    expect(providerTypeLabel('OPENAI_COMPAT')).toBe('OpenAI 兼容')
  })

  it('clears incompatible models when the provider changes', () => {
    expect(isModelCompatible(providers, 'p1', 'deepseek-chat')).toBe(true)
    expect(isModelCompatible(providers, 'p2', 'deepseek-chat')).toBe(true)
    expect(isModelCompatible(providers, 'p1', 'missing-model')).toBe(false)
    expect(isModelCompatible(providers, '', 'deepseek-chat')).toBe(true)
  })
})
