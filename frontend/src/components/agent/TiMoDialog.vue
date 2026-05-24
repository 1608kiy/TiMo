<template>
  <div class="timo-dialog-overlay" @click.self="handleClose">
    <div class="timo-dialog" :style="dialogStyle" ref="dialogRef">
      <div class="dialog-header">
        <TiMoAvatar :state="agentStore.tiMoState" size="small" />
        <div class="header-info">
          <span class="header-name">TiMo</span>
          <span class="header-status" :class="agentStore.apiStatus">
            {{ agentStore.apiStatus === 'online' ? '在线' : '离线' }}
          </span>
        </div>
        <el-icon class="close-btn" @click="handleClose"><Close /></el-icon>
      </div>

      <div class="dialog-messages" ref="messagesRef">
        <!-- Exam Planning Mode -->
        <template v-if="examPlanStore.isActive">
          <div v-for="(msg, i) in examPlanStore.dialogMessages" :key="'ep-' + i"
            :class="['message', msg.role === 'user' ? 'user-msg' : 'agent-msg']">
            <TiMoAvatar v-if="msg.role === 'assistant'" :state="agentStore.tiMoState" size="small" />
            <div class="msg-bubble">{{ msg.content }}</div>
          </div>

          <!-- Quick reply options for exam planning -->
          <div v-if="currentOptions.length && !dialoguing" class="message agent-msg">
            <TiMoAvatar state="idle" size="small" />
            <div class="options-area">
              <button
                v-for="opt in currentOptions"
                :key="opt.value"
                class="option-btn"
                @click="selectExamOption(opt)"
              >
                {{ opt.label }}
              </button>
            </div>
          </div>

          <!-- Plan result card -->
          <div v-if="examPlanStore.generatedPlan" class="plan-result-card">
            <div class="plan-card-header">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>你的备考计划</span>
            </div>
            <div class="plan-card-body">
              <div class="plan-row"><span class="plan-label">考试类型</span><span class="plan-value">{{ examPlanStore.generatedPlan.examType }}</span></div>
              <div class="plan-row"><span class="plan-label">每日新词</span><span class="plan-value">{{ examPlanStore.generatedPlan.dailyNewWords }} 个</span></div>
              <div class="plan-row"><span class="plan-label">每日复习</span><span class="plan-value">{{ examPlanStore.generatedPlan.dailyReviewWords }} 个</span></div>
              <div class="plan-row"><span class="plan-label">预计天数</span><span class="plan-value">{{ examPlanStore.generatedPlan.estimatedDays }} 天</span></div>
            </div>
            <div class="plan-card-actions">
              <button class="plan-action-btn primary" @click="confirmPlan">确认，开始执行</button>
              <button class="plan-action-btn secondary" @click="restartExamPlan">再调调看</button>
            </div>
          </div>
        </template>

        <!-- General Chat Mode -->
        <template v-else>
          <!-- History messages from backend -->
          <div v-for="(msg, i) in agentStore.messages" :key="'chat-' + i"
            :class="['message', msg.role === 'user' ? 'user-msg' : 'agent-msg']">
            <TiMoAvatar v-if="msg.role === 'assistant'" :state="agentStore.tiMoState" size="small" />
            <div class="msg-content-wrap">
              <div class="msg-bubble">{{ msg.content }}</div>
              <!-- Action buttons on assistant messages -->
              <div v-if="msg.role === 'assistant' && msg.actions && msg.actions.length" class="msg-actions">
                <button v-for="action in msg.actions" :key="action" class="action-btn" @click="handleAction(action)">
                  {{ action }}
                </button>
              </div>
            </div>
          </div>
          <div v-if="agentStore.messages.length === 0" class="empty-hint">
            <TiMoAvatar state="idle" size="large" />
            <p class="empty-title">{{ pageTitleConfig.greeting }}</p>
            <p class="empty-sub">{{ pageTitleConfig.description }}</p>
            <div class="quick-starters">
              <button v-for="starter in pageTitleConfig.starters" :key="starter"
                class="starter-btn" @click="sendStarter(starter)">
                {{ starter }}
              </button>
            </div>
          </div>
        </template>

        <!-- Typing indicator -->
        <div v-if="dialoguing" class="message agent-msg">
          <TiMoAvatar state="thinking" size="small" />
          <div class="msg-bubble typing">思考中...</div>
        </div>
      </div>

      <!-- Input area (general mode only) -->
      <div v-if="!examPlanStore.isActive" class="dialog-input">
        <el-input v-model="inputText" placeholder="问 TiMo 任何问题..." @keyup.enter="sendMessage" :disabled="agentStore.apiStatus === 'offline'" size="large">
          <template #append>
            <el-button @click="sendMessage" :disabled="!inputText.trim() || agentStore.apiStatus === 'offline'" type="primary">
              发送
            </el-button>
          </template>
        </el-input>
      </div>

      <el-dialog v-model="nicknameDialogVisible" title="先认识一下" width="360px" :append-to-body="true">
        <p class="nickname-tip">你希望我怎么称呼你？</p>
        <el-input v-model="nicknameInput" placeholder="比如：小王、学长、同学" maxlength="20" />
        <template #footer>
          <el-button @click="skipNickname">先跳过</el-button>
          <el-button type="primary" :disabled="!nicknameInput.trim()" @click="saveNickname">保存称呼</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onMounted, onBeforeUnmount } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useExamPlanStore } from '../../stores/examPlan'
