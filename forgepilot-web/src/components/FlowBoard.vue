<template>
  <section class="fp-card fp-flow-board" aria-label="可视化研发流程">
    <div class="fp-flow-board__head">
      <div>
        <div class="fp-flow-board__kicker">研发流水线</div>
        <h2 class="fp-flow-board__title">{{ title }}</h2>
      </div>
      <div class="fp-flow-legend">
        <span><i class="done"></i>已完成</span>
        <span><i class="now"></i>进行中</span>
        <span><i class="loop"></i>缺陷回路</span>
        <span><i class="todo"></i>未开始</span>
      </div>
    </div>
    <div class="fp-flow-canvas">
      <div class="fp-flow-groups">
        <div v-for="group in groups" :key="group.name" class="fp-flow-group">
          <div class="fp-flow-group__name">{{ group.name }}</div>
          <div class="fp-flow-track">
            <template v-for="(stage, index) in group.stages" :key="stage.id">
              <button
                class="fp-flow-node"
                :class="`is-${stateOf(stage.id)}`"
                type="button"
                :title="stage.id"
                @click="emit('select', stage.id)"
              >
                <span class="fp-flow-node__orb" aria-hidden="true">
                  <svg v-if="stateOf(stage.id) === 'done' || stateOf(stage.id) === 'loop'" viewBox="0 0 12 12" fill="none">
                    <path d="M2.2 6.2l2.4 2.4 5.2-5.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <svg v-else-if="stateOf(stage.id) === 'current'" viewBox="0 0 12 12">
                    <circle cx="6" cy="6" r="2.5" fill="currentColor" />
                  </svg>
                  <svg v-else viewBox="0 0 12 12" fill="none">
                    <circle cx="6" cy="6" r="3.1" stroke="currentColor" stroke-width="1.4" />
                  </svg>
                </span>
                <span class="fp-flow-node__name">{{ stage.label }}</span>
              </button>
              <span
                v-if="index < group.stages.length - 1"
                class="fp-flow-edge"
                :class="`is-${edgeOf(stage.id)}`"
              />
            </template>
          </div>
          <div v-if="group.name === '缺陷与交付' && showLoop" class="fp-flow-loop">
            <div class="fp-flow-loop__cap">{{ loopCaption || '从测试分出的缺陷回路' }}</div>
            <div class="fp-flow-track">
              <template v-for="(stage, index) in loopStages" :key="stage.id">
                <button
                  class="fp-flow-node"
                  :class="`is-${stateOf(stage.id)}`"
                  type="button"
                  :title="stage.id"
                  @click="emit('select', stage.id)"
                >
                  <span class="fp-flow-node__orb" aria-hidden="true">
                    <svg viewBox="0 0 12 12" fill="none">
                      <path d="M2.2 6.2l2.4 2.4 5.2-5.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                  <span class="fp-flow-node__name">{{ stage.label }}</span>
                </button>
                <span v-if="index < loopStages.length - 1" class="fp-flow-edge is-done" />
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  LOOP_STAGE_IDS,
  PIPELINE_GROUPS,
  PIPELINE_STAGES,
  edgeState,
  nodeVisualState,
  stageDef,
  type PipelineStageId,
} from '@/domain/pipeline'

const props = defineProps<{
  currentStage: string
  currentStageIndex?: number
  title: string
  showLoop?: boolean
  loopCaption?: string
}>()

const emit = defineEmits<{ select: [stage: string] }>()

const currentIndex = computed(() =>
  props.currentStageIndex == null
    ? PIPELINE_STAGES.findIndex((item) => item.id === props.currentStage)
    : props.currentStageIndex,
)

const groups = computed(() =>
  PIPELINE_GROUPS.map((group) => ({
    name: group.name,
    stages: group.stageIds.map((id) => stageDef(id)!).filter(Boolean),
  })),
)

const loopStages = computed(() => LOOP_STAGE_IDS.map((id) => stageDef(id)!).filter(Boolean))

function stateOf(stage: PipelineStageId | string) {
  return nodeVisualState(stage, props.currentStage, currentIndex.value)
}

function edgeOf(stage: PipelineStageId | string) {
  return edgeState(stage, props.currentStage, currentIndex.value)
}
</script>
