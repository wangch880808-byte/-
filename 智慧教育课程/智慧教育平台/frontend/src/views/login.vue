<template>
  <div class="login">
    <el-form ref="loginRef" :model="form" :rules="rules" class="login-form" @submit.prevent="submit">
      <img :src="logo" alt="校徽" class="login-logo" />
      <h2>知教·智慧教育课程平台</h2>
      <el-radio-group v-model="form.role" aria-label="登录入口">
        <el-radio-button value="admin">管理员</el-radio-button><el-radio-button value="teacher">教师</el-radio-button><el-radio-button value="student">学生</el-radio-button>
      </el-radio-group>
      <el-form-item label="账号" prop="username"><el-input v-model="form.username" aria-label="账号" autocomplete="username" /></el-form-item>
      <el-form-item label="密码" prop="password"><el-input v-model="form.password" aria-label="密码" type="password" show-password autocomplete="current-password" @keyup.enter="submit" /></el-form-item>
      <el-form-item v-if="captchaEnabled" label="验证码" prop="code">
        <el-input v-model="form.code" aria-label="验证码" style="width:60%" @keyup.enter="submit" />
        <button type="button" class="captcha-button" aria-label="更换验证码" @click="refreshCaptcha"><img v-if="image" :src="image" alt="验证码" /></button>
      </el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-button type="primary" :loading="busy" class="login-button" @click="submit">登录</el-button>
    </el-form>
  </div>
</template>
<script setup>
import { getCodeImg } from '@/api/login'
import useUserStore from '@/store/modules/user'
const router=useRouter(),route=useRoute(),user=useUserStore()
const loginRef=ref(),busy=ref(false),error=ref(''),image=ref(''),captchaEnabled=ref(true)
const logo=import.meta.env.BASE_URL+'logo.png'
const form=reactive({username:'',password:'',role:'admin',code:'',uuid:''})
const rules={username:[{required:true,message:'请输入账号'}],password:[{required:true,message:'请输入密码'}],code:[{validator:(_rule,value,callback)=>callback(captchaEnabled.value&&!value?new Error('请输入验证码'):undefined)}]}
let captchaSequence=0
async function refreshCaptcha() {
  const sequence=++captchaSequence
  try { const data=await getCodeImg();if(sequence!==captchaSequence)return;captchaEnabled.value=data.captchaEnabled!==false;image.value=data.img?'data:image/jpeg;base64,'+data.img:'';form.uuid=data.uuid||'';form.code='' }
  catch(e){error.value=e.message||'验证码加载失败'}
}
async function submit() {
  if(busy.value)return
  try {await loginRef.value.validate()} catch{return}
  busy.value=true;error.value=''
  try {await user.login(form);await user.getInfo();form.password='';const target=String(route.query.redirect||'');await router.replace(target.startsWith('/'+user.role+'/')?target:'/'+user.role+'/overview')}
  catch(e){error.value=e.message||'登录失败';await refreshCaptcha()}
  finally{busy.value=false}
}
refreshCaptcha()
</script>
<style scoped>
.login{height:100%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#e8f4ff,#edf1f7)}
.login-form{width:430px;max-width:calc(100% - 32px);padding:32px;background:white;border-radius:12px;box-shadow:0 16px 50px #20385618}
.login-form h2{text-align:center;font-size:21px;margin:14px 0 24px}.login-logo{display:block;width:58px;height:58px;object-fit:contain;margin:auto}.login-form .el-radio-group{display:flex;justify-content:center;margin-bottom:24px}.login-button{width:100%;margin-top:16px}.captcha-button{border:0;background:transparent;cursor:pointer;width:38%;height:34px}.captcha-button img{max-width:100%;max-height:34px}
</style>
