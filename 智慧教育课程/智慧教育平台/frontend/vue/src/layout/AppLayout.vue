<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission, logout, session } from '../stores/session'
import { menuByRole, roleLabels } from '../config/menu'

const route = useRoute()
const router = useRouter()
const navOpen = ref(false)
const forbidden = ref('')
// 部署到 /vue/ 时 public 资源前缀随 BASE_URL 变化
const logoUrl = `${import.meta.env.BASE_URL}logo.png`
let forbiddenTimer = null

const groups = computed(() =>
  (menuByRole[session.role] || [])
    .map((group) => ({ ...group, items: group.items.filter((item) => hasPermission(item.permission)) }))
    .filter((group) => group.items.length)
)

const roleLabel = computed(() => roleLabels[session.role] || '')
const userLabel = computed(() => session.user?.nickName || session.user?.userName || '')

const flatItems = computed(() => groups.value.flatMap((group) => group.items.map((item) => ({ ...item, group: group.title }))))
const currentItem = computed(() => flatItems.value.find((item) => linkOf(item) === route.path))
const breadcrumb = computed(() => {
  if (!currentItem.value) return roleLabel.value ? `${roleLabel.value} / 首页` : '首页'
  return `${currentItem.value.group} / ${currentItem.value.title}`
})

function linkOf(item) {
  return `/${session.role}${item.path ? '/' + item.path : ''}`
}

function onForbidden(event) {
  forbidden.value = event.detail || '没有访问该操作的权限'
  clearTimeout(forbiddenTimer)
  forbiddenTimer = setTimeout(() => (forbidden.value = ''), 4000)
}

async function doLogout() {
  await logout()
  router.replace('/login')
}

onMounted(() => window.addEventListener('app:forbidden', onForbidden))
onUnmounted(() => {
  window.removeEventListener('app:forbidden', onForbidden)
  clearTimeout(forbiddenTimer)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">
        <button class="nav-toggle" type="button" aria-label="展开或收起导航" @click="navOpen = !navOpen">☰</button>
        <img :src="logoUrl" alt="校徽" />
        <span>知教·智慧教育课程平台</span>
      </div>
      <div class="header-right">
        <span class="role-tag">{{ roleLabel }}</span>
        <span class="user">{{ userLabel }}</span>
        <button class="ui-btn" type="button" @click="doLogout">退出登录</button>
      </div>
    </header>

    <div class="app-body">
      <aside class="app-sidebar" :class="{ open: navOpen }">
        <template v-if="groups.length">
          <div v-for="group in groups" :key="group.title" class="menu-group">
            <div class="menu-title">{{ group.title }}</div>
            <router-link
              v-for="item in group.items"
              :key="item.path"
              class="menu-item"
              :class="{ active: linkOf(item) === route.path }"
              :to="linkOf(item)"
              @click="navOpen = false"
            >
              {{ item.title }}
            </router-link>
          </div>
        </template>
        <p v-else class="menu-empty">暂无可用功能</p>
      </aside>

      <main class="app-main">
        <div class="breadcrumb">{{ breadcrumb }}</div>
        <div v-if="forbidden" class="alert warn">{{ forbidden }}</div>
        <div class="page">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>
