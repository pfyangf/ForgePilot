import { describe, expect, it } from 'vitest'
import {
  currentStageFromStatus,
  defectLoopCaption,
  edgeState,
  flowBoardTitle,
  miniPipeline,
  nodeVisualState,
  shouldShowDefectLoop,
  stageIndex,
} from './pipeline'

describe('pipeline visual states', () => {
  it('marks completed and current nodes on the main track', () => {
    expect(nodeVisualState('提出需求', '验收测试')).toBe('done')
    expect(nodeVisualState('验收测试', '验收测试')).toBe('current')
    expect(nodeVisualState('发布', '验收测试')).toBe('todo')
    expect(edgeState('验收测试', '验收测试')).toBe('current')
    expect(edgeState('回归测试', '验收测试')).toBe('done')
  })

  it('uses loop styling for defect nodes that have been reached', () => {
    expect(nodeVisualState('缺陷登记', '缺陷修改')).toBe('loop')
    expect(nodeVisualState('缺陷修改', '缺陷修改')).toBe('loop')
    expect(nodeVisualState('回归测试', '缺陷修改')).toBe('todo')
    expect(nodeVisualState('测试', '缺陷修改')).toBe('done')
  })

  it('shows the defect loop when defects exist or the current stage is in the loop', () => {
    expect(shouldShowDefectLoop('测试', [])).toBe(false)
    expect(shouldShowDefectLoop('测试', [{ status: 'CLOSED' }])).toBe(true)
    expect(shouldShowDefectLoop('缺陷修改', [])).toBe(true)
    expect(defectLoopCaption('验收测试', [{ status: 'CLOSED' }])).toContain('已关闭')
    expect(defectLoopCaption('测试中', [{ status: 'IN_FIX' }])).toContain('处理中')
  })

  it('builds a compact workbench pipeline from status', () => {
    const acceptance = miniPipeline('待验收')
    expect(acceptance.map((item) => item.state)).toEqual(['done', 'done', 'done', 'done', 'current', 'todo'])
    const released = miniPipeline('已发布')
    expect(released.every((item) => item.state === 'done')).toBe(true)
    const coding = miniPipeline('开发中')
    expect(coding.find((item) => item.key === 'develop')?.state).toBe('current')
    const fixing = miniPipeline('测试中', '缺陷修改')
    expect(fixing.find((item) => item.key === 'test')?.state).toBe('loop')
  })

  it('maps statuses and writes a concrete board title', () => {
    expect(currentStageFromStatus('待验收')).toBe('验收测试')
    expect(stageIndex('发布')).toBe(14)
    expect(flowBoardTitle('验收测试', '待验收')).toContain('发布还不能开始')
  })
})
