import { describe, expect, it } from 'vitest'
import { APP_LOCALE, applyAppLocale, elementPlusLocale, isSimplifiedChineseLocale } from './locale'

describe('app locale', () => {
  it('locks Element Plus and the document to zh-CN, ignoring browser language', () => {
    expect(APP_LOCALE).toBe('zh-CN')
    expect(isSimplifiedChineseLocale(elementPlusLocale)).toBe(true)
    expect(elementPlusLocale.name).toBe('zh-cn')
    expect(String(elementPlusLocale.el?.datepicker?.today || '')).toBe('今天')
    expect(JSON.stringify(elementPlusLocale)).not.toMatch(/キャンセル/)
    const attrs: Record<string, string> = {}
    const root = {
      setAttribute(name: string, value: string) {
        attrs[name] = value
      },
      getAttribute(name: string) {
        return attrs[name] ?? null
      },
    } as unknown as HTMLElement
    applyAppLocale(root)
    expect(root.getAttribute('lang')).toBe('zh-CN')
  })
})
