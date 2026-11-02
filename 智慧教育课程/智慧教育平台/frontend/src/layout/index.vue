<template>
  <div class="workspace" :class="{ 'menu-open': menuOpen }">
    <button v-if="menuOpen" class="nav-scrim" aria-label="收起导航" @click="menuOpen = false" />
    <aside class="sidebar">
      <router-link class="brand" to="/"><img :src="logo" alt="" /><span>知教<small>智慧教育平台</small></span></router-link>
      <div class="role-caption">{{ ROLE_LABELS[user.role] }}工作空间</div>
      <nav aria-label="主导航">
        <router-link v-for="(item, index) in menu" :key="item.path" :to="item.path"
          :class="{ selected: activeMenu === item.path }" @click="menuOpen = false">
          <span class="nav-mark">{{ String(index + 1).padStart(2, '0') }}</span>{{ item.meta.title }}
        </router-link>
      </nav>
      <div class="sidebar-foot"><span class="online-dot" /> 教学与学习，在此连接</div>
    </aside>
    <div class="workspace-body">
      <header class="topbar">
        <div class="breadcrumb"><button class="menu-toggle" aria-label="展开导航" @click="menuOpen = !menuOpen">☰</button>
          <span>教学平台</span><span class="separator">/</span><strong>{{ route.meta.title }}</strong></div>
        <div class="user-tools">
          <el-select v-if="availableRoles.length > 1" :model-value="user.role" aria-label="切换角色" style="width:110px" @change="switchRole">
            <el-option v-for="role in availableRoles" :key="role" :value="role" :label="ROLE_LABELS[role]" />
          </el-select>
          <span class="user-name">{{ user.user?.nickName || user.user?.userName || '已登录用户' }}</span>
          <el-button :loading="leaving" @click="signOut">退出登录</el-button>
        </div>
      </header>
      <main class="page-content">
        <div class="page-heading"><div><span class="eyebrow">{{ ROLE_LABELS[user.role] }} · 教学工作台</span><h1>{{ route.meta.title }}</h1></div>
          <span class="workspace-badge">知教 / {{ new Date().getFullYear() }}</span></div>
        <router-view v-slot="{ Component }"><component :is="Component" :key="route.fullPath" /></router-view>
      </main>
      <footer class="page-footer">知教 · 智慧教育课程平台</footer>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { businessRoutes } from '@/router/routes'
import { ROLES, ROLE_LABELS, ROLE_HOME, canAccess } from '@/utils/access'
import { setRole } from '@/utils/auth'
import logo from '../../logo.png'
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const menuOpen = ref(false)
const leaving = ref(false)
const availableRoles = computed(() => ROLES.filter(role => user.roles.includes(role)))
const menu = computed(() => businessRoutes.filter(item => item.meta.nav && item.meta.roles.includes(user.role) && canAccess(item.meta, user.roles, user.permissions)))
const activeMenu = computed(() => route.meta.activeMenu || route.path)
function switchRole(role) { user.role = role; setRole(role); router.push(ROLE_HOME[role]) }
async function signOut() {
  leaving.value = true
  try { await user.signOut() } catch { ElMessage.warning('本页已退出，服务端退出状态未确认') }
  finally { leaving.value = false; router.replace('/login') }
}
</script>
