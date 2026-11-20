<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserProfile, updateUserProfile, updateUserPwd, uploadAvatar } from '@/api/system/user'
import useUserStore from '@/store/modules/user'
import { usePasswordRule } from '@/utils/passwordRule'

const userStore = useUserStore()
const { infoPwdValidator } = usePasswordRule()
const account = ref(null)
const roleGroup = ref('')
const postGroup = ref('')
const loading = ref(false)
const busy = ref('')
const tab = ref('profile')
const profileForm = ref()
const passwordForm = ref()
const avatarInput = ref()
const avatarOpen = ref(false)
const avatarPreview = ref('')
let avatarFile = null

const profile = reactive({ nickName: '', phonenumber: '', email: '', sex: '2' })
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileRules = {
  nickName: [{ required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_rule, value) => value === password.newPassword || new Error('两次输入的新密码不一致'), trigger: 'blur' }
  ]
}

async function loadProfile() {
  loading.value = true
  try {
    const result = await getUserProfile()
    account.value = result.data
    roleGroup.value = result.roleGroup
    postGroup.value = result.postGroup
    Object.assign(profile, {
      nickName: account.value.nickName || '',
      phonenumber: account.value.phonenumber || '',
      email: account.value.email || '',
      sex: ['0', '1', '2'].includes(account.value.sex) ? account.value.sex : '2'
    })
  } catch {
    // 请求层统一提示失败，页面保留重试入口。
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (busy.value) return
  busy.value = 'profile'
  try {
    if (!await profileForm.value.validate().catch(() => false)) return
    const changes = {
      nickName: profile.nickName.trim(),
      phonenumber: profile.phonenumber.trim(),
      email: profile.email.trim(),
      sex: profile.sex
    }
    await updateUserProfile(changes)
    Object.assign(profile, changes)
    Object.assign(account.value, changes)
    Object.assign(userStore.profile, changes)
    userStore.nickName = changes.nickName
    ElMessage.success('个人资料已保存')
  } catch {
    // 请求层统一提示失败。
  } finally {
    busy.value = ''
  }
}

async function savePassword() {
  if (busy.value) return
  busy.value = 'password'
  try {
    if (!await passwordForm.value.validate().catch(() => false)) return
    await updateUserPwd(password.oldPassword, password.newPassword)
    passwordForm.value.resetFields()
    ElMessage.success('密码已修改，下次登录请使用新密码')
  } catch {
    // 请求层统一提示失败。
  } finally {
    busy.value = ''
  }
}

function clearAvatarPreview() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  avatarFile = null
}

function chooseAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    ElMessage.warning('请选择不超过 5 MB 的 JPG 或 PNG 图片')
    return
  }
  clearAvatarPreview()
  avatarFile = file
  avatarPreview.value = URL.createObjectURL(file)
  avatarOpen.value = true
}

async function saveAvatar() {
  if (busy.value || !avatarFile) return
  busy.value = 'avatar'
  try {
    const data = new FormData()
    data.append('avatarfile', avatarFile, avatarFile.type === 'image/png' ? 'avatar.png' : 'avatar.jpg')
    const result = await uploadAvatar(data)
    userStore.avatar = (import.meta.env.VITE_APP_BASE_API || '') + result.imgUrl
    userStore.profile.avatar = result.imgUrl
    account.value.avatar = result.imgUrl
    avatarOpen.value = false
    ElMessage.success('头像已更新')
  } catch {
    // 请求层统一提示失败，保留预览以便重试。
  } finally {
    busy.value = ''
  }
}

onMounted(loadProfile)
onUnmounted(clearAvatarPreview)
</script>

