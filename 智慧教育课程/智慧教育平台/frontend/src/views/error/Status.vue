<template>
  <div class="status-page">
    <el-result :icon="status === '403' ? 'warning' : 'info'" :title="content.title" :sub-title="content.text">
      <template #extra>
        <el-button type="primary" :loading="retrying" @click="retry">{{ status === 'connection' ? '重新连接' : '返回首页' }}</el-button>
        <el-button v-if="status === 'connection'" @click="relogin">重新登录</el-button>
      </template>
    </el-result>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { ROLE_HOME, safeRedirect } from '@/utils/access'
import { getToken } from '@/utils/auth'
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const retrying = ref(false)
const status = computed(() => route.meta.status)
const content = computed(() => ({
  connection: {
    title: '暂时无法连接服务',
    text: '多半是后端刚重启或 8080 暂时连不上。确认 IDEA 里 RuoYiApplication 已启动后，点“重新连接”。'
  },
  '403': { title: '无访问权限', text: '当前账号无权访问此页面，请联系管理员确认权限。' },
  '404': { title: '页面不存在', text: '页面地址可能有误，也可能尚未开放。' }
}[status.value] || { title: '正在进入工作空间', text: '' }))
async function retry() {
  if (status.value !== 'connection') {
    router.replace(safeRedirect(route.query.next, ROLE_HOME[user.role] || '/'))
    return
  }
  retrying.value = true
  try {
    if (!getToken()) {
      router.replace('/login')
      return
    }
    user.loaded = false
    await user.loadIdentity()
    router.replace(safeRedirect(route.query.next, ROLE_HOME[user.role] || '/'))
  } catch (error) {
    if (error?.kind === 'unauthorized') {
      user.reset()
      router.replace({ path: '/login', query: { reason: 'expired' } })
      return
    }
    ElMessage.error(error?.kind === 'network'
      ? '仍然连不上后端，请确认 IDEA 已启动且端口是 8080'
      : (error?.message || '重新连接失败'))
  } finally {
    retrying.value = false
  }
}
function relogin() { user.reset(); router.replace('/login') }
</script>
