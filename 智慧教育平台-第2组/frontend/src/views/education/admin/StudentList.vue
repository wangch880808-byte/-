<template>
  <div class="app-container">
    <el-form ref="queryRef" :inline="true" :model="queryParams">
      <el-form-item label="学生">
        <el-input
          v-model="queryParams.keyword"
          placeholder="学号 / 姓名"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">搜索</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="学号" prop="userName" width="150" />
      <el-table-column label="姓名" prop="nickName" min-width="120" />
      <el-table-column label="所属部门" prop="deptName" min-width="160" show-overflow-tooltip />
      <el-table-column label="在班班级" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.className || '未入班' }}</template>
      </el-table-column>
      <el-table-column label="班级数" width="100" align="center">
        <template #default="{ row }">{{ row.classCount || 0 }} 个</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openClasses(row)">在班记录</el-button>
          <el-button link type="primary" @click="gotoClassPage">去调班</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="edu-empty">
          <p>没有符合条件的学生</p>
          <p class="edu-empty-hint">学生账号由「系统管理 → 用户管理」维护；入班、退班在「班级管理」里做。</p>
        </div>
      </template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-drawer v-model="drawer" :title="current.nickName + ' · 在班记录'" size="720px" destroy-on-close>
      <el-table v-loading="drawerLoading" :data="classes" border size="small">
        <el-table-column label="教学班" prop="className" min-width="180" />
        <el-table-column label="学期" min-width="150">
          <template #default="{ row }">{{ row.termName || row.semesterName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '在班' : '已退出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入班" prop="joinedAt" width="120" />
        <el-table-column label="退出" width="120">
          <template #default="{ row }">{{ row.leftAt || '—' }}</template>
        </el-table-column>
        <template #empty>
          <div class="edu-empty"><p>该学生还没有入班记录</p></div>
        </template>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="EduStudentList">
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listStudent, listStudentClasses } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const drawer = ref(false)
const drawerLoading = ref(false)
const classes = ref([])
const current = ref({ userId: null, nickName: '' })

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listStudent(queryParams)
    list.value = res.rows || []
    total.value = Number(res.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
    errorText.value = eduErrorText(e)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.keyword = ''
  handleQuery()
}

async function openClasses(row) {
  current.value = { userId: row.userId, nickName: row.nickName }
  drawer.value = true
  drawerLoading.value = true
  try {
    const res = await listStudentClasses(row.userId)
    classes.value = res.rows || []
  } catch (e) {
    classes.value = []
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    drawerLoading.value = false
  }
}

function gotoClassPage() {
  router.push({ path: '/education/admin/class' })
}

onMounted(getList)
</script>

<style scoped>
.edu-empty {
  padding: 16px 0;
  color: #606266;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
</style>
