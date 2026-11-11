<template>
  <el-dialog
    :model-value="modelValue"
    :title="course && course.id ? '编辑课程' : '新建课程'"
    width="620px"
    append-to-body
    @open="clearValidate"
    @update:model-value="close"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="课程名称" prop="name">
        <el-input v-model="form.name" placeholder="如 软件工程导论" maxlength="100" />
      </el-form-item>
      <el-form-item label="关联正式课程">
        <el-select
          v-model="form.officialCourseId"
          placeholder="可暂不关联，管理员可后续对齐"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option v-for="item in officialCourses" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程简介">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="教学目标">
        <el-input v-model="form.learningObjectives" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="CourseFormDialog">
import { ref, reactive, watch, getCurrentInstance, onMounted } from 'vue'
import { addCourse, updateCourse } from '@/api/education/course'
import { listOfficialCourse } from '@/api/education/directory'
import { eduErrorText } from '@/api/education/http'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  course: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const { proxy } = getCurrentInstance()

const submitting = ref(false)
const officialCourses = ref([])
const formRef = ref()
const form = reactive({ id: null, name: '', officialCourseId: null, summary: '', learningObjectives: '' })

const rules = {
  name: [{ required: true, message: '课程名称不能为空', trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    const source = props.course
    Object.assign(form, {
      id: source && source.id ? source.id : null,
      name: source ? source.name || '' : '',
      officialCourseId: source ? source.officialCourseId || null : null,
      summary: source ? source.summary || '' : '',
      learningObjectives: source ? source.learningObjectives || '' : ''
    })
  }
)

function close() {
  emit('update:modelValue', false)
}

function clearValidate() {
  formRef.value && formRef.value.clearValidate()
}

function submit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        name: form.name.trim(),
        officialCourseId: form.officialCourseId || null,
        summary: form.summary || null,
        learningObjectives: form.learningObjectives || null
      }
      if (form.id) {
        await updateCourse(form.id, payload)
        proxy.$modal.msgSuccess('已保存')
      } else {
        await addCourse(payload)
        proxy.$modal.msgSuccess('课程已创建，绑定教学班后学生才能看到')
      }
      close()
      emit('saved')
    } catch (e) {
      proxy.$modal.msgError(eduErrorText(e))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  try {
    const res = await listOfficialCourse({ pageNum: 1, pageSize: 200 })
    officialCourses.value = res.rows || []
  } catch (e) {
    officialCourses.value = []
  }
})
</script>
