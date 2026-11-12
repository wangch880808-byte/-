<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bootstrap, canOpen, logout, session } from '@/education/session.js'
import { api, cancelRequests } from '@/api/education/client.js'
import { pages } from '@/education/ui-pages.js'
import useUserStore from '@/store/modules/user'
import { adminMenuItems, buildAdminGroups } from '@/education/ui-menu.js'
import { courseSections, workbenchSections, workbenchTitle } from '@/education/course-menu.js'
import { context, contexts, prepareContext, resetView, suspendWorkspace, workspace } from '@/education/workspace.js'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const navOpen = ref(false)
const logoUrl = import.meta.env.BASE_URL + 'logo.png'
const roleLabels = { admin: '管理员', teacher: '教师', student: '学生' }
const authorized = computed(() => pages.filter(canOpen))
const groups = computed(() => session.role === 'admin' ? buildAdminGroups(pages, canOpen) : [])
const coursePage = computed(() => authorized.value.find(page => page.id === 'courses'))
const currentPage = computed(() => route.meta.page)
const selectedMenu = computed(() => session.role === 'admin'
  ? adminMenuItems.find(item => route.path === '/admin/' + item.path) : null)
const title = computed(() => sectionTitle.value || selectedMenu.value?.title || currentPage.value?.title || (route.name === 'unavailable' ? '功能未开放' : '工作台'))
const navLevel = computed(() => {
  if (route.name === 'home') return 'workbench'
  if (route.name === 'courses') return 'courses'
  if (route.name === 'course-detail') return 'course'
  return ''
})
const activeSection = computed(() => String(route.query.section || 'overview'))
const activeTab = computed(() => String(route.query.tab || 'outline'))
const workbenchItems = computed(() => workbenchSections[session.role] || [])
const courseItems = computed(() => courseSections[session.role] || [])
const sectionTitle = computed(() => {
  if (navLevel.value === 'workbench') return workbenchItems.value.find(item => item.key === activeSection.value)?.title
  if (navLevel.value === 'courses') return session.role === 'teacher' ? '教学课程' : '我的课程'
  if (navLevel.value === 'course') return courseItems.value.find(item => item.key === activeTab.value)?.title
  return ''
})
function contextQuery(extra) {
  const query = { ...extra }
  for (const key of ['courseSpaceId', 'classId', 'bindingId']) if (context.current?.[key]) query[key] = context.current[key]
  return query
}
function sectionTarget(key) {
  return { path: '/' + session.role, query: contextQuery({ section: key }) }
}
function tabTarget(key) {
  return { path: '/' + session.role + '/courses/' + route.params.courseId, query: contextQuery({ tab: key }) }
}
const collapsedGroups = ref(session.role === 'admin' ? [...new Set(adminMenuItems.map(item => item.group))] : [])
function toggleGroup(title) {
  collapsedGroups.value = collapsedGroups.value.includes(title)
    ? collapsedGroups.value.filter(item => item !== title) : [...collapsedGroups.value, title]
}
const selection = computed(() => context.pending || context.current)
const selectedCourse = computed(() => context.courses.find(item => item.courseSpaceId === selection.value?.courseSpaceId))
// 学生端按「自己的班级」进入，教师端按「教学课程 → 教学班」进入。
const studentClassValue = computed(() => selection.value?.courseSpaceId && selection.value?.classId
  ? selection.value.courseSpaceId + ':' + selection.value.classId : '')
function selectStudentClass(value) {
  if (!value) { contexts.select('', null); return }
  const [courseSpaceId, classId] = value.split(':')
  const target = context.courses.find(item => item.courseSpaceId === courseSpaceId)?.classes.find(item => item.classId === classId)
  contexts.select(courseSpaceId, classId, target?.bindingId)
}
const contextMissing = computed(() => currentPage.value?.requiresContext &&
  (!context.current || (currentPage.value.requiresContext === 'class' && !context.current.classId)))
