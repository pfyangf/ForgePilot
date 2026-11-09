export interface SidebarItem {
  path: string
  label: string
  codes: string[]
  icon: string
}

const GUIDANCE_LABELS: Record<string, string> = {
  'platform-guidance': '平台规约与文档',
  'tenant-guidance': '公司规约与文档',
  'project-guidance': '项目规约与文档',
}

export const SIDEBAR_ITEMS: SidebarItem[] = [
  { path: '/', label: '工作台', codes: ['dashboard'], icon: 'Odometer' },
  { path: '/projects', label: '项目管理', codes: ['projects'], icon: 'Folder' },
  { path: '/coding', label: 'AI Coding', codes: ['ai-coding'], icon: 'MagicStick' },
  { path: '/delivery', label: '测试与交付', codes: ['delivery'], icon: 'Upload' },
  { path: '/guidance', label: '规约与文档', codes: ['platform-guidance', 'tenant-guidance', 'project-guidance'], icon: 'Document' },
  { path: '/catalog', label: '模型与工具', codes: ['catalog'], icon: 'Cpu' },
  { path: '/activity', label: '操作记录', codes: ['activity'], icon: 'Notebook' },
  { path: '/nodes', label: '执行节点', codes: ['nodes'], icon: 'Connection' },
  { path: '/admin', label: '平台管理', codes: ['admin'], icon: 'Setting' },
]

export function visibleSidebarItems(menus: string[] | undefined | null): SidebarItem[] {
  const granted = new Set(menus ?? [])
  return SIDEBAR_ITEMS.filter((item) => item.codes.some((code) => granted.has(code))).map((item) => {
    if (item.path !== '/guidance') {
      return item
    }
    const matched = item.codes.filter((code) => granted.has(code))
    if (matched.length === 1) {
      return { ...item, label: GUIDANCE_LABELS[matched[0]] ?? item.label }
    }
    return item
  })
}

export function firstMenuPath(menus: string[] | undefined | null): string {
  return visibleSidebarItems(menus)[0]?.path ?? '/'
}

export function canAccessPath(path: string, menus: string[] | undefined | null): boolean {
  const granted = new Set(menus ?? [])
  if (path === '/' || path.startsWith('/requirements') || path.startsWith('/tasks')) {
    return granted.has('dashboard')
  }
  if (path.startsWith('/projects')) {
    return granted.has('projects')
  }
  if (path.startsWith('/coding')) {
    return granted.has('ai-coding')
  }
  if (path.startsWith('/delivery')) {
    return granted.has('delivery')
  }
  if (path.startsWith('/guidance')) {
    return ['platform-guidance', 'tenant-guidance', 'project-guidance'].some((code) => granted.has(code))
  }
  if (path.startsWith('/catalog')) {
    return granted.has('catalog')
  }
  if (path.startsWith('/activity')) {
    return granted.has('activity')
  }
  if (path.startsWith('/nodes')) {
    return granted.has('nodes')
  }
  if (path.startsWith('/admin')) {
    return granted.has('admin')
  }
  return true
}
