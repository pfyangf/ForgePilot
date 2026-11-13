import zhCn from 'element-plus/es/locale/lang/zh-cn'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

/** Product UI and AI-facing copy always use Simplified Chinese, never the browser language. */
export const APP_LOCALE = 'zh-CN' as const
export const elementPlusLocale = zhCn

dayjs.locale('zh-cn')

export function applyAppLocale(root: HTMLElement | null | undefined = typeof document === 'undefined' ? null : document.documentElement) {
  if (!root) return
  root.setAttribute('lang', APP_LOCALE)
  root.setAttribute('data-fp-locale', APP_LOCALE)
}

export function isSimplifiedChineseLocale(locale: { name?: string } | string | null | undefined) {
  const name = typeof locale === 'string' ? locale : locale?.name
  return name === 'zh-cn' || name === 'zh-CN'
}
