<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb8"
      title="组织架构只读视图"
      description="部门与账号的增删改走「系统管理 → 部门管理 / 用户管理」（第 1 组 1-04）。本页只展示部门结构与师生人数，用于核对教学班归属。"
    />

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card v-loading="loading" shadow="never">
          <template #header>
            <span>部门结构</span>
          </template>
          <el-tree
            :data="tree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleSelect"
          >
            <template #default="{ data }">
              <span class="edu-node">
                <span>{{ data.name }}</span>
                <span class="edu-node-meta">
                  教师 {{ data.teacherCount || 0 }} · 学生 {{ data.studentCount || 0 }}
                </span>
              </span>
            </template>
          </el-tree>
          <div v-if="!loading && !tree.length" class="edu-empty">
            <p>没有可展示的部门</p>
            <p class="edu-empty-hint">请先在「系统管理 → 部门管理」建立学校、院系结构。</p>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>当前部门</span>
          </template>
          <el-descriptions v-if="selected" :column="1" border>
            <el-descriptions-item label="部门">{{ selected.name }}</el-descriptions-item>
            <el-descriptions-item label="部门 ID">{{ selected.id }}</el-descriptions-item>
            <el-descriptions-item label="上级部门">{{ parentName }}</el-descriptions-item>
            <el-descriptions-item label="教师人数">{{ selected.teacherCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="学生人数">{{ selected.studentCount || 0 }}</el-descriptions-item>
          </el-descriptions>
          <div v-else class="edu-empty">
            <p>点击左侧部门查看明细</p>
          </div>
          <div class="edu-actions">
            <el-button type="primary" plain @click="gotoDept">打开部门管理</el-button>
            <el-button plain @click="gotoUser">打开用户管理</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="EduDeptManage">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrgTree } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'

const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const tree = ref([])
const flat = ref([])
const selected = ref(null)

const parentName = computed(() => {
  if (!selected.value) return '—'
  const parent = flat.value.find((item) => item.id === selected.value.parentId)
  return parent ? parent.name : '顶级'
})

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

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await getOrgTree()
    flat.value = Array.isArray(res) ? res : res.data || []
    tree.value = buildTree(flat.value)
  } catch (e) {
    flat.value = []
    tree.value = []
    errorText.value = eduErrorText(e)
  } finally {
    loading.value = false
  }
}

function handleSelect(data) {
  selected.value = data
}

function gotoDept() {
  router.push({ path: '/system/dept' })
}

function gotoUser() {
  router.push({ path: '/system/user' })
}

onMounted(load)
</script>

<style scoped>
.edu-node {
  display: flex;
  gap: 12px;
  align-items: center;
  flex: 1;
}
.edu-node-meta {
  font-size: 12px;
  color: #909399;
}
.edu-empty {
  padding: 16px 0;
  color: #606266;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
.edu-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
