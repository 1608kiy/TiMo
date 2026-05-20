<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo -->
      <div class="brand-section fade-in-up">
        <div class="brand-mark">
          <svg class="brand-svg" width="32" height="32" viewBox="0 0 32 32" fill="none">
            <path d="M16 2 L16 30" stroke="#7a9e7e" stroke-width="1.2" stroke-linecap="round"/>
            <path d="M8 8 L16 14 L24 8" stroke="#7a9e7e" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M8 16 L16 22 L24 16" stroke="#a3c4a6" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M8 24 L16 30 L24 24" stroke="#d4e8d5" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          </svg>
        </div>
        <span class="brand-name">TiMo</span>
      </div>

      <!-- Headline -->
      <div class="headline-section fade-in-up fade-in-up-delay-1">
        <h1>找回密码</h1>
        <p class="subtitle">通过邮箱验证码重置密码</p>
      </div>

      <!-- Step 1: 输入邮箱 -->
      <div v-if="step === 1" class="form-section">
        <form @submit.prevent="handleSendCode" class="login-form">
          <div class="field fade-in-up fade-in-up-delay-2">
            <label class="field-label">注册邮箱</label>
            <input
              v-model="form.email"
              type="email"
              placeholder="you@example.com"
              class="form-input"
              required
            />
          </div>
          <div class="fade-in-up fade-in-up-delay-3">
            <button type="submit" class="submit-btn" :disabled="loading || cooldown > 0">
              <span v-if="loading" class="loading-spinner"></span>
              <span v-else>{{ cooldown > 0 ? `重新发送 (${cooldown}s)` : '发送验证码' }}</span>
            </button>
          </div>
        </form>
      </div>

      <!-- Step 2: 输入验证码 + 新密码 -->
      <div v-if="step === 2" class="form-section">
        <form @submit.prevent="handleReset" class="login-form">
          <div class="field fade-in-up fade-in-up-delay-2">
            <label class="field-label">验证码</label>
            <input
              v-model="form.code"
              type="text"
              placeholder="请输入6位验证码"
              class="form-input"
              maxlength="6"
              required
            />
          </div>
          <div class="field fade-in-up fade-in-up-delay-3">
            <label class="field-label">新密码</label>
            <input
              v-model="form.newPassword"
              type="password"
              placeholder="请输入新密码（至少6位）"
              class="form-input"
              minlength="6"
              required
            />
          </div>
          <div class="field fade-in-up fade-in-up-delay-3">
            <label class="field-label">确认新密码</label>
            <input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入新密码"
              class="form-input"
              required
            />
          </div>
          <div class="fade-in-up fade-in-up-delay-4">
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading" class="loading-spinner"></span>
              <span v-else>重置密码</span>
            </button>
          </div>
        </form>
      </div>

      <!-- Step 3: 成功 -->
      <div v-if="step === 3" class="success-section fade-in-up">
        <div class="success-icon">&#x2705;</div>
        <h2>密码重置成功</h2>
        <p>请使用新密码登录</p>
        <button class="submit-btn" @click="goLogin" style="margin-top: 20px;">去登录</button>
      </div>

      <!-- Back to login -->
      <p class="switch-hint fade-in-up fade-in-up-delay-5">
        <span class="switch-link" @click="$router.push('/login')">← 返回登录</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendResetCode, resetPassword } from '../api/auth'

const router = useRouter()
const step = ref(1)
const loading = ref(false)
const cooldown = ref(0)
const form = ref({ email: '', code: '', newPassword: '', confirmPassword: '' })

let cooldownTimer = null

function startCooldown() {
  cooldown.value = 60
  cooldownTimer = setInterval(() => {
    cooldown.value--
    if (cooldown.value <= 0) {
      clearInterval(cooldownTimer)
    }
  }, 1000)
}

async function handleSendCode() {
  if (!form.value.email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  loading.value = true
  try {
    await sendResetCode(form.value.email)
    ElMessage.success('验证码已发送，请查看邮箱')
    step.value = 2
    startCooldown()
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleReset() {
  if (!form.value.code || form.value.code.length !== 6) {
    ElMessage.warning('请输入6位验证码')
    return
  }
  if (form.value.newPassword.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword(form.value.email, form.value.code, form.value.newPassword)
    step.value = 3
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.push('/login')
}

onUnmounted(() => {
  clearInterval(cooldownTimer)
})
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

.brand-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 48px;
}

.brand-mark {
  display: flex;
  align-items: center;
  animation: breathe 3s ease-in-out infinite;
}

.brand-svg {
  transition: transform 0.3s ease;
}

.brand-section:hover .brand-svg {
  transform: rotate(8deg);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: 0.05em;
}

.headline-section {
  text-align: center;
  margin-bottom: 40px;
}

.headline-section h1 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
  line-height: 1.3;
}

.subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-weight: 400;
}

.form-section {
  margin-bottom: 28px;
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
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-regular);
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
  color: var(--color-text-primary);
  outline: none;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
}

.form-input::placeholder {
  color: var(--color-text-placeholder);
}

.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(122, 158, 126, 0.1);
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
  transition: background 0.25s ease, transform 0.15s ease;
}

.submit-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  box-shadow: 0 2px 8px rgba(122, 158, 126, 0.25);
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.985);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.success-section {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.success-section h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.success-section p {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.switch-hint {
  text-align: center;
  margin-top: 28px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.switch-link {
  color: var(--color-primary-dark);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
}

.switch-link:hover {
  color: var(--color-primary);
}
</style>
