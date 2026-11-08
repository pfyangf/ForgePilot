<template>
  <el-card>
    <template #header>{{ title }}</template>
    <p>{{ note }}</p>
    <el-button type="primary" @click="router.push('/')">回到工作台</el-button>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const title = computed(() => String(route.meta.title ?? '尚未实现'))
const note = computed(() => String(route.meta.note ?? '这个页面还是骨架。'))
</script>
