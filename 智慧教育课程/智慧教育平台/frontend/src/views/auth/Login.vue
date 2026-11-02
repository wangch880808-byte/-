<template>
  <main class="login-page">
    <section class="login-story">
      <div class="login-brand"><img :src="logo" alt="" />知教 <span>ZH/JIAO</span></div>
      <div class="story-copy"><span class="story-label">智慧教育 · 连接每一次成长</span><h1>让教与学，<br />在这里相遇。</h1>
        <p>从课程建设到课堂互动，<br />让教学有序开展，让学习持续发生。</p>
        <div class="story-line"><span />课程 · 教学 · 成长</div></div>
      <small>智慧教育课程平台</small>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <span class="eyebrow">欢迎回来 / WELCOME</span><h2>{{ ROLE_LABELS[form.role] }}登录</h2><p class="login-subtitle">登录你的教学工作空间</p>
        <div class="role-tabs" aria-label="登录角色">
          <button v-for="role in ROLES" :key="role" :class="{ active: form.role === role }" :aria-pressed="form.role === role" @click="form.role = role; errorText = ''">{{ ROLE_LABELS[role] }}</button>
        </div>
        <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon class="mb16" />
        <el-alert v-else-if="route.query.reason === 'expired'" title="登录已过期，请重新登录" type="info" :closable="false" class="mb16" />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号" prop="username"><el-input v-model="form.username" aria-label="账号" name="username" autocomplete="username" placeholder="请输入账号 / 工号 / 学号" size="large" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" aria-label="密码" name="password" type="password" autocomplete="current-password" show-password placeholder="请输入密码" size="large" /></el-form-item>
          <el-form-item v-if="captchaEnabled" label="验证码" prop="code">
            <div class="captcha-row"><el-input v-model="form.code" aria-label="验证码" name="captcha" autocomplete="off" placeholder="请输入验证码" size="large" />
              <button type="button" class="captcha-button" aria-label="刷新验证码" :disabled="captchaLoading" @click="loadCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="验证码" /><span v-else>{{ captchaLoading ? '加载中…' : '点击重试' }}</span>
              </button></div>
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" native-type="submit" :loading="submitting" :disabled="captchaLoading || !captchaReady">登录工作空间</el-button>
        </el-form>
        <p class="login-help">账号由学校统一分配，如需帮助请联系管理员。</p>
      </div>
      <footer>知教 · 智慧教育课程平台</footer>
    </section>
  </main>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { ROLES, ROLE_LABELS, ROLE_HOME, safeRedirect } from '@/utils/access'
import { eduErrorText } from '@/utils/request'
import logo from '../../../logo.png'
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const formRef = ref()
const form = reactive({ username: '', password: '', code: '', uuid: '', role: 'admin' })
const submitting = ref(false)
const captchaEnabled = ref(true)
const captchaLoading = ref(false)
const captchaReady = ref(false)
const captchaImage = ref('')
const errorText = ref('')
const rules = {
  username: [{ required: true, whitespace: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ validator: (rule, value, callback) => captchaEnabled.value && !value.trim() ? callback(new Error('请输入验证码')) : callback(), trigger: 'blur' }]
}
async function loadCaptcha() {
  captchaLoading.value = true
  captchaReady.value = false
  captchaImage.value = ''
  form.code = ''
  form.uuid = ''
  try {
    const result = await getCaptcha()
    captchaEnabled.value = result.captchaEnabled !== false
    if (captchaEnabled.value) {
      if (!result.uuid || !result.img) throw new Error('验证码数据不完整')
      form.uuid = result.uuid
      captchaImage.value = 'data:image/jpeg;base64,' + result.img
    }
    captchaReady.value = true
    if (errorText.value.startsWith('验证码加载失败：')) errorText.value = ''
  } catch (error) { captchaEnabled.value = true; errorText.value = '验证码加载失败：' + eduErrorText(error) }
  finally { captchaLoading.value = false }
}
async function submit() {
  if (submitting.value || !captchaReady.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  submitting.value = true
  errorText.value = ''
  try {
    await user.signIn({ ...form, username: form.username.trim(), code: captchaEnabled.value ? form.code.trim() : '' })
    form.password = ''
    await router.replace(safeRedirect(route.query.redirect, ROLE_HOME[user.role]))
  } catch (error) { errorText.value = eduErrorText(error); await loadCaptcha() }
  finally { submitting.value = false }
}
onMounted(loadCaptcha)
</script>
