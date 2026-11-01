<template>
  <div class="app-container">
    <el-form ref="queryRef" :inline="true" :model="queryParams">
      <el-form-item label="学期" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="按名称或编码搜索"
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="3">
        <el-button type="primary" plain @click="handleAdd">新建学期</el-button>
      </el-col>
    </el-row>

    <el-alert
      v-if="errorText"
      :title="errorText"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="学期名称" prop="name" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="edu-strong">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="编码" prop="code" width="180" />
      <el-table-column label="起止日期" min-width="220">
        <template #default="{ row }">{{ row.startsOn || '—' }} 至 {{ row.endsOn || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="isCurrent(row)" type="success">当前学期</el-tag>
          <el-tag v-else type="info">普通学期</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleUpdate(row)">编辑</el-button>
          <el-button v-if="!isCurrent(row)" link type="primary" @click="handleSetCurrent(row)">
            设为当前
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="edu-empty">
          <p>还没有学期</p>
          <p class="edu-empty-hint">学期是建教学班的前提：先建一个学期，再回来建班。</p>
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

    <el-dialog v-model="open" :title="form.id ? '编辑学期' : '新建学期'" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="学期名称" prop="name">
          <el-input v-model="form.name" placeholder="如 2026-2027 学年第一学期" maxlength="64" />
        </el-form-item>
        <el-form-item label="学期编码" prop="code">
          <el-input v-model="form.code" placeholder="同一学校内唯一，如 2026-2027-1" maxlength="32" />
        </el-form-item>
        <el-form-item label="开始日期" prop="startsOn">
          <el-date-picker v-model="form.startsOn" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endsOn">
          <el-date-picker v-model="form.endsOn" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="EduTermList">
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { listTerm, addTerm, updateTerm, setCurrentTerm } from '@/api/education/term'
import { eduErrorText } from '@/api/education/http'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const list = ref([])
const total = ref(0)
const open = ref(false)
const formRef = ref()

const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const form = reactive({ id: null, name: '', code: '', startsOn: '', endsOn: '' })

const rules = {
  name: [{ required: true, message: '学期名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '学期编码不能为空', trigger: 'blur' }],
  startsOn: [{ required: true, message: '开始日期不能为空', trigger: 'change' }],
  endsOn: [
    { required: true, message: '结束日期不能为空', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (value && form.startsOn && value < form.startsOn) {
          callback(new Error('结束日期不能早于开始日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function isCurrent(row) {
  return row.current === true || row.current === 1
}

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listTerm(queryParams)
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

function resetForm() {
  Object.assign(form, { id: null, name: '', code: '', startsOn: '', endsOn: '' })
  // 不用 resetFields：弹窗复用同一个 form 实例，重置到"挂载时快照"会把上一行数据带回来
  formRef.value && formRef.value.clearValidate()
}

function handleAdd() {
  resetForm()
  open.value = true
}

function handleUpdate(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    startsOn: row.startsOn || '',
    endsOn: row.endsOn || ''
  })
  open.value = true
}

function submitForm() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        name: form.name.trim(),
        code: form.code.trim(),
        startsOn: form.startsOn,
        endsOn: form.endsOn
      }
      if (form.id) {
        await updateTerm(form.id, payload)
        proxy.$modal.msgSuccess('已保存')
      } else {
        await addTerm(payload)
        proxy.$modal.msgSuccess('学期已创建')
      }
      open.value = false
      getList()
    } catch (e) {
      proxy.$modal.msgError(eduErrorText(e))
    } finally {
      submitting.value = false
    }
  })
}

function handleSetCurrent(row) {
  proxy.$modal.confirm('把「' + row.name + '」设为当前学期？教师端和学生端默认展示的课程范围会随之切换。')
    .then(async () => {
      try {
        await setCurrentTerm(row.id)
        proxy.$modal.msgSuccess('已设为当前学期')
        getList()
      } catch (e) {
        proxy.$modal.msgError(eduErrorText(e))
      }
    })
    .catch(() => {})
}

onMounted(getList)
</script>

<style scoped>
.edu-strong {
  font-weight: 600;
}
.edu-empty {
  padding: 16px 0;
  color: #606266;
}
.edu-empty-hint {
  font-size: 12px;
  color: #909399;
}
</style>