function pathOf(page) { return '/' + session.role + '/' + page.path }
function withContext(path) {
  const params = new URLSearchParams()
  for (const key of ['courseSpaceId', 'classId', 'bindingId']) if (context.current?.[key]) params.set(key, context.current[key])
  return path + (params.size ? '?' + params : '')
}
// 同一路由下的工作台分区或课程分区靠 query 区分，页签标识必须带上分区，否则只会有「课程概览」一个页签。
const tabSection = computed(() => {
  if (navLevel.value === 'workbench') return { section: activeSection.value }
  if (navLevel.value === 'course') return { tab: activeTab.value }
  return {}
})
const tabKey = computed(() => route.path + '?' + new URLSearchParams(tabSection.value))
function tabTo(tab) {
  return { path: tab.path, query: contextQuery(tab.query || {}) }
}
watch(() => [route.path, tabKey.value, session.ready], () => {
  if (!session.ready) return
  workspace.tabs = workspace.tabs.filter(tab => !tab.page || canOpen(tab.page))
  if (route.name === 'unavailable') return
  if (!workspace.tabs.some(tab => tab.key === tabKey.value)) {
    workspace.tabs.push({ key: tabKey.value, path: route.path, query: tabSection.value, title: title.value, page: currentPage.value })
    if (workspace.tabs.length > 8) workspace.tabs.shift()
  }
}, { immediate: true })
async function closeTab(tab) {
  const wasActive = tab.key === tabKey.value
  workspace.tabs = workspace.tabs.filter(item => item.key !== tab.key)
  if (wasActive) {
    const last = workspace.tabs.at(-1)
    await router.push(last ? tabTo(last) : withContext('/' + session.role))
  }
}
async function retry() {
  resetView()
  try {
    await bootstrap(); await prepareContext(true)
    if (currentPage.value && !canOpen(currentPage.value)) await router.replace('/' + session.role)
  } catch { await router.replace('/login') }
}
async function doLogout() {
  suspendWorkspace()
  try { await logout() } catch { /* 本地身份已清理，仍返回登录页。 */ }
  await router.replace({ path: '/login', query: { logout: 'ok' } })
}
let headerVersion = 0
let headerController = null
async function refreshHeader() {
  const run = ++headerVersion
  headerController?.abort()
  headerController = new AbortController()
  const signal = headerController.signal
  const epoch = workspace.epoch
  workspace.notifications = []
  if (!session.ready || workspace.blocked) return
  const entries = authorized.value.filter(page => page.headerEntry &&
    (!page.requiresContext || (context.current && (page.requiresContext === 'course' || context.current.classId))))
  workspace.notifications = entries.map(page => ({ id: page.id, title: page.headerEntry.label, page, status: 'ready', count: null }))
  await Promise.all(entries.map(async page => {
    if (!page.headerEntry.getUnreadCount) return
    const entry = workspace.notifications.find(item => item.id === page.id)
    try {
      const count = await page.headerEntry.getUnreadCount(context.current ? Object.freeze({ ...context.current }) : null,
        { signal, request: (path, options) => api.get(path, { ...options, scope: 'header', signal, silent403: true }) })
      if (signal.aborted || run !== headerVersion || epoch !== workspace.epoch) return
      if (!Number.isSafeInteger(count) || count < 0) throw new Error('未读数格式不正确')
      entry.count = count
    } catch (error) {
      if (signal.aborted || run !== headerVersion || epoch !== workspace.epoch) return
      if (error.forbidden) workspace.notifications = workspace.notifications.filter(item => item.id !== page.id)
      else if (!error.cancelled && !error.unauthorized) entry.status = 'unavailable'
    }
  }))
}
watch(() => [session.ready, workspace.epoch, context.current, pages.length], refreshHeader, { immediate: true })
onMounted(() => window.addEventListener('edu:refresh-header', refreshHeader))
onUnmounted(() => {
  headerVersion++; headerController?.abort(); cancelRequests('header')
  window.removeEventListener('edu:refresh-header', refreshHeader)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">
        <button class="nav-toggle" type="button" aria-label="展开或收起导航" @click="navOpen = !navOpen">☰</button>
        <img :src="logoUrl" alt="校徽" /><span>知教·智慧教育课程平台</span>
      </div>
      <div class="header-right">
        <button v-for="entry in workspace.notifications" :key="entry.id" class="ui-btn" type="button" @click="router.push(withContext(pathOf(entry.page)))">
          {{ entry.title }}<span v-if="entry.count > 0" class="notice-count">{{ entry.count > 99 ? '99+' : entry.count }}</span>
          <span v-if="entry.status === 'unavailable'" class="hint">（暂不可用）</span>
        </button>
        <span class="role-tag">{{ roleLabels[session.role] }}</span>
        <router-link v-if="session.ready" class="profile-entry" :to="withContext('/' + session.role + '/profile')"
          aria-label="个人中心" title="个人中心">
          <el-avatar :size="30" :src="userStore.avatar" />
          <span class="user">{{ session.user?.nickName || session.user?.userName }}</span>
          <span>个人中心</span>
        </router-link>
        <button class="ui-btn" type="button" @click="doLogout">退出登录</button>
      </div>
    </header>
    <div class="app-body">
      <button v-if="navOpen" class="nav-scrim" type="button" aria-label="收起导航" @click="navOpen = false"></button>
      <aside class="app-sidebar" :class="{ open: navOpen }">
        <template v-if="session.role !== 'admin'">
          <router-link class="menu-item menu-level1" :class="{ active: navLevel === 'workbench' }"
            :to="withContext('/' + session.role)" @click="navOpen = false">{{ workbenchTitle(session.role) }}</router-link>
          <div v-if="navLevel === 'workbench'" class="menu-sub">
            <router-link v-for="item in workbenchItems" :key="item.key" class="menu-item menu-level2"
              :class="{ active: activeSection === item.key }" :to="sectionTarget(item.key)" @click="navOpen = false">{{ item.title }}</router-link>
          </div>
          <template v-if="coursePage">
            <router-link class="menu-item menu-level1" :class="{ active: navLevel === 'courses' || navLevel === 'course' }"
              :to="withContext(pathOf(coursePage))" @click="navOpen = false">{{ session.role === 'teacher' ? '教学课程' : '我的课程' }}</router-link>
            <div v-if="navLevel === 'courses'" class="menu-sub">
              <router-link class="menu-item menu-level2 active" :to="withContext(pathOf(coursePage))"
                @click="navOpen = false">{{ session.role === 'teacher' ? '课程列表 / 创建课程' : '在修课程列表' }}</router-link>
            </div>
            <div v-if="navLevel === 'course'" class="menu-sub">
              <router-link v-for="item in courseItems" :key="item.key" class="menu-item menu-level2"
                :class="{ active: activeTab === item.key }" :to="tabTarget(item.key)" @click="navOpen = false">{{ item.title }}</router-link>
            </div>
          </template>
        </template>
        <div v-for="group in groups" :key="group.title" class="menu-group" :class="{ collapsed: collapsedGroups.includes(group.title) }">
          <button class="menu-title menu-group-toggle" type="button" :aria-expanded="!collapsedGroups.includes(group.title)"
            @click="toggleGroup(group.title)">{{ group.title }}<span class="arrow" aria-hidden="true">▾</span></button>
          <div v-show="!collapsedGroups.includes(group.title)">
            <template v-for="page in group.items" :key="page.id">
              <span v-if="page.disabled" class="menu-item menu-disabled" aria-disabled="true">{{ page.title }}</span>
              <router-link v-else class="menu-item" :class="{ active: route.path === pathOf(page) }"
                :to="withContext(pathOf(page))" @click="navOpen = false">{{ page.title }}</router-link>
            </template>
          </div>
        </div>
      </aside>
      <main class="app-main">
        <div class="breadcrumb">{{ selectedMenu?.group || roleLabels[session.role] }} / {{ title }}</div>
        <nav v-if="session.ready" class="workspace-tabs" aria-label="已打开的页签">
          <span v-for="tab in workspace.tabs" :key="tab.key" :class="{ selected: tab.key === tabKey }">
            <router-link :to="tabTo(tab)">{{ tab.title }}</router-link>
            <button type="button" :aria-label="'关闭' + tab.title" @click="closeTab(tab)">×</button>
          </span>
        </nav>
        <div v-if="session.ready && session.role !== 'admin'" class="workspace-context">
          <span v-if="context.term">{{ context.term.termName || context.term.termCode }}</span>
          <select v-if="session.role === 'student'" class="ui-select" aria-label="当前班级" :value="studentClassValue" @change="selectStudentClass($event.target.value)">
            <option value="">选择班级</option>
            <optgroup v-for="course in context.courses" :key="course.courseSpaceId" :label="course.title">
              <option v-for="item in course.classes" :key="item.classId" :value="course.courseSpaceId + ':' + item.classId">{{ item.className }}</option>
            </optgroup>
          </select>
          <template v-else>
            <select class="ui-select" aria-label="当前教学课程" :value="selection?.courseSpaceId || ''" @change="contexts.select($event.target.value, null)">
              <option value="">选择教学课程</option><option v-for="course in context.courses" :key="course.courseSpaceId" :value="course.courseSpaceId">{{ course.title }}</option>
            </select>
            <select class="ui-select" aria-label="当前教学班" :disabled="!selectedCourse" :value="selection?.classId || ''"
              @change="contexts.select(selectedCourse.courseSpaceId, $event.target.value)">
              <option value="">选择教学班</option><option v-for="item in selectedCourse?.classes || []" :key="item.classId" :value="item.classId">{{ item.className }}</option>
            </select>
          </template>
          <span role="status" class="hint">{{ context.message }}</span>
          <button class="ui-btn" type="button" :disabled="context.status === 'loading'" @click="prepareContext(true)">刷新课程</button>
        </div>
        <div v-if="!session.ready || workspace.pending" class="panel state" role="status">正在核验登录与权限…</div>
        <div v-else-if="workspace.blocked" class="panel state" role="alert">
          <p>{{ workspace.blocked }}</p><button class="ui-btn" type="button" @click="retry">重新核验</button>
        </div>
        <div v-else-if="contextMissing" class="panel state" role="status">{{ context.message || '请先选择有效的课程与教学班' }}</div>
        <div v-else class="page"><router-view :key="route.path + ':' + workspace.epoch" /></div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.profile-entry { display: inline-flex; align-items: center; gap: 8px; color: inherit; border-radius: 6px; padding: 2px 4px; }
.profile-entry:hover, .profile-entry.router-link-active { color: var(--primary-dark); background: #f0f5ff; }
/* 教师端 / 学生端两级菜单：一级平铺，二级在当前一级下展开。 */
.menu-level1 { padding-left: 16px; font-weight: 600; color: #333; }
.menu-sub { padding-bottom: 6px; }
.menu-level2 { padding-left: 34px; }
</style>
