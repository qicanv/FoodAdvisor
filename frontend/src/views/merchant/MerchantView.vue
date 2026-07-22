<template>
  <div class="merchant-view">
    <header class="merchant-header">
      <div class="container">
        <div class="header-content">
          <div class="logo-section">
            <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
            <span class="brand-name">食尚参谋</span>
          </div>
          <div class="nav-links">
            <a href="/" class="nav-link">返回首页</a>
            <div v-if="isLoggedIn" class="user-info">
              <span class="user-name">{{ userInfo.username }}</span>
              <button class="logout-btn" @click="handleLogout">退出</button>
            </div>
          </div>
        </div>
      </div>
    </header>
    
    <main class="merchant-main">
      <div class="auth-section">
        <div class="auth-container">
          <div class="auth-content">
            <div class="auth-icon">
              <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ffffff" stroke-width="2">
                <path d="M3 7v6h6"></path>
                <path d="M21 17a2 2 0 0 0-2-2H7l-4 4v-6a2 2 0 0 0-2-2"></path>
                <path d="M21 7a2 2 0 0 0-2-2H9"></path>
                <path d="M3 17h18"></path>
              </svg>
            </div>
            <h1 class="auth-title">商户端</h1>
            <p class="auth-subtitle">管理店铺信息，查看经营分析</p>
            
            <div class="auth-tabs">
              <button 
                class="tab-btn" 
                :class="{ active: activeTab === 'login' }" 
                @click="activeTab = 'login'"
              >登录</button>
              <button 
                class="tab-btn" 
                :class="{ active: activeTab === 'register' }" 
                @click="activeTab = 'register'"
              >注册</button>
            </div>
            
            <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="auth-form">
              <div class="form-group">
                <label>用户名</label>
                <input type="text" v-model="loginForm.username" placeholder="请输入用户名" class="form-input" />
                <span v-if="loginErrors.username" class="error-text">{{ loginErrors.username }}</span>
              </div>
              <div class="form-group">
                <label>密码</label>
                <input type="password" v-model="loginForm.password" placeholder="请输入密码" class="form-input" />
                <span v-if="loginErrors.password" class="error-text">{{ loginErrors.password }}</span>
              </div>
              <div class="form-group">
                <label class="checkbox-label">
                  <input type="checkbox" v-model="loginForm.remember" />
                  <span>记住我</span>
                </label>
              </div>
              <span v-if="loginErrors.general" class="error-text general-error">{{ loginErrors.general }}</span>
              <button type="submit" class="submit-btn" :disabled="loginLoading">{{ loginLoading ? '登录中...' : '登录' }}</button>
            </form>
            
            <form v-else @submit.prevent="handleRegister" class="auth-form">
              <div class="form-group">
                <label>用户名</label>
                <input type="text" v-model="registerForm.username" placeholder="请输入用户名" class="form-input" />
                <span v-if="registerErrors.username" class="error-text">{{ registerErrors.username }}</span>
              </div>
              <div class="form-group">
                <label>邮箱</label>
                <input type="email" v-model="registerForm.email" placeholder="请输入邮箱" class="form-input" />
                <span v-if="registerErrors.email" class="error-text">{{ registerErrors.email }}</span>
              </div>
              <div class="form-group">
                <label>密码</label>
                <input type="password" v-model="registerForm.password" placeholder="请输入密码" class="form-input" />
                <span v-if="registerErrors.password" class="error-text">{{ registerErrors.password }}</span>
              </div>
              <div class="form-group">
                <label>确认密码</label>
                <input type="password" v-model="registerForm.confirmPassword" placeholder="请确认密码" class="form-input" />
                <span v-if="registerErrors.confirmPassword" class="error-text">{{ registerErrors.confirmPassword }}</span>
              </div>
              <span v-if="registerErrors.general" class="error-text general-error">{{ registerErrors.general }}</span>
              <button type="submit" class="submit-btn" :disabled="registerLoading">{{ registerLoading ? '注册中...' : '注册' }}</button>
            </form>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const isLoggedIn = ref(false)
const userInfo = ref({ username: '' })
const activeTab = ref('login')

const loginForm = ref({
  username: '',
  password: '',
  remember: false
})

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loginErrors = ref({
  username: '',
  password: '',
  general: ''
})

const registerErrors = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  general: ''
})

const loginLoading = ref(false)
const registerLoading = ref(false)
const loginAttempts = ref(0)
const isLocked = ref(false)

onMounted(() => {
  const token = localStorage.getItem('token')
  const user = localStorage.getItem('user')
  if (token && user) {
    isLoggedIn.value = true
    userInfo.value = JSON.parse(user)
  }
  
  const savedUsername = localStorage.getItem('savedUsername')
  if (savedUsername) {
    loginForm.value.username = savedUsername
    loginForm.value.remember = true
  }
})

const validateLogin = () => {
  loginErrors.value = { username: '', password: '', general: '' }
  
  if (!loginForm.value.username.trim()) {
    loginErrors.value.username = '请输入用户名'
    return false
  }
  
  if (!loginForm.value.password.trim()) {
    loginErrors.value.password = '请输入密码'
    return false
  }
  
  return true
}

