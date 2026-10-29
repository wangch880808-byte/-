<script setup>
import { computed } from 'vue'
import { session } from '../stores/session'
import { menuByRole, roleLabels } from '../config/menu'

const groups = computed(() => menuByRole[session.role] || [])
const roleLabel = computed(() => roleLabels[session.role] || '')

function linkOf(item) {
  return `/${session.role}${item.path ? '/' + item.path : ''}`
}
</script>

<template>
  <div class="panel">
    <h2 style="margin: 0 0 8px; font-size: 18px">{{ roleLabel }}工作台</h2>
    <p style="margin: 0 0 18px; color: #8c8c8c; font-size: 13px">
      当前为 Vue 版前端骨架，已接通的页面见左侧导航。
    </p>

    <div v-if="session.role === 'admin'">
      <div v-for="group in groups" :key="group.title" style="margin-bottom: 16px">
        <div style="font-size: 13px; font-weight: 600; margin-bottom: 8px">{{ group.title }}</div>
        <div style="display: flex; flex-wrap: wrap; gap: 10px">
          <router-link v-for="item in group.items" :key="item.path" class="ui-btn" :to="linkOf(item)">
            {{ item.title }}
          </router-link>
        </div>
      </div>
    </div>

    <div v-else class="state" style="text-align: left; padding: 12px 0">
      <p>教师/学生业务页面由第 2～6 组按分工接入，当前 Vue 工程只提供登录、布局、权限与请求基础设施。</p>
      <p>可参考静态版：<code>frontend/teacher.html</code>、<code>frontend/student.html</code>。</p>
    </div>
  </div>
</template>
