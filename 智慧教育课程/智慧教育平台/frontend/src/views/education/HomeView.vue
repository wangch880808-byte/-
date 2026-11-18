<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { canOpen, session } from '@/education/session.js'
import { pages } from '@/education/ui-pages.js'
import { workbenchSections } from '@/education/course-menu.js'
import StudentNotices from '@/views/education/student/StudentNotices.vue'
import TeacherAnnouncements from '@/views/education/teacher/TeacherAnnouncements.vue'
import StudentOverview from '@/views/education/student/StudentOverview.vue'
import TeacherOverview from '@/views/education/teacher/TeacherOverview.vue'
const route = useRoute()
// 总览在 overview 分区内直接渲染，不再重复占用欢迎页的快捷入口。
const available = computed(() => pages.filter(page =>
  !['teacher-overview', 'student-overview'].includes(page.id) && canOpen(page)))
const sectionKey = computed(() => String(route.query.section || 'overview'))
const section = computed(() => (workbenchSections[session.role] || []).find(item => item.key === sectionKey.value))
const noticePage = computed(() => pages.find(page => page.id === 'student-notices' && canOpen(page)))
const teacherAnnouncementPage = computed(() =>
  pages.find(page => page.id === 'teacher-announcements' && canOpen(page)))
const teacherOverviewPage = computed(() =>
  pages.find(page => page.id === 'teacher-overview' && canOpen(page)))
const studentOverviewPage = computed(() =>
  pages.find(page => page.id === 'student-overview' && canOpen(page)))
</script>
<template>
  <div class="panel">
    <h2>{{ section?.title || '工作台' }}</h2>
    <template v-if="sectionKey === 'overview'">
      <p>{{ session.user?.nickName || session.user?.userName }}，欢迎回来</p>
      <div v-if="available.length" class="workspace-shortcuts">
        <router-link v-for="page in available" :key="page.id" class="ui-btn" :to="'/' + session.role + '/' + page.path">{{ page.title }}</router-link>
      </div>
      <p v-else class="state" role="status">当前暂无可用功能，已授权功能开放后会显示在这里。</p>
      <teacher-overview v-if="session.role === 'teacher' && teacherOverviewPage" />
      <student-overview v-else-if="session.role === 'student' && studentOverviewPage" />
      <p v-else-if="session.role === 'teacher' || session.role === 'student'" class="state" role="status">
        总览未开放或没有访问权限。
      </p>
    </template>
    <template v-else-if="sectionKey === 'notice' && session.role === 'student'">
      <student-notices v-if="noticePage" />
      <p v-else class="state" role="status">该功能未开放或没有访问权限。</p>
    </template>
    <template v-else-if="sectionKey === 'notice' && session.role === 'teacher'">
      <teacher-announcements v-if="teacherAnnouncementPage" />
      <p v-else class="state" role="status">该功能未开放或没有访问权限。</p>
    </template>
    <p v-else class="state" role="status">该功能尚未开放。</p>
  </div>
</template>
