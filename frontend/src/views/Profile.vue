<template>
  <div class="profile-page fade-in-up">
    <h2 class="page-title">个人中心</h2>

    <el-row :gutter="20">
      <!-- User info card -->
      <el-col :span="8">
        <el-card shadow="hover" class="info-card fade-in-up fade-in-up-delay-1">
          <div class="avatar-section">
            <el-upload
              class="avatar-upload"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
            >
              <div v-if="profile.avatarUrl" class="avatar-img-wrap">
                <img :src="profile.avatarUrl" class="avatar-img" />
                <div class="avatar-overlay">更换头像</div>
              </div>
              <div v-else class="avatar-placeholder">{{ (profile.nickname || 'U')[0] }}</div>
            </el-upload>
            <h3>{{ profile.nickname || '未设置昵称' }}</h3>
            <p class="email">{{ profile.email }}</p>
          </div>
          <el-divider />
          <div class="timo-mini-section">
            <TiMoFAB compact />
            <span class="timo-mini-hint">问我调整学习设置</span>
          </div>
          <el-button type="primary" plain size="small" @click="replan" style="width: 100%; margin-top: 8px;">
            重新规划备考计划
          </el-button>
          <div class="info-row">
            <span class="label">考试类型</span>
            <span>{{ profile.examType || '未设置' }}</span>
          </div>
          <div class="info-row">
            <span class="label">目标词汇</span>
            <span>{{ profile.targetVocab || 5000 }}</span>
          </div>
          <div class="info-row">
            <span class="label">备考天数</span>
            <span>{{ profile.studyDays || 30 }} 天</span>
          </div>
          <div class="info-row">
            <span class="label">每日新词上限</span>
            <span>{{ profile.dailyNewLimit || 20 }} 个</span>
          </div>
          <div class="info-row">
            <span class="label">默认学习模式</span>
            <span>{{ studyModeLabel(profile.defaultStudyMode) }}</span>
          </div>
        </el-card>
      </el-col>

      <!-- Settings panel -->
      <el-col :span="16">
        <el-card shadow="hover" class="fade-in-up fade-in-up-delay-2">
          <template #header><span>偏好设置</span></template>
          <el-form :model="form" label-width="110px" label-position="right">
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" placeholder="输入昵称" />
            </el-form-item>
            <el-form-item label="考试类型">
              <el-select v-model="form.examType" placeholder="选择考试类型" style="width: 100%">
                <el-option v-for="t in examTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标词汇量">
              <el-input-number v-model="form.targetVocab" :min="1000" :max="20000" :step="500" />
            </el-form-item>
            <el-form-item label="备考天数">
              <el-input-number v-model="form.studyDays" :min="7" :max="365" :step="7" />
            </el-form-item>
            <el-form-item label="难度偏好">
              <el-radio-group v-model="form.difficultyPreference">
                <el-radio value="easy">简单</el-radio>
                <el-radio value="standard">标准</el-radio>
                <el-radio value="hard">挑战</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="每日新词上限">
              <el-input-number v-model="form.dailyNewLimit" :min="5" :max="100" :step="5" />
            </el-form-item>
            <el-form-item label="默认学习模式">
              <el-select v-model="form.defaultStudyMode" style="width: 100%">
                <el-option label="快速记忆" value="quick_memory" />
                <el-option label="语境深度学习" value="context_deep" />
                <el-option label="统一复习" value="unified_review" />
              </el-select>
            </el-form-item>
            <el-form-item label="疲劳提醒">
              <el-switch v-model="form.fatigueReminder" active-text="开启" inactive-text="关闭" />
              <span class="form-hint">学习超过20分钟后提醒休息</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile" :loading="saving">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- Danger zone -->
        <el-card shadow="hover" class="danger-card fade-in-up fade-in-up-delay-3" style="margin-top: 16px">
          <template #header><span style="color: #f56c6c">危险操作</span></template>
          <div class="danger-row">
            <div>
              <p class="danger-title">退出登录</p>
              <p class="danger-desc">退出当前账号，需要重新登录</p>
            </div>
            <el-button type="danger" plain @click="logout">退出登录</el-button>
          </div>
          <el-divider style="margin: 16px 0" />
          <div class="danger-row">
            <div>
              <p class="danger-title">注销账户</p>
              <p class="danger-desc">永久删除账户及所有学习数据，此操作不可撤销</p>
            </div>
            <el-button type="danger" @click="confirmDeleteAccount">注销账户</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProfile, updatePreferences, uploadAvatar, deleteAccount } from '../api/user'
