<template>
  <el-tooltip :content="text" placement="top" effect="dark" :show-after="150" :trigger="['hover', 'focus']">
    <button class="fp-help-tip" type="button" :aria-label="text">
      <span aria-hidden="true">?</span>
    </button>
  </el-tooltip>
</template>

<script setup lang="ts">
defineProps<{
  text: string
}>()
</script>