import { sendChatMessage, loadChatHistory } from '../../api/agent'
import { startExamPlanDialog, continueExamPlanDialog } from '../../api/examPlan'
import TiMoAvatar from './TiMoAvatar.vue'
import { Close } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { updatePreferences } from '../../api/user'

const router = useRouter()
const agentStore = useAgentStore()
const examPlanStore = useExamPlanStore()
const userStore = useUserStore()
const inputText = ref('')
const messagesRef = ref(null)
const dialogRef = ref(null)
const dialoguing = ref(false)
const currentOptions = ref([])
const nicknameDialogVisible = ref(false)
const nicknameInput = ref('')

const windowWidth = ref(window.innerWidth)
const windowHeight = ref(window.innerHeight)
const measuredHeight = ref(0)

function onWindowResize() {
  windowWidth.value = window.innerWidth
  windowHeight.value = window.innerHeight
  nextTick(() => {
    if (dialogRef.value) {
      measuredHeight.value = dialogRef.value.getBoundingClientRect().height
    }
  })
}

const dialogStyle = computed(() => {
  const rect = agentStore.fabRect
  const gap = 12
  const margin = 16
  const dialogWidth = Math.min(480, windowWidth.value - margin * 2)
  const estimatedHeight = measuredHeight.value || Math.min(500, windowHeight.value * 0.7)

  let left = rect.x + rect.width / 2 - dialogWidth / 2
  let top = rect.y + rect.height + gap

  if (left < margin) left = margin
  if (left + dialogWidth > windowWidth.value - margin) {
    left = windowWidth.value - dialogWidth - margin
  }
  // 若下方放不下，翻到上方；若上方也放不下，靠下边缘
  if (top + estimatedHeight > windowHeight.value - margin) {
    const topFlip = rect.y - estimatedHeight - gap
    top = topFlip > margin ? topFlip : windowHeight.value - estimatedHeight - margin
  }
  if (top < margin) top = margin
  return { left: left + 'px', top: top + 'px', width: dialogWidth + 'px' }
})

const pageConfigs = {
  dashboard: {
    greeting: '你好！我是 TiMo',
    description: '你的每日学习教练，有什么可以帮你的？',
    starters: ['今天学什么好？', '帮我推荐学习计划', '分析薄弱环节', '查看顽固词']
  },
  wordSelect: {
    greeting: '选词顾问 TiMo',
    description: '帮你挑选最合适的单词进行学习',
    starters: ['推荐适合我的词单', '帮我挑高频词', '查看顽固词', '今天学什么好？']
  },
  quickMemory: {
    greeting: 'TiMo 观察中',
    description: '不认识的单词可以问我助记技巧',
    starters: ['这个词怎么记？', '有什么助记技巧？', '查看词根分析']
  },
  deepLearning: {
    greeting: '深度学习助手 TiMo',
    description: '帮你深入理解单词的语境和用法',
    starters: ['生成短文填空', '这个词怎么用？', '查看近义词辨析', '分析薄弱环节']
  },
  review: {
    greeting: '复习调度员 TiMo',
    description: '帮你分析复习情况，优化记忆策略',
    starters: ['为什么这个词总是忘？', '分析复习情况', '查看顽固词', '复习策略建议']
  },
  wordbank: {
    greeting: '单词百科 TiMo',
    description: '帮你深度解析单词的词根、词源和用法',
    starters: ['这个词怎么记？', 'X 和 Y 有什么区别？', '查看词根分析', '考点预测']
  },
  stats: {
    greeting: '数据分析师 TiMo',
    description: '帮你解读学习数据，发现改进方向',
    starters: ['帮我分析学习情况', '生成周报', '预测学习进度', '查看薄弱环节']
  },
  profile: {
    greeting: '计划管理员 TiMo',
    description: '帮你调整学习计划和偏好设置',
    starters: ['重新规划备考计划', '调整每日新词量', '修改学习偏好']
  },
  calendar: {
    greeting: '日历助手 TiMo',
    description: '帮你查看和分析学习日历',
    starters: ['这个月学习情况', '查看缺勤记录', '总结本月学习']
  }
}