import { logout as apiLogout } from '../api/auth'
import TiMoFAB from '../components/agent/TiMoFAB.vue'
import { useUserStore } from '../stores/user'
import { useAgentStore } from '../stores/agent'
import { useExamPlanStore } from '../stores/examPlan'
import { examTypeOptions } from '../constants/examTypes'

const router = useRouter()
const userStore = useUserStore()
const agentStore = useAgentStore()
const examPlanStore = useExamPlanStore()
const saving = ref(false)

const profile = reactive({
  id: null, email: '', nickname: '', examType: '',
  targetVocab: 5000, studyDays: 30, selfAssessedLevel: '', difficultyPreference: 'standard',
  avatarUrl: '', dailyNewLimit: 20, defaultStudyMode: 'context_deep', fatigueReminder: true
})

const form = reactive({
  nickname: '', examType: '', targetVocab: 5000, studyDays: 30, difficultyPreference: 'standard',
  dailyNewLimit: 20, defaultStudyMode: 'context_deep', fatigueReminder: true
})

function studyModeLabel(mode) {
  const map = { quick_memory: '快速记忆', context_deep: '语境深度学习', unified_review: '统一复习' }
  return map[mode] || '语境深度学习'
}

async function loadProfile() {
  try {
    const res = await getProfile()
    Object.assign(profile, res.data)
    Object.assign(form, {
      nickname: res.data.nickname || '',
      examType: res.data.examType || '',
      targetVocab: res.data.targetVocab || 5000,
      studyDays: res.data.studyDays || 30,
      difficultyPreference: res.data.difficultyPreference || 'standard',
      dailyNewLimit: res.data.dailyNewLimit || 20,
      defaultStudyMode: res.data.defaultStudyMode || 'context_deep',
      fatigueReminder: res.data.fatigueReminder !== false
    })
  } catch { /* ignore */ }
}

async function saveProfile() {
  saving.value = true
  try {
    await updatePreferences(form)
    ElMessage.success('设置已保存')
    loadProfile()
  } catch { /* handled by interceptor */ }
  finally { saving.value = false }
}

function beforeAvatarUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt2M) { ElMessage.error('图片大小不能超过2MB'); return false }
  return true
}

async function handleAvatarUpload({ file }) {
  try {
    await uploadAvatar(file)
    ElMessage.success('头像已更新')
    loadProfile()
  } catch { /* handled */ }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    try { await apiLogout() } catch { /* 静默处理 */ }
    userStore.logout()
    router.push('/login')
  } catch { /* cancelled */ }
}

async function confirmDeleteAccount() {
  try {
    await ElMessageBox.confirm(
      '此操作将永久删除您的账户及所有学习数据，包括背单词记录、学习计划、统计数据等，且无法恢复。',
      '确认注销账户',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    )
    // Second confirmation
    try {
      await ElMessageBox.prompt('请输入您的邮箱地址以确认注销', '二次确认', {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        type: 'error',
        inputPlaceholder: profile.email,
        inputValidator: (val) => val === profile.email || '邮箱地址不匹配',
        inputType: 'email'
      })
      await deleteAccount()
      ElMessage.success('账户已注销')
      userStore.logout()
      router.push('/login')
    } catch { /* cancelled */ }
  } catch { /* cancelled */ }
}

