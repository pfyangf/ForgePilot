<template>
  <div class="fp-mini">
    <div class="fp-mini-flow" aria-hidden="true">
      <template v-for="(node, index) in nodes" :key="node.key">
        <span :class="`is-${node.state}`"></span>
        <i v-if="index < nodes.length - 1" :class="{ 'is-done': node.state === 'done' }"></i>
      </template>
    </div>
    <div class="fp-mini-flow-labels">
      <span v-for="node in nodes" :key="node.key + '-label'">{{ node.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { miniPipeline, type MiniNode } from '@/domain/pipeline'

const props = defineProps<{ status: string; currentStage?: string }>()
const nodes = computed<MiniNode[]>(() => miniPipeline(props.status, props.currentStage))
</script>
