<template>
  <div v-if="visible" class="auth-modal-overlay" @click.self="handleClose">
    <div class="auth-modal">
      <div class="auth-modal-header">
        <div class="auth-tabs">
          <button 
            class="auth-tab" 
            :class="{ active: isLogin }" 
            @click="switchToLogin"
          >
            登录
          </button>
          <button 
            class="auth-tab" 
            :class="{ active: !isLogin }" 
            @click="switchToRegister"
          >
            注册
          </button>
        </div>
        <button class="close-btn" @click="handleClose">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      
      <div class="auth-modal-body">
        <form @submit.prevent="handleSubmit" class="auth-form">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <input 
              v-model="form.username" 
              type="text" 
              class="form-input" 
              placeholder="请输入用户名"
              required
            />
          </div>
          
          <div class="form-group">
            <label class="form-label">密码</label>
            <input 
              v-model="form.password" 
              type="password" 
              class="form-input" 
              placeholder="请输入密码"
              required
            />
          </div>
          
          <div v-if="!isLogin" class="form-group">
            <label class="form-label">昵称</label>
            <input 
              v-model="form.nickname" 
              type="text" 
              class="form-input" 
              placeholder="请输入昵称"
              required
            />
          </div>
          
          <div v-if="isLogin" class="form-group form-group-flex">
            <label class="form-checkbox">
              <input type="checkbox" v-model="form.remember" />
              <span>记住我</span>
            </label>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>
          
          <button type="submit" class="auth-submit-btn" :disabled="loading">
            <span v-if="loading" class="loading-spinner"></span>
            {{ loading ? (isLogin ? '登录中...' : '注册中...') : (isLogin ? '登录' : '注册') }}
          </button>
        </form>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
      </div>
      
      <div class="auth-modal-footer">
        <p>其他登录方式</p>
        <div class="social-login">
          <button class="social-btn" title="微信登录">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="#07c160">
              <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.166 6.839 9.489.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.604-3.369-1.341-3.369-1.341-.454-1.155-1.11-1.462-1.11-1.462-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.026A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.295 2.747-1.026 2.747-1.026.546 1.377.202 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.163 22 16.418 22 12c0-5.523-4.477-10-10-10z"/>
            </svg>
          </button>
          <button class="social-btn" title="QQ登录">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="#12B7F5">
              <path d="M12 2C6.477 2 2 6.477 2 12c0 5.013 3.693 9.153 8.505 9.876V14.89h-2.6v-3.09h2.6V9.41c0-2.508 1.493-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V11.8h2.773l-.443 3.09h-2.33v6.786C18.235 21.236 22 17.062 22 12c0-5.523-4.477-10-10-10z"/>
            </svg>
          </button>
          <button class="social-btn" title="微博登录">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="#E6162D">
              <path d="M10.098 20.323c-3.977.391-7.414-1.406-7.672-4.02-.259-2.609 2.759-5.047 6.74-5.441 3.979-.394 7.413 1.404 7.671 4.018.259 2.6-2.759 5.049-6.739 5.443zM9.05 17.219c-.384-.51-.744-1.045-1.076-1.606-.335-.565-.578-1.14-.733-1.725-.156-.59-.207-1.173-.156-1.749.051-.576.207-1.128.469-1.636.262-.509.609-.957 1.035-1.326.426-.37.896-.664 1.404-.879.509-.215 1.045-.344 1.606-.384.561-.04.977.195 1.249.664.272.469.384 1.045.333 1.606-.051.561-.207 1.087-.469 1.562-.262.477-.578.879-.946 1.204-.369.326-.784.578-1.241.754-.457.177-.879.266-1.263.266-.138 0-.284-.014-.437-.04zm3.733-3.677c-.477-.621-.908-1.275-1.292-1.963-.384-.688-.621-1.38-.705-2.077-.085-.698.079-1.362.426-1.963.347-.601.879-1.026 1.562-1.249.683-.223 1.412-.156 2.151.195.739.351 1.362.879 1.834 1.562.473.682.785 1.412.915 2.151.131.739-.044 1.436-.426 2.077-.383.641-.879 1.195-1.5 1.636-.621.441-1.313.713-2.056.811-.743.099-1.441-.079-2.069-.492z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>import { ref, reactive } from 'vue';
