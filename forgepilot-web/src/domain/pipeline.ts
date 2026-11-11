export const PIPELINE_STAGE_IDS = [
  '提出需求',
  '需求分析',
  '需求评审',
  '设计文档',
  '设计文档评审',
  'AI Coding 绑定',
  '仓库绑定',
  '开发',
  '代码评审',
  '测试',
  '缺陷登记',
  '缺陷修改',
  '回归测试',
  '验收测试',
  '发布',
] as const

export type PipelineStageId = (typeof PIPELINE_STAGE_IDS)[number]
export type NodeVisualState = 'done' | 'current' | 'todo' | 'loop'

export interface PipelineStageDef {
  id: PipelineStageId
  label: string
  group: '需求准备' | '设计与绑定' | '研发执行' | '缺陷与交付'
  loop?: boolean
  section: 'flow' | 'docs' | 'tasks' | 'defects' | 'project'
}

export const PIPELINE_STAGES: PipelineStageDef[] = [
  { id: '提出需求', label: '提出', group: '需求准备', section: 'flow' },
  { id: '需求分析', label: '分析', group: '需求准备', section: 'flow' },
  { id: '需求评审', label: '需求评审', group: '需求准备', section: 'flow' },
  { id: '设计文档', label: '设计文档', group: '设计与绑定', section: 'docs' },
  { id: '设计文档评审', label: '设计评审', group: '设计与绑定', section: 'docs' },
  { id: 'AI Coding 绑定', label: '工具与模型', group: '设计与绑定', section: 'project' },
  { id: '仓库绑定', label: '仓库分支', group: '设计与绑定', section: 'project' },
  { id: '开发', label: '开发', group: '研发执行', section: 'tasks' },
  { id: '代码评审', label: '代码评审', group: '研发执行', section: 'tasks' },
  { id: '测试', label: '测试', group: '研发执行', section: 'tasks' },
  { id: '缺陷登记', label: '缺陷登记', group: '缺陷与交付', loop: true, section: 'defects' },
  { id: '缺陷修改', label: '缺陷修改', group: '缺陷与交付', loop: true, section: 'defects' },
  { id: '回归测试', label: '回归', group: '缺陷与交付', section: 'defects' },
  { id: '验收测试', label: '验收', group: '缺陷与交付', section: 'flow' },
  { id: '发布', label: '发布', group: '缺陷与交付', section: 'flow' },
]

export const PIPELINE_GROUPS = [
  { name: '需求准备', stageIds: ['提出需求', '需求分析', '需求评审'] as PipelineStageId[] },
  { name: '设计与绑定', stageIds: ['设计文档', '设计文档评审', 'AI Coding 绑定', '仓库绑定'] as PipelineStageId[] },
  { name: '研发执行', stageIds: ['开发', '代码评审', '测试'] as PipelineStageId[] },
  { name: '缺陷与交付', stageIds: ['回归测试', '验收测试', '发布'] as PipelineStageId[] },
]

export const LOOP_STAGE_IDS: PipelineStageId[] = ['缺陷登记', '缺陷修改']

export const MINI_PIPELINE = [
  { key: 'propose', label: '提出', stages: ['提出需求', '需求分析', '需求评审'] },
  { key: 'design', label: '设计', stages: ['设计文档', '设计文档评审', 'AI Coding 绑定', '仓库绑定'] },
  { key: 'develop', label: '开发', stages: ['开发', '代码评审'] },
  { key: 'test', label: '测试', stages: ['测试', '缺陷登记', '缺陷修改', '回归测试'] },
  { key: 'accept', label: '验收', stages: ['验收测试'] },
  { key: 'release', label: '发布', stages: ['发布'] },
] as const

export const IN_PROGRESS_STATUSES = ['待分析', '待需求评审', '设计中', '待设计评审', '待开发', '开发中', '测试中', '待验收', '已完成']

export interface DefectLike {
  status?: string
}

export function stageIndex(stage: string) {
  return PIPELINE_STAGE_IDS.indexOf(stage as PipelineStageId)
}

export function stageDef(stage: string) {
  return PIPELINE_STAGES.find((item) => item.id === stage)
}

