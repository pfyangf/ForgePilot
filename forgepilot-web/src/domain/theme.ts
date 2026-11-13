export const THEME_STORAGE_KEY = 'fp.theme'
export const THEMES = ['dark', 'light'] as const
export type ThemeName = (typeof THEMES)[number]

export function isThemeName(value: unknown): value is ThemeName {
  return value === 'dark' || value === 'light'
}

/** Default is dark. Never follow the browser or OS locale/theme automatically. */
export function normalizeTheme(value: unknown): ThemeName {
  return value === 'light' ? 'light' : 'dark'
}

export function readStoredTheme(storage?: Pick<Storage, 'getItem'> | null): ThemeName {
  try {
    return normalizeTheme(storage?.getItem(THEME_STORAGE_KEY))
  } catch {
    return 'dark'
  }
}

export function persistTheme(theme: ThemeName, storage?: Pick<Storage, 'setItem'> | null) {
  try {
    storage?.setItem(THEME_STORAGE_KEY, normalizeTheme(theme))
  } catch {
    /* private mode or missing storage */
  }
}

export function applyThemeClass(theme: ThemeName, root: HTMLElement | null | undefined) {
  if (!root) return
  const next = normalizeTheme(theme)
  root.classList.toggle('dark', next === 'dark')
  root.classList.toggle('light', next === 'light')
  root.style.colorScheme = next
  if (!root.getAttribute('lang')) {
    root.setAttribute('lang', 'zh-CN')
  }
}
