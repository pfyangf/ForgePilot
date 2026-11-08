<template>
  <el-card>
    <template #header>{{ title }}</template>
    <p>{{ note }}</p>
    <el-table :data="rows">
      <el-table-column prop="layer" label="层次" width="120" />
      <el-table-column prop="kind" label="种类" width="100" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="agent" label="进入 Agent" width="120" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? '规约与文档'))
const note = computed(() => String(route.meta.note ?? ''))
const rows = [
  { layer: '平台', kind: '规约', title: '提交说明', agent: '是' },
  { layer: '公司', kind: '规约', title: '提交说明', agent: '是，覆盖平台同标题' },
  { layer: '项目', kind: '规约', title: '模块边界', agent: '是' },
  { layer: '项目', kind: '文档', title: '架构笔记', agent: '否，只给人看' },
]
</script>
