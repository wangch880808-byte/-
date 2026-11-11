<template>
  <div class="app-container group2-page">
    <el-row :gutter="16">
      <el-col :span="5">
        <el-card shadow="never" class="edu-tree-card">
          <template #header>
            <div class="edu-card-title">
              <span>组织架构</span>
              <el-button link type="primary" @click="clearDept">全部</el-button>
            </div>
          </template>
          <el-tree
            ref="deptTreeRef"
            :data="deptTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          />
        </el-card>
      </el-col>

      <el-col :span="19">
        <el-form ref="queryRef" :inline="true" :model="queryParams">
          <el-form-item label="教师">
            <el-input
              v-model="queryParams.keyword"
              placeholder="姓名 / 账号"
              clearable
              style="width: 220px"
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
          <el-table-column label="姓名" prop="nickName" min-width="120" />
          <el-table-column label="账号" prop="userName" width="150" />
          <el-table-column label="所属部门" prop="deptName" min-width="160" show-overflow-tooltip />
          <el-table-column label="手机号" width="140">
            <template #default="{ row }">{{ row.phonenumber || row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="任教班级" width="120" align="center">
            <template #default="{ row }">{{ row.classCount || 0 }} 个</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openClasses(row)">任教关系</el-button>
              <el-button link type="primary" @click="gotoAccount(row)">账号</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="edu-empty">
              <p>没有符合条件的教师</p>
              <p class="edu-empty-hint">账号本身由「系统管理 → 用户管理」维护，这里只读教学关系。</p>
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
      </el-col>
    </el-row>

    <el-drawer v-model="drawer" :title="current.nickName + ' · 任教关系'" size="760px" destroy-on-close>
      <el-table v-loading="drawerLoading" :data="classes" border size="small">
        <el-table-column label="教学班" prop="className" min-width="180" />
        <el-table-column label="学期" min-width="150">
          <template #default="{ row }">{{ row.termName || row.semesterName || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">{{ roleLabel(row.teacherRole) }}</template>
        </el-table-column>
        <el-table-column label="权限" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.canPublish" size="small" class="edu-tag">发布</el-tag>
            <el-tag v-if="row.canGrade" size="small" type="success" class="edu-tag">评分</el-tag>
            <el-tag v-if="row.canExport" size="small" type="info" class="edu-tag">导出</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">{{ row.validFrom || '—' }} 至 {{ row.validTo || '长期' }}</template>
        </el-table-column>
        <template #empty>
          <div class="edu-empty"><p>该教师当前没有任课授权</p></div>
        </template>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="EduTeacherList">
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTeacher, listTeacherClasses, getOrgTree } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const deptTree = ref([])
const deptTreeRef = ref()

const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: '', deptId: null })

const drawer = ref(false)
const drawerLoading = ref(false)
const classes = ref([])
const current = ref({ userId: null, nickName: '' })

function roleLabel(role) {
  if (role === 'LEAD') return '主讲'
  if (role === 'ASSIST') return '助教'
  if (role === 'GUEST') return '旁听'
  return role || '—'
}

function buildTree(rows) {
  const map = new Map()
  rows.forEach((row) => map.set(row.id, { ...row, children: [] }))
  const roots = []
  map.forEach((node) => {
    const parent = map.get(node.parentId)
    if (parent && parent.id !== node.id) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

async function loadDeptTree() {
  try {
    const res = await getOrgTree()
    const rows = Array.isArray(res) ? res : res.data || []
    deptTree.value = buildTree(rows)
  } catch (e) {
    deptTree.value = []
  }
}

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listTeacher(queryParams)
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

function handleNodeClick(node) {
  queryParams.deptId = node.id
  queryParams.pageNum = 1
  getList()
}

function clearDept() {
  queryParams.deptId = null
  deptTreeRef.value && deptTreeRef.value.setCurrentKey(null)
  handleQuery()
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
    const res = await listTeacherClasses(row.userId)
    classes.value = res.rows || []
  } catch (e) {
    classes.value = []
    proxy.$modal.msgError(eduErrorText(e))
  } finally {
    drawerLoading.value = false
  }
}

function gotoAccount(row) {
  router.push({ path: '/admin/users', query: { userName: row.userName } })
}

onMounted(() => {
  loadDeptTree()
  getList()
})
</script>

<style scoped>
.edu-card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.edu-tree-card {
  min-height: 420px;
}
.edu-empty {
  padding: 16px 0;
  color: #606266;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
.edu-tag {
  margin-right: 4px;
}
</style>