export function stageLabel(stage: string) {
  return stageDef(stage)?.label || stage
}

export function isLoopStage(stage: string) {
  return LOOP_STAGE_IDS.includes(stage as PipelineStageId)
}

export function shouldShowDefectLoop(currentStage: string, defects: DefectLike[] | undefined) {
  const index = stageIndex(currentStage)
  if (index === 10 || index === 11 || index === 12) return true
  return Boolean(defects && defects.length > 0)
}

export function defectLoopCaption(currentStage: string, defects: DefectLike[] | undefined) {
  const list = defects || []
  if (!list.length) {
    return currentStage.startsWith('缺陷') || currentStage === '回归测试'
      ? '从测试分出的缺陷回路'
      : ''
  }
  const open = list.some((item) => item.status === 'OPEN' || item.status === 'IN_FIX')
  const fixed = list.some((item) => item.status === 'FIXED')
  if (open) return '从测试分出的缺陷回路，处理中'
  if (fixed) return '缺陷已修复，等待回归'
  return '从测试分出的缺陷回路，已关闭'
}

export function nodeVisualState(
  stage: string,
  currentStage: string,
  currentStageIndex = stageIndex(currentStage),
): NodeVisualState {
  const index = stageIndex(stage)
  if (index < 0) return 'todo'
  if (stage === currentStage) {
    return isLoopStage(stage) ? 'loop' : 'current'
  }
  if (currentStageIndex > index) {
    return isLoopStage(stage) ? 'loop' : 'done'
  }
  return 'todo'
}

export function edgeState(
  fromStage: string,
  currentStage: string,
  currentStageIndex = stageIndex(currentStage),
): 'done' | 'current' | 'todo' {
  const fromIndex = stageIndex(fromStage)
  if (currentStageIndex > fromIndex) return 'done'
  if (fromStage === currentStage) return 'current'
  return 'todo'
}

export interface MiniNode {
  key: string
  label: string
  state: NodeVisualState
}

export function miniPipeline(status: string, currentStage?: string): MiniNode[] {
  const resolvedStage = currentStage || currentStageFromStatus(status)
  const currentIndex = stageIndex(resolvedStage)
  return MINI_PIPELINE.map((item) => {
    const indexes = item.stages.map((stage) => stageIndex(stage)).filter((value) => value >= 0)
    const start = Math.min(...indexes)
    const end = Math.max(...indexes)
    let state: NodeVisualState = 'todo'
    if (status === '已发布' || currentIndex > end) {
      state = 'done'
    } else if (currentIndex >= start && currentIndex <= end) {
      state = item.key === 'test' && isLoopStage(resolvedStage) ? 'loop' : 'current'
    }
    if (status === '已取消') {
      state = currentIndex > end ? 'done' : 'todo'
    }
    return { key: item.key, label: item.label, state }
  })
}

export function currentStageFromStatus(status: string): PipelineStageId {
  switch (status) {
    case '待分析':
      return '需求分析'
    case '待需求评审':
      return '需求评审'
    case '设计中':
      return '设计文档'
    case '待设计评审':
      return '设计文档评审'
    case '待开发':
      return '开发'
    case '开发中':
      return '开发'
    case '测试中':
      return '测试'
    case '待验收':
      return '验收测试'
    case '已完成':
    case '已发布':
      return '发布'
    default:
      return '提出需求'
  }
}

export function flowBoardTitle(currentStage: string, status: string) {
  if (status === '已发布') return '需求已发布，这条流水线已经走完'
  if (status === '已完成') return '验收已通过，发布前请确认要合并的目标分支'
  if (status === '已取消') return '这条需求已取消'
  if (status === '待验收') return '当前停在验收测试，发布还不能开始'
  if (status === '开发中' && currentStage === '代码评审') return '当前停在代码评审，平台不会自动合并'
  return `当前停在${currentStage}，平台会托管推进，需要你确认时再停下来`
}

export function isInProgressRequirement(status: string) {
  return IN_PROGRESS_STATUSES.includes(status)
}

export function stageAnchorId(stage: string) {
  if (stage === '缺陷修改' || stage === '回归测试') return 'stage-缺陷登记'
  return `stage-${stage}`
}
