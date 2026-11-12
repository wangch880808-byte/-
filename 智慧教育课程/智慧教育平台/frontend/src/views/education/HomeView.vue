<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { canOpen, session } from '@/education/session.js'
import { pages } from '@/education/ui-pages.js'
import { workbenchSections } from '@/education/course-menu.js'
const route = useRoute()
const available = computed(() => pages.filter(canOpen))
const sectionKey = computed(() => String(route.query.section || 'overview'))
const section = computed(() => (workbenchSections[session.role] || []).find(item => item.key === sectionKey.value))
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
    </template>
    <p v-else class="state" role="status">该功能尚未开放。</p>
  </div>
</template>
