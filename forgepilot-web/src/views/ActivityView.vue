<template>
  <el-card>
    <template #header>操作记录与日报</template>
    <p>这里以后查询本公司的操作记录，并导出当日摘要。骨架接口还没接上，也不会返回密钥。</p>
    <el-button @click="load">请求日报占位接口</el-button>
    <pre v-if="message" class="log">{{ message }}</pre>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const message = ref('')

async function load() {
  const response = await fetch('/api/v1/reports/daily')
  message.value = `${response.status} ${await response.text()}`
}
</script>