onMounted(() => {
  agentStore.setCurrentPage('profile')
  if (userStore.token) {
    loadProfile()
  }
})

function replan() {
  examPlanStore.reset()
  agentStore.setConversationType('exam_planning')
  if (!agentStore.isOpen) agentStore.toggleDialog()
}
</script>

<style scoped>
.profile-page { max-width: 1000px; margin: 0 auto; }
.page-title { font-size: 22px; font-weight: 900; color: var(--color-text-primary); margin: 0 0 20px; }

.info-card {
  text-align: center;
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 4px 0 var(--color-border-lighter);
}

.avatar-section { padding: 32px 0; }

.avatar-upload { display: inline-block; cursor: pointer; }

.avatar-placeholder {
  width: 88px; height: 88px; border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary), var(--color-blue));
  color: #fff; font-size: 36px; font-weight: 900;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 12px;
  box-shadow: 0 4px 0 var(--color-primary-dark);
  border: 3px solid #FFFFFF;
  transition: transform 0.3s ease, border-color 0.3s ease;
  text-shadow: 0 1px 2px rgba(0,0,0,0.15);
}
.avatar-placeholder:hover { transform: scale(1.05); border-color: var(--color-primary-light); }

.avatar-img-wrap {
  position: relative; width: 88px; height: 88px; border-radius: 50%;
  margin: 0 auto 12px; overflow: hidden;
  box-shadow: 0 4px 0 var(--color-primary-dark);
  border: 3px solid #FFFFFF;
  transition: transform 0.3s ease;
}
.avatar-img-wrap:hover { transform: scale(1.05); }
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-overlay {
  position: absolute; inset: 0; background: rgba(0,0,0,0.5);
  color: #fff; font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s ease;
}
.avatar-img-wrap:hover .avatar-overlay { opacity: 1; }

.info-card h3 { font-size: 18px; font-weight: 800; color: var(--color-text-primary); }
.email { color: var(--color-text-secondary); font-size: 13px; margin: 4px 0 0; font-weight: 600; }

.info-row {
  display: flex; justify-content: space-between; padding: 10px 0;
  border-bottom: 1px solid var(--color-border-lighter); font-weight: 600;
  transition: background 0.2s ease, border-radius 0.2s ease;
}
.info-row:last-child { border-bottom: none; }
.info-row:hover { background: var(--color-bg-hover); border-radius: var(--radius-sm); }
.info-row .label { color: var(--color-text-muted); font-weight: 700; }

.form-hint { font-size: 12px; color: var(--color-text-muted); margin-left: 12px; font-weight: 600; }

.danger-card {
  border: 2px solid var(--color-red-light);
  box-shadow: 0 4px 0 var(--color-red-light);
  border-left: 3px solid var(--color-red);
}
.danger-card :deep(.el-card__header) {
  border-bottom-color: var(--color-red-light);
  color: var(--color-red);
  font-weight: 800;
}
.danger-row { display: flex; justify-content: space-between; align-items: center; }
.danger-title { margin: 0; font-weight: 700; color: var(--color-red); }
.danger-desc { margin: 4px 0 0; color: var(--color-text-muted); font-size: 13px; }

.timo-mini-section {
  display: flex; align-items: center; gap: 8px; padding: 8px 0;
}
.timo-mini-hint {
  font-size: 12px; font-weight: 700; color: var(--color-text-muted);
}

:deep(.el-form-item) { transition: background 0.2s ease; }
:deep(.el-form-item:hover) { background: var(--color-bg-hover); border-radius: var(--radius-sm); margin: 0 -8px; padding: 4px 8px; }

@media (max-width: 768px) {
  .profile-page .el-row { flex-direction: column; margin-left: 0 !important; margin-right: 0 !important; }
  .profile-page .el-col { max-width: 100% !important; flex: 0 0 100% !important; padding-left: 0 !important; padding-right: 0 !important; margin-bottom: 16px; }
}
</style>
