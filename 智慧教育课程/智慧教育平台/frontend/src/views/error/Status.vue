<template>
  <div class="status-page">
    <el-result :icon="status === '403' ? 'warning' : 'info'" :title="content.title" :sub-title="content.text">
      <template #extra><el-button type="primary" @click="retry">{{ status === 'connection' ? '重新连接' : '返回首页' }}</el-button>
        <el-button v-if="status === 'connection'" @click="relogin">重新登录</el-button></template>
    </el-result>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ROLE_HOME, safeRedirect } from '@/utils/access'
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const status = computed(() => route.meta.status)
const content = computed(() => ({
  connection: { title: '暂时无法连接服务', text: '请检查后端服务及网络连接，然后重试。' },
  '403': { title: '无访问权限', text: '当前账号无权访问此页面，请联系管理员确认权限。' },
  '404': { title: '页面不存在', text: '页面地址可能有误，也可能尚未开放。' }
}[status.value] || { title: '正在进入工作空间', text: '' }))
function retry() { router.replace(safeRedirect(route.query.next, ROLE_HOME[user.role] || '/')) }
function relogin() { user.reset(); router.replace('/login') }
</script>
