/** Short hover help shown beside a few important workbench controls. */
export const HELP = {
  pmoConfirm:
    '确认后只会创建待处理任务，不会自动开工，也不会自动通过评审、验收或发布。',
  pmoReturn: '退回后，在情况变化之前不会再出现这条建议。可填写原因（可选）。',
  inbox:
    '这里只列出当前需要你确认的事项，不会把所有环节的动作一次摊开。点开需求后，可按当前环节处理。',
  repoBranch:
    '源分支是编码开始时检出的基线；工作分支是本次任务新建的分支名，默认按需求号生成，Agent 不得自行另起分支名。',
  releaseTarget:
    '发布时把工作分支合并进这里选择的目标分支。代码评审通过不会自动合并，合并只发生在你确认发布之后。',
  toolModelBind:
    '按「编码工具 → 模型供应商 → 模型」绑定。启动 IMPLEMENT / BUGFIX 任务前必须完成绑定，并通过需求和设计评审。',
  reviewDecision: '以本轮结论为准。通过会进入下一环节，驳回会退回上一环节。审核通过不会自动合并代码。',
  themeSwitch: '在深色和浅色主题之间切换。选择保存在本机，刷新后仍然有效。默认是深色主题。',
  stageActions:
    '流水线会展示从分析到发布的完整路径。按钮只按当前环节、门禁和待确认的平台建议显示；已完成环节可点开回看。',
} as const

export type HelpKey = keyof typeof HELP
