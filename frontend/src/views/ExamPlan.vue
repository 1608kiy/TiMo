<template>
  <div class="exam-plan fade-in-up">
    <el-card class="plan-card">
      <template #header>
        <div class="card-header">
          <TiMoAvatar :state="dialoguing ? 'thinking' : 'idle'" size="small" />
          <span class="card-title">备考规划</span>
        </div>
      </template>

      <!-- 对话区域 -->
      <div class="dialog-area" ref="dialogRef">
        <div v-for="(msg, i) in messages" :key="i"
          :class="['message', msg.role === 'user' ? 'user-msg' : 'agent-msg']">
          <TiMoAvatar v-if="msg.role === 'assistant'" state="idle" size="small" />
          <div class="msg-content">
            <div class="msg-bubble">{{ msg.content }}</div>
            <!-- 选项按钮 -->
            <div v-if="msg.options && msg.options.length && i === messages.length - 1 && msg.role === 'assistant'"
              class="options-area">
              <el-button
                v-for="opt in msg.options"
                :key="opt.value"
                size="default"
                class="option-btn"
                :type="selectedOption === opt.value ? 'primary' : ''"
                :disabled="dialoguing"
                @click="selectOption(opt)"
              >
                {{ opt.label }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="dialoguing" class="message agent-msg">
          <TiMoAvatar state="thinking" size="small" />
          <div class="msg-bubble typing">思考中...</div>
        </div>
      </div>

      <!-- 输入区域 (自由输入时显示) -->
      <div v-if="showInput" class="dialog-input">
        <el-input v-model="inputText" :placeholder="inputPlaceholder" @keyup.enter="submitInput">
          <template #append>
            <el-button @click="submitInput" :disabled="!inputText.trim() || dialoguing">
              确认
            </el-button>
          </template>
        </el-input>
      </div>

      <!-- 规划结果 -->
      <div v-if="planSummary" class="plan-result">
        <el-divider />
        <div class="plan-summary">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="考试类型">{{ getExamName(planSummary.examType) }}</el-descriptions-item>
            <el-descriptions-item label="目标词汇">{{ planSummary.targetVocab }}词</el-descriptions-item>
            <el-descriptions-item label="每日新词">{{ planSummary.dailyNewWords }}词</el-descriptions-item>
            <el-descriptions-item label="每日复习">{{ planSummary.dailyReviewWords }}词</el-descriptions-item>
            <el-descriptions-item label="预计天数">{{ planSummary.estimatedDays }}天</el-descriptions-item>
            <el-descriptions-item label="每日时长">{{ planSummary.dailyHours }}小时</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="plan-actions">
          <el-button type="primary" @click="$router.push('/word-select')">开始学习</el-button>
          <el-button @click="startOver">重新规划</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useExamPlanStore } from '../stores/examPlan'
import { useUserStore } from '../stores/user'
import { startExamPlanDialog, continueExamPlanDialog, getExamPlanStatus } from '../api/examPlan'
import { examTypeMap } from '../constants/examTypes'
import TiMoAvatar from '../components/agent/TiMoAvatar.vue'

const router = useRouter()
const examPlanStore = useExamPlanStore()
const userStore = useUserStore()

const messages = ref([])
const dialoguing = ref(false)
const selectedOption = ref(null)
const inputText = ref('')
const inputPlaceholder = ref('输入你的回答...')
const planSummary = ref(null)
const dialogRef = ref(null)

// Determine if current stage needs free text input
const showInput = ref(false)

function getExamName(type) {
  return examTypeMap[type] || type
}

function scrollToBottom() {
  nextTick(() => {
    if (dialogRef.value) {
      dialogRef.value.scrollTop = dialogRef.value.scrollHeight
    }
  })
}

watch(() => messages.value.length, scrollToBottom)

async function startDialog() {
  dialoguing.value = true
  try {
    const res = await startExamPlanDialog()
    const data = res.data
    examPlanStore.setStage(data.stage)
    messages.value.push({
      role: 'assistant',
      content: data.message,
      options: data.options || []
    })
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，出了点问题，请稍后再试。',
      options: []
    })
  } finally {
    dialoguing.value = false
  }
}

