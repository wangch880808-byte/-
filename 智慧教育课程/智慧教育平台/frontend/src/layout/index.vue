<template>
  <div class="workspace" :class="{ 'menu-open': menuOpen }">
    <button v-if="menuOpen" class="nav-scrim" aria-label="收起导航" @click="menuOpen = false" />
    <aside class="sidebar">
      <router-link class="brand" to="/"><img :src="logo" alt="" /><span>知教<small>智慧教育平台</small></span></router-link>
      <div class="role-caption">{{ ROLE_LABELS[user.role] }}工作空间</div>
      <nav aria-label="主导航">
        <section v-for="group in groups" :key="group.name" class="menu-group">
          <button type="button" class="group-title" @click="collapsed[group.name] = !collapsed[group.name]">
            <span>{{ group.name }}</span><span>{{ collapsed[group.name] ? '▸' : '▾' }}</span>
          </button>
          <div v-show="!collapsed[group.name]">
            <router-link v-for="item in group.children" :key="item.path" :to="item.path"
              :class="{ selected: route.path === item.path }" @click="menuOpen = false">{{ item.meta.title }}</router-link>
          </div>
        </section>
      </nav>
      <div class="sidebar-foot"><span class="online-dot" /> 教学与学习，在此连接</div>
    </aside>
    <div class="workspace-body">
      <header class="topbar">
        <div class="breadcrumb">
          <button class="menu-toggle" aria-label="展开导航" @click="menuOpen = !menuOpen">☰</button>
          <span>{{ currentGroup }}</span><span class="separator">/</span><strong>{{ route.meta.title }}</strong>
        </div>
        <div class="user-tools">
          <el-select v-if="bindings.length" :model-value="course.bindingId" aria-label="当前课程" style="width:220px" @change="course.select">
            <el-option v-for="item in bindings" :key="item.bindingId" :value="item.bindingId" :label="bindingLabel(item)" />
          </el-select>
          <el-select v-if="availableRoles.length > 1" :model-value="user.role" aria-label="切换角色" style="width:110px" @change="switchRole">
            <el-option v-for="role in availableRoles" :key="role" :value="role" :label="ROLE_LABELS[role]" />
          </el-select>
          <span class="user-name">{{ user.user?.nickName || user.user?.userName || '已登录用户' }}</span>
          <el-button :loading="leaving" @click="signOut">退出登录</el-button>
        </div>
      </header>
      <div class="workspace-tabs" v-if="workspace.tabs.length">
        <span v-for="tab in workspace.tabs" :key="tab.path" class="workspace-tab" :class="{ active: route.path === tab.path }">
          <router-link :to="tab.path">{{ tab.title }}</router-link>
          <button type="button" aria-label="关闭页签" @click="closeTab(tab.path)">×</button>
        </span>
      </div>
      <main class="page-content">
        <router-view v-slot="{ Component }"><component :is="Component" :key="route.fullPath" /></router-view>
      </main>
      <footer class="page-footer">知教 · 智慧教育课程平台</footer>
    </div>
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useCourseStore } from '@/store/course'
import { useWorkspaceStore } from '@/store/workspace'
import { businessRoutes } from '@/router/routes'
import { ROLES, ROLE_LABELS, ROLE_HOME, canAccess } from '@/utils/access'
import { setRole } from '@/utils/auth'
import logo from '../../logo.png'

const API_ROLE = { teacher: 'TEACHER', student: 'STUDENT' }
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const course = useCourseStore()
const workspace = useWorkspaceStore()
const menuOpen = ref(false)
const leaving = ref(false)
const collapsed = reactive({})
const availableRoles = computed(() => ROLES.filter(role => user.roles.includes(role)))
const groups = computed(() => {
  const list = []
  for (const item of businessRoutes) {
    if (!item.meta.roles.includes(user.role) || !canAccess(item.meta, user.roles, user.permissions)) continue
    let group = list.find(entry => entry.name === item.meta.group)
    if (!group) {
      group = { name: item.meta.group, children: [] }
      list.push(group)
    }
    group.children.push(item)
  }
  return list
})
const currentGroup = computed(() => route.meta.group || ROLE_LABELS[user.role] || '教学平台')
const bindings = computed(() => API_ROLE[user.role] ? course.forRole(API_ROLE[user.role]) : [])
function bindingLabel(item) { return `${item.courseTitle || '课程'} · ${item.className || '教学班'}` }
function closeTab(path) {
  const next = workspace.close(path)
  if (route.path === path) router.push(next?.path || ROLE_HOME[user.role])
}
function switchRole(role) {
  user.role = role
  setRole(role)
  workspace.$reset()
  const apiRole = API_ROLE[role]
  if (apiRole) course.ensure(apiRole).catch(() => {})
  router.push(ROLE_HOME[role])
}
async function signOut() {
  leaving.value = true
  try { await user.signOut() } catch { ElMessage.warning('本页已退出，服务端退出状态未确认') }
  finally {
    course.reset()
    workspace.$reset()
    leaving.value = false
    router.replace('/login')
  }
}
watch(groups, list => {
  list.forEach((group, index) => {
    if (collapsed[group.name] === undefined) collapsed[group.name] = index > 0
  })
}, { immediate: true })
watch(() => user.role, role => {
  const apiRole = API_ROLE[role]
  if (apiRole) course.ensure(apiRole).catch(() => {})
}, { immediate: true })
</script>
