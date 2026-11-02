<template>
  <div class="app-container">
    <el-empty :description="route.meta.reason || '该功能的后端接口尚未提供。'">
      <template #default>
        <p class="pending-note">这里不会使用本地假数据。接口就绪后，本页会改为请求对应后端接口。</p>
      </template>
    </el-empty>
  </div>
</template>
<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<style scoped>
.pending-note { margin: 0; color: #8a96a9; font-size: 13px; }
</style>