async function selectOption(opt) {
  selectedOption.value = opt.value
  messages.value.push({ role: 'user', content: opt.label })
  await sendToBackend(opt.value)
}

async function submitInput() {
  const text = inputText.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  showInput.value = false
  await sendToBackend(text)
}

async function sendToBackend(answer) {
  dialoguing.value = true
  selectedOption.value = null
  try {
    const res = await continueExamPlanDialog(answer)
    const data = res.data
    examPlanStore.setStage(data.stage)

    messages.value.push({
      role: 'assistant',
      content: data.message,
      options: data.options || []
    })

    if (data.planReady && data.planSummary) {
      planSummary.value = data.planSummary
      examPlanStore.setGeneratedPlan(data.planSummary)
    }

    // Determine if next stage needs free input
    freeInputMode.value = false
    showInput.value = false
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，出了点问题，请稍后再试。',
      options: []
    })
  } finally {
    dialoguing.value = false
  }
}

function startOver() {
  messages.value = []
  planSummary.value = null
  examPlanStore.reset()
  startDialog()
}

onMounted(async () => {
  if (!userStore.token) return

  // Check if user already has a plan
  try {
    const res = await getExamPlanStatus()
    const data = res.data
    if (data.planReady && data.planSummary) {
      planSummary.value = data.planSummary
      messages.value.push({
        role: 'assistant',
        content: data.message,
        options: []
      })
    } else {
      await startDialog()
    }
  } catch {
    await startDialog()
  }
})
</script>

<style scoped>
.exam-plan { max-width: 640px; margin: 0 auto; padding: 20px; }

.plan-card {
  min-height: 500px;
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 4px 0 var(--color-border-lighter);
  border-radius: var(--radius-xl);
}

.card-header { display: flex; align-items: center; gap: 10px; }
.card-title { font-weight: 800; font-size: 17px; }

.dialog-area {
  min-height: 300px; max-height: 500px; overflow-y: auto;
  display: flex; flex-direction: column; gap: 12px; padding: 8px 0;
  scroll-behavior: smooth;
}

.message { display: flex; gap: 8px; align-items: flex-start; animation: msgSlideIn 0.3s ease; }
.user-msg { justify-content: flex-end; }
.msg-content { max-width: 80%; }
.msg-bubble {
  padding: 12px 16px; border-radius: var(--radius-lg); font-size: 14px; line-height: 1.6; font-weight: 600;
}
.user-msg .msg-bubble {
  background: var(--color-primary); color: #fff;
  border-radius: var(--radius-xl) 4px var(--radius-xl) var(--radius-xl);
  box-shadow: 0 3px 0 var(--color-primary-dark);
  word-break: break-word;
}
.agent-msg .msg-bubble {
  background: var(--color-bg-hover); color: var(--color-text-primary); border-bottom-left-radius: 4px;
  border-left: 3px solid var(--color-primary-lighter); word-break: break-word;
}
.msg-bubble.typing { color: var(--color-text-muted); font-style: italic; }

.options-area { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }

.option-btn {
  text-align: left; white-space: normal; height: auto; padding: 10px 16px;
  border-radius: var(--radius-full); font-weight: 700;
  border: 2px solid var(--color-border-lighter); box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: all 0.15s ease;
}
.option-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 0 var(--color-border-lighter);
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}
.option-btn:active:not(:disabled) { transform: translateY(2px); box-shadow: 0 1px 0 var(--color-border-lighter); }
.option-btn.el-button--primary {
  background: var(--color-primary); color: #fff; border-color: var(--color-primary-dark);
  box-shadow: 0 3px 0 var(--color-primary-dark);
}

.dialog-input { border-top: 1px solid var(--color-border-lighter); padding-top: 16px; margin-top: 8px; }

.plan-result { background: var(--color-primary-bg); border-radius: var(--radius-md); padding: 16px; margin-top: 8px; }
.plan-summary { margin-bottom: 12px; }
.plan-actions { display: flex; gap: 12px; justify-content: center; }

@keyframes msgSlideIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

@media (max-width: 768px) {
  .exam-plan { padding: 12px; }
  .plan-card { min-height: 400px; }
  .dialog-area { min-height: 250px; max-height: 350px; }
  .msg-content { max-width: 90%; }
}
</style>