<template>
  <section class="personal-center" v-loading="loading">
    <h1>个人中心</h1>
    <p class="profile-description">查看账号信息，管理个人资料、头像和登录密码。</p>
    <div v-if="account" class="profile-grid">
      <el-card shadow="never">
        <template #header><h2>个人信息</h2></template>
        <div class="identity">
          <el-avatar :size="88" :src="userStore.avatar" :alt="account.nickName || account.userName" />
          <strong>{{ account.nickName || account.userName }}</strong>
          <span class="account-name">账号 {{ account.userName }}</span>
          <el-button size="small" :disabled="!!busy" @click="avatarInput.click()">更换头像</el-button>
          <input ref="avatarInput" type="file" accept="image/jpeg,image/png" hidden @change="chooseAvatar" />
        </div>
        <dl class="account-details">
          <dt>所属角色</dt><dd>{{ roleGroup || '—' }}</dd>
          <dt>手机号码</dt><dd>{{ account.phonenumber || '未填写' }}</dd>
          <dt>邮箱</dt><dd>{{ account.email || '未填写' }}</dd>
          <dt>所属部门</dt><dd>{{ account.dept?.deptName || '—' }}</dd>
          <dt>所属岗位</dt><dd>{{ postGroup || '—' }}</dd>
          <dt>创建时间</dt><dd>{{ account.createTime || '—' }}</dd>
          <dt>最近登录</dt><dd>{{ account.loginDate || '—' }}</dd>
        </dl>
      </el-card>

      <el-card shadow="never">
        <el-tabs v-model="tab">
          <el-tab-pane label="基本设置" name="profile">
            <el-form ref="profileForm" :model="profile" :rules="profileRules" :disabled="!!busy"
              class="settings-form" label-width="88px" @submit.prevent="saveProfile">
              <el-form-item label="昵称" prop="nickName">
                <el-input v-model.trim="profile.nickName" maxlength="30" autocomplete="nickname" />
              </el-form-item>
              <el-form-item label="手机号" prop="phonenumber">
                <el-input v-model.trim="profile.phonenumber" maxlength="11" autocomplete="tel" inputmode="tel" placeholder="选填" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model.trim="profile.email" maxlength="50" autocomplete="email" placeholder="选填" />
              </el-form-item>
              <el-form-item label="性别" prop="sex">
                <el-radio-group v-model="profile.sex">
                  <el-radio-button value="0">男</el-radio-button>
                  <el-radio-button value="1">女</el-radio-button>
                  <el-radio-button value="2">未设置</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <div class="settings-actions">
                <el-button type="primary" native-type="submit" :loading="busy === 'profile'">保存资料</el-button>
              </div>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="密码设置" name="password" lazy>
            <el-form ref="passwordForm" :model="password" :rules="passwordRules" :disabled="!!busy"
              class="settings-form" label-width="88px" @submit.prevent="savePassword">
              <el-form-item label="当前密码" prop="oldPassword">
                <el-input v-model="password.oldPassword" type="password" autocomplete="current-password" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
                <el-input v-model="password.newPassword" type="password" autocomplete="new-password" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="password.confirmPassword" type="password" autocomplete="new-password" show-password />
              </el-form-item>
              <div class="settings-actions">
                <el-button type="primary" native-type="submit" :loading="busy === 'password'">修改密码</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
    <el-empty v-else-if="!loading" description="暂时无法加载个人资料">
      <el-button @click="loadProfile">重试</el-button>
    </el-empty>
    <el-dialog v-model="avatarOpen" title="预览新头像" width="min(380px, 92vw)" append-to-body
      :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="clearAvatarPreview">
      <div class="avatar-preview"><el-avatar :size="128" :src="avatarPreview" alt="新头像预览" /></div>
      <template #footer>
        <el-button :disabled="!!busy" @click="avatarOpen = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'avatar'" @click="saveAvatar">保存头像</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.personal-center { max-width: 1240px; margin: 0 auto; padding: 8px 4px 24px; }
h1 { margin: 0 0 6px; font-size: 24px; }
h2 { margin: 0; font-size: 17px; }
.profile-description { margin: 0 0 22px; color: var(--muted); }
.profile-grid { display: grid; grid-template-columns: minmax(260px, 2fr) minmax(0, 3fr); align-items: start; gap: 18px; }
.identity { display: grid; justify-items: center; gap: 10px; padding: 6px 0 18px; }
.identity strong { font-size: 18px; text-align: center; overflow-wrap: anywhere; }
.account-name { color: var(--muted); }
.account-details { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px 16px; margin: 0; padding-top: 18px; border-top: 1px solid var(--border); }
.account-details dt { color: var(--muted); }
.account-details dd { margin: 0; overflow-wrap: anywhere; }
.settings-form { max-width: 560px; padding: 8px 0 2px; }
.settings-actions { display: flex; justify-content: flex-end; }
.avatar-preview { display: flex; justify-content: center; padding: 8px 0; }
@media (max-width: 920px) {
  .profile-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
