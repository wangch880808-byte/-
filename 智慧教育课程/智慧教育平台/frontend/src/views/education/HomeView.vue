<script setup>
import { computed } from 'vue'
import { canOpen, session } from '@/education/session.js'
import { pages } from '@/education/ui-pages.js'
const available = computed(() => pages.filter(canOpen))
</script>
<template>
  <div class="panel">
    <h2>{{ session.user?.nickName || session.user?.userName }}，欢迎回来</h2>
    <div v-if="available.length" class="workspace-shortcuts">
      <router-link v-for="page in available" :key="page.id" class="ui-btn" :to="'/' + session.role + '/' + page.path">{{ page.title }}</router-link>
    </div>
    <p v-else class="state" role="status">当前暂无可用功能，已授权功能开放后会显示在这里。</p>
  </div>
</template>
