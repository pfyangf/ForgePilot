import { ref } from 'vue'
import { applyThemeClass, normalizeTheme, persistTheme, readStoredTheme, type ThemeName } from '@/domain/theme'

const theme = ref<ThemeName>(readStoredTheme(typeof localStorage === 'undefined' ? null : localStorage))

export function useTheme() {
  function setTheme(next: ThemeName) {
    const value = normalizeTheme(next)
    theme.value = value
    applyThemeClass(value, document.documentElement)
    persistTheme(value, localStorage)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  applyThemeClass(theme.value, typeof document === 'undefined' ? null : document.documentElement)

  return { theme, setTheme, toggleTheme }
}
