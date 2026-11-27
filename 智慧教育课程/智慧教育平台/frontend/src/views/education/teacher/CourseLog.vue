<template>
  <div v-loading="loading">
    <el-alert
      v-if="errorText"
      :title="errorText"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />
    <el-table v-if="!errorText" :data="rows" border>
      <el-table-column label="时间" prop="operTime" width="165" />
      <el-table-column label="操作人" prop="operName" min-width="110" />
      <el-table-column label="操作" prop="title" min-width="200" />
      <el-table-column label="IP" prop="operIp" width="130" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !errorText && !rows.length" description="还没有操作记录" :image-size="80" />
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listCourseOperations } from '@/api/education/course'
import { eduErrorText } from '@/api/education/http'

const props = defineProps({ courseId: { type: [String, Number], required: true } })

const loading = ref(false)
const errorText = ref('')
const rows = ref([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10 })

async function getList() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await listCourseOperations(props.courseId, queryParams)
    rows.value = res.rows || []
    total.value = Number(res.total) || 0
  } catch (e) {
    rows.value = []
    total.value = 0
    errorText.value = eduErrorText(e)
  } finally {
    loading.value = false
  }
}

onMounted(getList)
</script>
