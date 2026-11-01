<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon title="系统目录查询" description="本页读取现有若依接口。账号与部门的增删改、角色授权由系统管理模块负责，当前仅提供查询。" class="mb16" />
    <el-form inline @submit.prevent="search"><el-form-item :label="isUser ? '账号' : '部门名称'"><el-input v-model="keyword" clearable @clear="search" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item></el-form>
    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon class="mb16" />
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column :prop="isUser ? 'userId' : 'deptId'" label="ID" width="100" />
      <el-table-column :prop="isUser ? 'userName' : 'deptName'" :label="isUser ? '账号' : '部门'" min-width="160" />
      <el-table-column v-if="isUser" prop="nickName" label="姓名" min-width="140" />
      <el-table-column v-if="isUser" prop="dept.deptName" label="所属部门" min-width="160" />
      <el-table-column v-else prop="leader" label="负责人" min-width="140" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
    </el-table>
    <pagination v-if="isUser && total" v-model:page="page" v-model:limit="limit" :total="total" @pagination="load" />
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import request, { eduErrorText } from '@/utils/request'
const route = useRoute()
const isUser = computed(() => route.meta.directory === 'user')
const keyword = ref(String(route.query.userName || ''))
const rows = ref([])
const total = ref(0)
const page = ref(1)
const limit = ref(10)
const loading = ref(false)
const errorText = ref('')
function search() { page.value = 1; load() }
async function load() {
  loading.value = true; errorText.value = ''
  try {
    const result = await request({ url: isUser.value ? '/system/user/list' : '/system/dept/list', params: isUser.value
      ? { userName: keyword.value, pageNum: page.value, pageSize: limit.value } : { deptName: keyword.value } })
    rows.value = isUser.value ? result.rows || [] : result.data || []
    total.value = Number(result.total) || 0
  } catch (error) { rows.value = []; errorText.value = eduErrorText(error) }
  finally { loading.value = false }
}
onMounted(load)
</script>
