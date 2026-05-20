<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo -->
      <div class="brand-section" @click="handleLogoClick">
        <div class="brand-mark">
          <img src="/agent/idle.png" alt="TiMo" class="brand-logo" />
        </div>
        <span class="brand-name">TiMo</span>
      </div>

      <!-- Hidden admin entry -->
      <Transition name="form-slide">
        <div v-if="showSecretInput" class="secret-section">
          <div class="field">
            <label class="field-label">管理员密钥</label>
            <input v-model="adminSecret" type="password" placeholder="输入管理员密钥" class="form-input" />
          </div>
          <p class="secret-hint">验证密钥后，当前账号将获得管理员入口</p>
        </div>
      </Transition>

      <!-- Headline -->
      <div class="headline-section">
        <Transition name="headline" mode="out-in">
          <div :key="isRegister ? 'reg' : 'login'">
            <h1>{{ isRegister ? '创建账号' : '欢迎回来' }}</h1>
            <p class="subtitle">{{ isRegister ? '开启你的学习之旅' : '继续上次的学习' }}</p>
          </div>
        </Transition>
      </div>

      <!-- Form -->
      <div class="form-section">
        <Transition name="form-slide" mode="out-in">
          <form
            :key="isRegister ? 'reg' : 'login'"
            @submit.prevent="handleSubmit"
            class="login-form"
          >
            <!-- 昵称 (注册) -->
            <div v-if="isRegister" class="field">
              <label class="field-label">昵称</label>
              <input
                v-model="form.nickname"
                type="text"
                placeholder="你希望怎么被称呼？"
                class="form-input"
                required
              />
            </div>

            <!-- 邮箱 -->
            <div class="field">
              <label class="field-label">邮箱</label>
              <input
                v-model="form.email"
                type="email"
                placeholder="you@example.com"
                class="form-input"
                required
              />
            </div>

            <!-- 密码 -->
            <div class="field">
              <div class="field-row">
                <label class="field-label">密码</label>
                <span v-if="!isRegister" class="forgot-link" @click="$router.push('/forgot-password')">忘记密码？</span>
              </div>
              <input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                class="form-input"
                required
              />
            </div>

            <!-- 确认密码 (注册) -->
            <div v-if="isRegister" class="field">
              <label class="field-label">确认密码</label>
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                class="form-input"
                required
              />
            </div>

            <!-- 记住密码 (登录) -->
            <label v-if="!isRegister" class="remember-row">
              <input type="checkbox" v-model="rememberMe" class="remember-checkbox" />
              <span class="remember-text">记住邮箱</span>
            </label>

            <div>
              <button type="submit" class="submit-btn" :disabled="loading">
                <span v-if="loading" class="loading-spinner"></span>
                <span v-else class="btn-text">{{ isRegister ? '创建账号' : '继续' }}</span>
              </button>
            </div>
          </form>
        </Transition>
      </div>

      <!-- Switch -->
      <p class="switch-hint">
        <Transition name="headline" mode="out-in">
          <span :key="isRegister ? 'reg' : 'login'">
            {{ isRegister ? '已有账号？' : '还没有账号？' }}
            <span class="switch-link" @click="isRegister = !isRegister">
              {{ isRegister ? '登录' : '注册' }}
            </span>
          </span>
        </Transition>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { login, register } from '../api/auth'
import { verifyAdminSecret } from '../api/admin'

const router = useRouter()
const userStore = useUserStore()
const isRegister = ref(false)
const loading = ref(false)
const rememberMe = ref(false)
const form = ref({ email: '', password: '', nickname: '', confirmPassword: '' })

// Admin hidden entry
const showSecretInput = ref(false)
const adminSecret = ref('')
let clickCount = 0
let clickTimer = null

function handleLogoClick() {
  clickCount++
  if (clickTimer) clearTimeout(clickTimer)
  clickTimer = setTimeout(() => { clickCount = 0 }, 3000)
  if (clickCount >= 5) {
    clickCount = 0
    showSecretInput.value = !showSecretInput.value
  }
}

onMounted(() => {
  const saved = localStorage.getItem('timo_remember')
  if (saved) {
    try {
      const { email } = JSON.parse(saved)
      form.value.email = email || ''
      rememberMe.value = true
    } catch { localStorage.removeItem('timo_remember') }
  }
})