import { login, register } from '../api/auth';
const props = defineProps({
 visible: {
 type: Boolean,
 default: false
 }
});
const emit = defineEmits(['close', 'success']);
const isLogin = ref(true);
const loading = ref(false);
const errorMessage = ref('');
const form = reactive({
 username: '',
 password: '',
 nickname: '',
 remember: false
});
const switchToLogin = () => {
 isLogin.value = true;
 errorMessage.value = '';
 resetForm();
};
const switchToRegister = () => {
 isLogin.value = false;
 errorMessage.value = '';
 resetForm();
};
const resetForm = () => {
 form.username = '';
 form.password = '';
 form.nickname = '';
 form.remember = false;
};
const handleClose = () => {
 emit('close');
};
const handleSubmit = async () => {
 errorMessage.value = '';
 loading.value = true;
 try {
 let result;
 if (isLogin.value) {
 result = await login(form.username, form.password);
 }
 else {
 result = await register(form.username, form.password, form.nickname);
 }
 if (result.success) {
 const { accessToken, user } = result.data;
 localStorage.setItem('accessToken', accessToken);
 localStorage.setItem('userInfo', JSON.stringify(user));
 emit('success', user);
 handleClose();
 }
 else {
 errorMessage.value = result.message || (isLogin.value ? '登录失败' : '注册失败');
 }
 }
 catch (err) {
 errorMessage.value = '网络请求失败，请稍后重试';
 }
 finally {
 loading.value = false;
 }
};
</script>

<style scoped>
.auth-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.auth-modal {
  width: 100%;
  max-width: 420px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(255, 103, 0, 0.2);
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-modal-header {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
  position: relative;
  border-bottom: 1px solid #f0f0f0;
}

.auth-tabs {
  display: flex;
  gap: 40px;
}

.auth-tab {
  font-size: 18px;
  font-weight: 600;
  color: #999999;
  background: none;
  border: none;
  padding: 8px 0;
  position: relative;
  transition: color 0.2s;
}

.auth-tab.active {
  color: #ff6700;
}

.auth-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 2px;
  background: #ff6700;
  border-radius: 1px;
}

.close-btn {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #999999;
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s;
}

.close-btn:hover {
  color: #666666;
}

.auth-modal-body {
  padding: 24px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group-flex {
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #333333;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  color: #333333;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  border-color: #ff6700;
  box-shadow: 0 0 0 3px rgba(255, 103, 0, 0.1);
}

.form-input::placeholder {
  color: #cccccc;
}

.form-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666666;
  cursor: pointer;
}

.form-checkbox input {
  width: 14px;
  height: 14px;
  accent-color: #ff6700;
}

.forgot-link {
  font-size: 13px;
  color: #ff6700;
  text-decoration: none;
}

.forgot-link:hover {
  text-decoration: underline;
}

.auth-submit-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #ff6700 0%, #ff9500 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
  margin-top: 8px;
}

.auth-submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 103, 0, 0.3);
}

.auth-submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-message {
  margin-top: 16px;
  padding: 12px;
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
  border-radius: 6px;
  font-size: 13px;
  text-align: center;
}

.auth-modal-footer {
  padding: 20px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.auth-modal-footer p {
  text-align: center;
  font-size: 13px;
  color: #999999;
  margin-bottom: 16px;
}

.social-login {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.social-btn {
  width: 40px;
  height: 40px;
  border: 1px solid #e8e8e8;
  border-radius: 50%;
  background: #ffffff;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
}

.social-btn:hover {
  border-color: #ff6700;
  transform: translateY(-2px);
}

@media (max-width: 480px) {
  .auth-modal {
    margin: 0 20px;
  }
  
  .auth-modal-header {
    padding: 20px;
  }
  
  .auth-modal-body {
    padding: 20px;
  }
  
  .auth-modal-footer {
    padding: 16px 20px;
  }
}
</style>