const validateRegister = () => {
  registerErrors.value = { username: '', email: '', password: '', confirmPassword: '', general: '' }
  
  if (!registerForm.value.username.trim()) {
    registerErrors.value.username = '请输入用户名'
    return false
  }
  
  if (registerForm.value.username.length < 3 || registerForm.value.username.length > 20) {
    registerErrors.value.username = '用户名长度应在3-20个字符之间'
    return false
  }
  
  if (!registerForm.value.email.trim()) {
    registerErrors.value.email = '请输入邮箱'
    return false
  }
  
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(registerForm.value.email)) {
    registerErrors.value.email = '请输入有效的邮箱地址'
    return false
  }
  
  if (!registerForm.value.password.trim()) {
    registerErrors.value.password = '请输入密码'
    return false
  }
  
  if (registerForm.value.password.length < 6) {
    registerErrors.value.password = '密码长度至少6个字符'
    return false
  }
  
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    registerErrors.value.confirmPassword = '两次输入的密码不一致'
    return false
  }
  
  return true
}

const handleLogin = async () => {
  if (isLocked.value) {
    loginErrors.value.general = '账号已锁定，请稍后再试'
    return
  }
  
  if (!validateLogin()) return
  
  loginLoading.value = true
  loginErrors.value.general = ''
  
  try {
    const response = await request.post('/api/auth/login', {
      username: loginForm.value.username,
      password: loginForm.value.password,
      role: 'MERCHANT'
    })
    
    if (response.success && response.data) {
      const { token, userId, username, nickname, email, role } = response.data
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify({
        id: userId,
        username,
        nickname,
        email,
        role
      }))
      localStorage.setItem('userRole', 'merchant')
      
      if (loginForm.value.remember) {
        localStorage.setItem('savedUsername', loginForm.value.username)
      } else {
        localStorage.removeItem('savedUsername')
      }
      
      loginAttempts.value = 0
      isLoggedIn.value = true
      userInfo.value = { username }
      router.push('/merchant/home')
    } else {
      loginAttempts.value++
      
      if (loginAttempts.value >= 5) {
        isLocked.value = true
        loginErrors.value.general = '登录失败次数过多，账号已锁定，请1分钟后再试'
        
        setTimeout(() => {
          isLocked.value = false
          loginAttempts.value = 0
        }, 60000)
      } else if (response.message) {
        loginErrors.value.general = response.message
      } else {
        loginErrors.value.general = '登录失败，请检查用户名或密码'
      }
    }
  } catch (error) {
    loginErrors.value.general = '网络错误，请稍后重试'
  } finally {
    loginLoading.value = false
  }
}

const handleRegister = async () => {
  if (!validateRegister()) return
  
  registerLoading.value = true
  registerErrors.value.general = ''
  
  try {
    const response = await request.post('/api/auth/register', {
      username: registerForm.value.username,
      password: registerForm.value.password,
      confirmPassword: registerForm.value.confirmPassword,
      email: registerForm.value.email,
      nickname: registerForm.value.nickname || registerForm.value.username,
      role: 'MERCHANT'
    })
    
    if (response.success && response.data) {
      const { token, userId, username, nickname, email, role } = response.data
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify({
        id: userId,
        username,
        nickname,
        email,
        role
      }))
      localStorage.setItem('userRole', 'merchant')
      
      isLoggedIn.value = true
      userInfo.value = { username }
      router.push('/merchant/home')
    } else {
      registerErrors.value.general = response.message || '注册失败'
    }
  } catch (error) {
    registerErrors.value.general = '网络错误，请稍后重试'
  } finally {
    registerLoading.value = false
  }
}

const handleLogout = () => {
  isLoggedIn.value = false
  userInfo.value = { username: '' }
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  localStorage.removeItem('savedUsername')
  router.push('/merchant')
}
</script>

<style scoped>
.merchant-view {
  min-height: 100vh;
  background: #fafafa;
}

.merchant-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 16px 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #52c41a;
}

.nav-links {
  display: flex;
  gap: 16px;
  align-items: center;
}

.nav-link {
  font-size: 14px;
  color: #666666;
  text-decoration: none;
}

.nav-link:hover {
  color: #52c41a;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  font-size: 14px;
  color: #333333;
}

.logout-btn {
  padding: 6px 16px;
  background: #f5f5f5;
  color: #666666;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.logout-btn:hover {
  background: #e8e8e8;
}

.merchant-main {
  padding-top: 0;
}

.auth-section {
  min-height: calc(100vh - 60px);
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 40%, #95de64 70%, #b7eb8f 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  position: relative;
  overflow: hidden;
}

.auth-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url("data:image/svg+xml,%3Csvg viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.05'/%3E%3C/svg%3E");
  pointer-events: none;
}

.auth-container {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 420px;
}

.auth-content {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 48px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.auth-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-title {
  font-size: 28px;
  font-weight: 700;
  color: #333333;
  margin-bottom: 8px;
}

.auth-subtitle {
  font-size: 15px;
  color: #999999;
  margin-bottom: 32px;
}

.auth-tabs {
  display: flex;
  margin-bottom: 28px;
  background: #f5f5f5;
  border-radius: 10px;
  padding: 4px;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  font-size: 15px;
  font-weight: 500;
  color: #666666;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #ffffff;
}

.auth-form {
  text-align: left;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333333;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  box-sizing: border-box;
  transition: all 0.2s;
  background: #ffffff;
}

.form-input:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.1);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label span {
  font-size: 14px;
  color: #666666;
}

.submit-btn {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 12px;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #ffffff;
  transition: all 0.2s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(82, 196, 26, 0.3);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.error-text {
  display: block;
  font-size: 13px;
  color: #ff4d4f;
  margin-top: 6px;
}

.error-text.general-error {
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .auth-content {
    padding: 32px 24px;
  }
  
  .auth-title {
    font-size: 24px;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

@media (max-width: 480px) {
  .auth-icon {
    width: 64px;
    height: 64px;
  }
  
  .auth-content {
    padding: 24px 20px;
  }
}
</style>