async function handleSubmit() {
  if (!form.value.email || !form.value.password) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }
  if (isRegister.value) {
    if (!form.value.nickname) {
      ElMessage.warning('请输入昵称')
      return
    }
    if (form.value.password !== form.value.confirmPassword) {
      ElMessage.warning('两次输入的密码不一致')
      return
    }
  }
  loading.value = true
  try {
    const res = isRegister.value
      ? await register({ email: form.value.email, password: form.value.password, nickname: form.value.nickname })
      : await login({ email: form.value.email, password: form.value.password })
    userStore.setToken(res.data.token)
    userStore.setUserInfo({ userId: res.data.userId, email: res.data.email, nickname: res.data.nickname, role: res.data.role })

    // Admin secret verification
    if (showSecretInput.value && adminSecret.value && !isRegister.value) {
      try {
        const secretRes = await verifyAdminSecret(adminSecret.value)
        userStore.setToken(secretRes.data.token)
        userStore.setUserInfo({ ...userStore.userInfo, role: secretRes.data.role })
      } catch (e) { console.warn('Admin secret verify failed:', e) }
    }

    if (!isRegister.value && rememberMe.value) {
      localStorage.setItem('timo_remember', JSON.stringify({ email: form.value.email }))
    } else {
      localStorage.removeItem('timo_remember')
    }
    ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
    if (isRegister.value) {
      localStorage.setItem('justRegistered', 'true')
    }

    const role = userStore.userInfo?.role
    router.push(role === 'SUPER_ADMIN' || role === 'ADMIN' ? '/admin' : '/')
  } catch (e) {
    // error handled by request interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-page);
  padding: 60px 24px;
}

.login-container {
  width: 100%;
  max-width: 340px;
}

/* === Brand === */
.brand-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 48px;
  animation: brand-in 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

@keyframes brand-in {
  from { opacity: 0; transform: scale(0.8) translateY(-10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.brand-mark {
  display: flex;
  align-items: center;
}

.brand-logo {
  width: 52px;
  height: 52px;
  object-fit: contain;
  border-radius: 50%;
  transition: transform 0.3s ease;
  pointer-events: none;
}

.brand-section:hover .brand-logo {
  transform: rotate(8deg) scale(1.05);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: 0.05em;
}

/* === Headline transitions === */
.headline-section {
  text-align: center;
  margin-bottom: 40px;
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.headline-section h1 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
  line-height: 1.3;
  letter-spacing: -0.01em;
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-weight: 400;
}

.headline-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.headline-leave-active {
  transition: all 0.15s ease-in;
}

.headline-enter-from {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
}

.headline-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}

/* === Form transitions === */
.form-section {
  margin-bottom: 28px;
  min-height: 200px;
}

.form-slide-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.form-slide-leave-active {
  transition: all 0.15s ease-in;
}

.form-slide-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.form-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  animation: field-in 0.3s ease both;
}

@keyframes field-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.field:nth-child(1) { animation-delay: 0.05s; }
.field:nth-child(2) { animation-delay: 0.1s; }
.field:nth-child(3) { animation-delay: 0.15s; }
.field:nth-child(4) { animation-delay: 0.2s; }
.field:nth-child(5) { animation-delay: 0.25s; }

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-regular);
  letter-spacing: 0.01em;
}

.field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.forgot-link {
  font-size: 12px;
  color: var(--color-primary-dark);
  cursor: pointer;
  font-weight: 500;
  transition: color 0.2s ease;
}

.forgot-link:hover {
  color: var(--color-primary);
}

/* === Remember === */
.remember-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.remember-checkbox {
  width: 16px;
  height: 16px;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.remember-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 400;
}

.form-input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background: #fff;
  font-family: var(--font-family);
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-primary);
  outline: none;
  transition: border-color 0.25s ease, box-shadow 0.25s ease, background 0.25s ease;
}

.form-input::placeholder {
  color: var(--color-text-placeholder);
  font-weight: 400;
}

.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(122, 158, 126, 0.1);
  background: #fcfcfb;
}

.submit-btn {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-family: var(--font-family);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.25s ease, transform 0.15s ease, box-shadow 0.25s ease;
  margin-top: 4px;
  position: relative;
  overflow: hidden;
}

.submit-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.1), transparent);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.submit-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  box-shadow: 0 2px 8px rgba(122, 158, 126, 0.25);
}

.submit-btn:hover:not(:disabled)::after {
  opacity: 1;
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.985);
  background: var(--color-primary-darker);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-text {
  position: relative;
  z-index: 1;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* === Switch === */
@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.switch-hint {
  text-align: center;
  margin-top: 28px;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 400;
  min-height: 20px;
  animation: fade-in 0.5s ease 0.45s both;
}

.switch-link {
  color: var(--color-primary-dark);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
  position: relative;
}

.switch-link::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--color-primary-dark);
  transform: scaleX(0);
  transition: transform 0.25s ease;
  transform-origin: center;
}

.switch-link:hover::after {
  transform: scaleX(1);
}

@media (max-width: 480px) {
  .headline-section h1 {
    font-size: 24px;
  }
}
</style>