const pageTitleConfig = computed(() => {
  return pageConfigs[agentStore.currentPage] || pageConfigs.dashboard
})

function handleClose() {
  if (examPlanStore.isActive) {
    examPlanStore.cancelPlanning()
    localStorage.setItem('timo_plan_skipped', 'true')
  }
  agentStore.toggleDialog()
}

// === Load History from Backend ===

async function fetchHistory() {
  if (!agentStore.sessionId || agentStore.messages.length > 0) return
  try {
    const res = await loadChatHistory(agentStore.sessionId)
    if (res.data && res.data.length) {
      res.data.forEach(m => {
        const msg = { role: m.role, content: m.content }
        if (m.actions) {
          try { msg.actions = JSON.parse(m.actions) } catch {}
        }
        agentStore.addMessage(msg)
      })
    }
  } catch { /* no history yet */ }
}

// === Action Handling ===

function handleAction(action) {
  if (action.includes('快速记忆')) {
    router.push('/quick-memory')
    agentStore.toggleDialog()
  } else if (action.includes('深度学习')) {
    if (agentStore._pendingDeepLearningWordIds) {
      router.push({ path: '/deep-learning', query: { words: agentStore._pendingDeepLearningWordIds } })
      delete agentStore._pendingDeepLearningWordIds
    } else {
      router.push('/deep-learning')
    }
    agentStore.toggleDialog()
  } else if (action.includes('周报')) {
    inputText.value = '给我看周报'
    sendMessage()
  } else if (action.includes('今日学习') || action.includes('开始学习')) {
    router.push('/word-select')
    agentStore.toggleDialog()
  } else {
    inputText.value = action
    sendMessage()
  }
}

// === Exam Planning Mode ===

async function startExamPlan() {
  examPlanStore.startPlanning()
  dialoguing.value = true
  agentStore.setTiMoState('thinking')
  try {
    const res = await startExamPlanDialog()
    const data = res.data
    examPlanStore.setStage(data.stage)
    examPlanStore.addDialogMessage({
      role: 'assistant',
      content: data.message
    })
    currentOptions.value = data.options || []
  } catch {
    examPlanStore.addDialogMessage({
      role: 'assistant',
      content: '抱歉，出了点问题，请稍后再试。'
    })
  } finally {
    dialoguing.value = false
    agentStore.setTiMoState('idle')
  }
}

async function selectExamOption(opt) {
  examPlanStore.addDialogMessage({ role: 'user', content: opt.label })
  currentOptions.value = []
  await sendExamAnswer(opt.value)
}

async function sendExamAnswer(answer) {
  dialoguing.value = true
  agentStore.setTiMoState('thinking')
  try {
    const res = await continueExamPlanDialog(answer)
    const data = res.data
    examPlanStore.setStage(data.stage)
    examPlanStore.addDialogMessage({
      role: 'assistant',
      content: data.message
    })
    currentOptions.value = data.options || []

    if (data.planReady && data.planSummary) {
      examPlanStore.setGeneratedPlan(data.planSummary)
      currentOptions.value = []
    }
  } catch {
    examPlanStore.addDialogMessage({
      role: 'assistant',
      content: '抱歉，出了点问题，请稍后再试。'
    })
  } finally {
    dialoguing.value = false
    agentStore.setTiMoState('idle')
  }
}

