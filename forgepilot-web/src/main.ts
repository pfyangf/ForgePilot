import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/base.css'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { applyAppLocale, elementPlusLocale } from '@/domain/locale'
import { applyThemeClass, readStoredTheme } from '@/domain/theme'

applyAppLocale()
applyThemeClass(readStoredTheme(localStorage), document.documentElement)

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: elementPlusLocale })
  .mount('#app')
