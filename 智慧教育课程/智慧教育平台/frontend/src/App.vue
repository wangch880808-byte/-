<template>
  <el-config-provider :locale="zhCn">
    <el-result v-if="failed" icon="error" title="页面出现异常" sub-title="请刷新页面后重试。">
      <template #extra><el-button type="primary" @click="reload">刷新页面</el-button></template>
    </el-result>
    <router-view v-else />
  </el-config-provider>
</template>
<script setup>
import { ref, onErrorCaptured } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
const failed = ref(false)
onErrorCaptured(() => { failed.value = true; return false })
function reload() { window.location.reload() }
</script>
