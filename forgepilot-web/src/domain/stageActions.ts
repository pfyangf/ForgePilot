import { isLoopStage, stageIndex } from './pipeline'

export type NodeActionType = 'ai' | 'import' | 'project' | 'defect' | 'review-task' | 'focus'

export interface NodeActionDef {
  label: string
  type: NodeActionType
  stage?: string
  scene?: string
  target?: string
}

export interface StageNodeActions {
  stage: string
  actions: NodeActionDef[]
}

export type StageActionAudience = 'current' | 'lookback' | 'upcoming'

export const ALL_NODE_ACTIONS: StageNodeActions[] = [
  { stage: '提出需求', actions: [{ label: '查看', type: 'focus', stage: '提出需求' }] },
  {
    stage: '需求分析',
    actions: [
      { label: 'AI 分析草稿', type: 'ai', scene: 'REQUIREMENT_ANALYSIS', target: 'draft' },
      { label: '修改', type: 'focus', stage: '需求分析' },
    ],
  },
  {
    stage: '需求评审',
    actions: [
      { label: 'AI 评审意见', type: 'ai', scene: 'REQUIREMENT_REVIEW', target: 'reviewComment' },
      { label: '查看', type: 'focus', stage: '需求评审' },
    ],
  },
  {
    stage: '设计文档',
    actions: [
      { label: 'AI 改写', type: 'ai', scene: 'DESIGN_REWRITE', target: 'designBody' },
      { label: '导入', type: 'import' },
      { label: '编辑', type: 'focus', stage: '设计文档' },
    ],
  },
  {
    stage: '设计文档评审',
    actions: [
      { label: 'AI 评审意见', type: 'ai', scene: 'DESIGN_REVIEW', target: 'designReviewComment' },
      { label: '查看', type: 'focus', stage: '设计文档评审' },
    ],
  },
  { stage: 'AI Coding 绑定', actions: [{ label: '配置', type: 'project' }] },
  { stage: '仓库绑定', actions: [{ label: '配置仓库', type: 'project' }] },
  { stage: '开发', actions: [{ label: '打开任务', type: 'focus', stage: '开发' }] },
  { stage: '代码评审', actions: [{ label: '打开待审任务', type: 'review-task' }] },
  { stage: '测试', actions: [{ label: 'AI 测试要点', type: 'ai', scene: 'TEST_POINTS', target: 'testPoints' }] },
  {
    stage: '缺陷登记',
    actions: [
      { label: 'AI 缺陷描述', type: 'ai', scene: 'DEFECT_DESCRIPTION', target: 'newDefectDesc' },
      { label: '登记', type: 'defect' },
    ],
  },
  { stage: '缺陷修改', actions: [{ label: '查看缺陷', type: 'focus', stage: '缺陷登记' }] },
  { stage: '回归测试', actions: [{ label: '查看缺陷', type: 'focus', stage: '缺陷登记' }] },
  {
    stage: '验收测试',
    actions: [
      { label: 'AI 验收清单', type: 'ai', scene: 'ACCEPTANCE', target: 'acceptComment' },
      { label: '修改', type: 'focus', stage: '验收测试' },
    ],
  },
  {
    stage: '发布',
    actions: [
      { label: 'AI 发布说明', type: 'ai', scene: 'RELEASE_NOTES', target: 'releaseNotes' },
      { label: '配置版本', type: 'focus', stage: '发布' },
    ],
  },
]

const LOOKBACK_TYPES: NodeActionType[] = ['focus', 'project', 'review-task']

export function stageActionAudience(stage: string, currentStage: string, status: string): StageActionAudience {
  const currentIdx = stageIndex(currentStage)
  const index = stageIndex(stage)
  if (index < 0) return 'upcoming'
  if (status === '已发布' && stage === '发布') return 'lookback'
  if (status === '已取消') return index <= currentIdx ? 'lookback' : 'upcoming'
  if (stage === currentStage) return 'current'
  if (index < currentIdx) return 'lookback'
  return 'upcoming'
}

export function actionsForAudience(node: StageNodeActions, audience: StageActionAudience): NodeActionDef[] {
  if (audience === 'upcoming') return []
  if (audience === 'lookback') {
    const kept = node.actions.filter((action) => LOOKBACK_TYPES.includes(action.type))
    return kept.length ? kept : [{ label: '查看', type: 'focus', stage: node.stage }]
  }
  return node.actions
}

export function visibleNodeActions(options: {
  currentStage: string
  status: string
  focusedStage?: string
  showDefectLoop?: boolean
}): { title: string; hint: string; audience: StageActionAudience; nodes: StageNodeActions[] } {
  const focused = options.focusedStage || options.currentStage
  const audience = stageActionAudience(focused, options.currentStage, options.status)
  const hideLoop = !options.showDefectLoop && isLoopStage(focused)
  const source = ALL_NODE_ACTIONS.find((item) => item.stage === focused)
  const nodes =
    hideLoop || !source
      ? []
      : [{ stage: source.stage, actions: actionsForAudience(source, audience) }].filter((item) => item.actions.length > 0)

  if (audience === 'lookback') {
    return {
      title: `回看：${focused}`,
      hint: '这是已完成环节，可回看内容。当前要处理的动作仍在上方「当前环节」。',
      audience,
      nodes,
    }
  }
  if (audience === 'upcoming') {
    return {
      title: `尚未到达：${focused}`,
      hint: '尚未到达该环节，所以不展示这里的按钮。当前可做的事在上方「当前环节」。',
      audience,
      nodes: [],
    }
  }
  return {
    title: `当前环节动作：${focused}`,
    hint: '根据当前状态、门禁和待确认建议，只显示这一环节的动作。',
    audience,
    nodes,
  }
}