function confirmPlan() {
  examPlanStore.finishPlanning(examPlanStore.generatedPlan)
  agentStore.setConversationType('general')
  agentStore.addMessage({
    role: 'assistant',
    content: '计划已确认！现在去选词页面开始你的备考之旅吧～'
  })
  router.push('/word-select')
}

function restartExamPlan() {
  examPlanStore.cancelPlanning()
  startExamPlan()
}

// === General Chat Mode ===

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  if (text.includes('制定学习计划') || text.includes('备考规划')) {
    inputText.value = ''
    agentStore.setConversationType('exam_planning')
    examPlanStore.startPlanning()
    startExamPlan()
    return
  }

  agentStore.addMessage({ role: 'user', content: text })
  inputText.value = ''
  agentStore.setTiMoState('thinking')
  sending.value = true

  try {
    const res = await sendChatMessage({
      message: text,
      sessionId: agentStore.sessionId
    })
    agentStore.setSessionId(res.data.sessionId)
    const replyMsg = { role: 'assistant', content: res.data.reply }
    if (res.data.suggestedActions && res.data.suggestedActions.length) {
      replyMsg.actions = res.data.suggestedActions
    }
    agentStore.addMessage(replyMsg)

    // Set tiMoState from backend response
    if (res.data.tiMoState) {
      agentStore.setTiMoState(res.data.tiMoState, 3000)
    }
  } catch {
    agentStore.addMessage({ role: 'assistant', content: '抱歉，我暂时无法回复，请稍后再试。' })
  } finally {
    agentStore.setTiMoState('idle')
    sending.value = false
  }
}

const sending = ref(false)

function sendStarter(text) {
  inputText.value = text
  sendMessage()
}

function ensureNicknamePrompt() {
  const hasNickname = !!userStore.userInfo?.nickname
  if (!hasNickname && !nicknameDialogVisible.value) {
    nicknameDialogVisible.value = true
  }
}

async function saveNickname() {
  const nickname = nicknameInput.value.trim()
  if (!nickname) return
  try {
    await updatePreferences({ nickname })
    userStore.userInfo = { ...(userStore.userInfo || {}), nickname }
    nicknameDialogVisible.value = false
    agentStore.addMessage({ role: 'assistant', content: `好呀，那我以后就叫你“${nickname}”了。` })
  } catch {
    agentStore.addMessage({ role: 'assistant', content: '保存称呼时出了点问题，你也可以先直接和我聊天。' })
  }
}

function skipNickname() {
  nicknameDialogVisible.value = false
}

// Expose startExamPlan for parent components
defineExpose({ startExamPlan })

let examPlanStarting = false
function tryStartExamPlan() {
  if (agentStore.conversationType === 'exam_planning' && !examPlanStore.isActive && !examPlanStarting) {
    examPlanStarting = true
    startExamPlan().finally(() => { examPlanStarting = false })
  }
}

onMounted(() => {
  window.addEventListener('resize', onWindowResize)
  nextTick(() => {
    if (dialogRef.value) {
      measuredHeight.value = dialogRef.value.getBoundingClientRect().height
    }
  })
  tryStartExamPlan()
  fetchHistory()
  ensureNicknamePrompt()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize)
})

watch(() => agentStore.isOpen, (open) => {
  if (open) {
    tryStartExamPlan()
    fetchHistory()
    ensureNicknamePrompt()
  }
})

watch(() => agentStore.messages.length, () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
    if (dialogRef.value) {
      measuredHeight.value = dialogRef.value.getBoundingClientRect().height
    }
  })
})

watch(() => examPlanStore.dialogMessages.length, () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
    if (dialogRef.value) {
      measuredHeight.value = dialogRef.value.getBoundingClientRect().height
    }
  })
})
</script>

<style scoped>
.timo-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(4px);
  animation: overlayFadeIn 0.2s ease;
}

.timo-dialog {
  position: absolute;
  max-height: calc(100vh - 100px);
  background: #FFFFFF;
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialogSlideIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.dialog-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 2px solid var(--color-border-lighter);
  background: #FFFFFF;
}

.header-info { flex: 1; }
.header-name { font-weight: 800; font-size: 15px; color: var(--color-text-primary); }
.header-status { font-size: 12px; margin-left: 6px; font-weight: 700; }
.header-status.online { color: var(--color-primary); }
.header-status.offline { color: var(--color-text-muted); }
.close-btn {
  cursor: pointer;
  font-size: 18px;
  color: var(--color-text-muted);
  transition: color 0.2s;
  padding: 4px;
  border-radius: var(--radius-sm);
}
.close-btn:hover { color: var(--color-text-primary); background: var(--color-bg-hover); }

