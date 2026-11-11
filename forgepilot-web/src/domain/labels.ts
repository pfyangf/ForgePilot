export type StatusTone = 'warning' | 'primary' | 'success' | 'danger' | 'muted' | ''

export function taskStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待执行',
    PREPARING: '准备中',
    RUNNING: '执行中',
    WAITING_USER: '待处理',
    VERIFYING: '验证中',
    WAITING_APPROVAL: '待审核',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

export function statusTone(status: string): StatusTone {
  if (['待验收', 'WAITING_APPROVAL', '待需求评审', '待设计评审', '待开发', 'PENDING', 'PREPARING'].includes(status)) {
    return status === '待验收' || status === 'WAITING_APPROVAL' ? 'warning' : 'primary'
  }
  if (['测试中', '开发中', 'RUNNING', 'VERIFYING', 'IN_FIX'].includes(status)) return 'primary'
  if (['已完成', '已发布', 'COMPLETED', 'CLOSED', 'VERIFIED', 'FIXED'].includes(status)) return 'success'
  if (['FAILED', '已取消', 'CANCELLED', 'REJECTED', 'OPEN'].includes(status)) {
    return status === 'OPEN' ? 'warning' : 'danger'
  }
  return 'muted'
}

export function progressPercent(done: number, total: number) {
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((done / total) * 100)))
}
