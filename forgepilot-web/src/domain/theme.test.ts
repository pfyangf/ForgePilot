import { describe, expect, it } from 'vitest'
import { applyThemeClass, normalizeTheme, persistTheme, readStoredTheme, THEME_STORAGE_KEY } from './theme'

function stubRoot() {
  const classes = new Set<string>()
  const attrs: Record<string, string> = {}
  const style = { colorScheme: '' }
  const root = {
    classList: {
      contains: (name: string) => classes.has(name),
      toggle(name: string, force?: boolean) {
        const should = force == null ? !classes.has(name) : force
        if (should) classes.add(name)
        else classes.delete(name)
        return should
      },
    },
    style,
    getAttribute(name: string) {
      return attrs[name] ?? null
    },
    setAttribute(name: string, value: string) {
      attrs[name] = value
    },
  }
  return root as unknown as HTMLElement
}

describe('theme persistence', () => {
  it('defaults to dark and only accepts light when explicitly stored', () => {
    expect(normalizeTheme(undefined)).toBe('dark')
    expect(normalizeTheme('light')).toBe('light')
    expect(normalizeTheme('auto')).toBe('dark')
    const storage = {
      data: {} as Record<string, string>,
      getItem(key: string) {
        return this.data[key] ?? null
      },
      setItem(key: string, value: string) {
        this.data[key] = value
      },
    }
    expect(readStoredTheme(storage)).toBe('dark')
    persistTheme('light', storage)
    expect(storage.data[THEME_STORAGE_KEY]).toBe('light')
    expect(readStoredTheme(storage)).toBe('light')
  })

  it('toggles html classes without following a missing value as light', () => {
    const root = stubRoot()
    applyThemeClass('light', root)
    expect(root.classList.contains('light')).toBe(true)
    expect(root.classList.contains('dark')).toBe(false)
    expect(root.style.colorScheme).toBe('light')
    applyThemeClass('dark', root)
    expect(root.classList.contains('dark')).toBe(true)
    expect(root.classList.contains('light')).toBe(false)
  })
})