.dialog-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 260px;
  max-height: 460px;
}

.message { display: flex; gap: 8px; align-items: flex-end; animation: msgSlideIn 0.3s ease; }
.user-msg { justify-content: flex-end; }
.user-msg .msg-bubble { width: fit-content; max-width: 80%; }

.msg-bubble {
  padding: 8px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.user-msg .msg-bubble {
  background: #f0f1f5;
  color: var(--color-text-primary);
  border-bottom-right-radius: 6px;
}

.agent-msg .msg-bubble {
  background: #f0f1f5;
  color: var(--color-text-primary);
  border-bottom-left-radius: 6px;
}

.msg-bubble.typing { color: var(--color-text-muted); font-style: italic; }

.msg-content-wrap { max-width: 75%; display: flex; flex-direction: column; gap: 6px; }
.msg-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.action-btn {
  padding: 4px 10px; border-radius: 12px;
  border: 1.5px solid var(--color-primary-light);
  background: var(--color-primary-bg);
  color: var(--color-primary-dark); font-size: 11px; font-weight: 600;
  cursor: pointer; transition: all 0.15s ease;
  font-family: var(--font-family);
}
.action-btn:hover {
  background: var(--color-primary); color: #fff;
  border-color: var(--color-primary);
}

/* === Exam Planning Options === */
.options-area {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 0;
}

.option-btn {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  border: 2px solid var(--color-border-lighter);
  background: #fff;
  color: var(--color-blue);
  font-weight: 700;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  box-shadow: 0 2px 0 var(--color-border-lighter);
  font-family: var(--font-family);
}

.option-btn:hover {
  border-color: var(--color-blue);
  background: var(--color-blue-light);
  box-shadow: 0 3px 0 var(--color-blue-dark);
  transform: translateY(-1px);
}

.option-btn:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 var(--color-border-lighter);
}

/* === Plan Result Card === */
.plan-result-card {
  background: linear-gradient(135deg, #f0f9f1, #e8f5e9);
  border: 2px solid var(--color-primary);
  border-radius: var(--radius-lg);
  padding: 16px;
  margin-top: 4px;
  animation: msgSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.plan-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  font-size: 14px;
  color: var(--color-primary-dark);
  margin-bottom: 12px;
}

.plan-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.plan-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.plan-label {
  color: var(--color-text-secondary);
  font-weight: 600;
}

.plan-value {
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

.plan-card-actions {
  display: flex;
  gap: 8px;
}

.plan-action-btn {
  flex: 1;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
  font-family: var(--font-family);
}

.plan-action-btn.primary {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 2px 0 var(--color-primary-dark);
}

.plan-action-btn.primary:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.plan-action-btn.secondary {
  background: #fff;
  color: var(--color-text-secondary);
  border: 2px solid var(--color-border-lighter);
}

.plan-action-btn.secondary:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
}

/* === General Mode === */
.empty-hint {
  text-align: center;
  padding: 20px 0;
}

.empty-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--color-text-primary);
  margin: 10px 0 4px;
}

.empty-sub {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.quick-starters {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 12px;
}

.starter-btn {
  padding: 8px 14px;
  border-radius: var(--radius-md);
  border: 2px solid var(--color-border-lighter);
  background: #FFFFFF;
  color: var(--color-blue);
  font-weight: 700;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 0 var(--color-border-lighter);
  font-family: var(--font-family);
}

.starter-btn:hover {
  border-color: var(--color-blue);
  background: var(--color-blue-light);
  box-shadow: 0 2px 0 var(--color-blue-dark);
}

.starter-btn:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 var(--color-border-lighter);
}

.dialog-input {
  padding: 12px 16px;
  border-top: 2px solid var(--color-border-lighter);
}

.dialog-input :deep(.el-input__wrapper) {
  border-radius: var(--radius-lg) !important;
  padding-left: 14px !important;
  box-shadow: 0 0 0 2px var(--color-border-lighter) !important;
}

.dialog-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px var(--color-primary-lighter) !important;
}

@keyframes overlayFadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes dialogSlideIn { from { opacity: 0; transform: translateY(-8px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes msgSlideIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
