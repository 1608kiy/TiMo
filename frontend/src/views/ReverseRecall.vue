<template>
  <div class="reverse-recall" tabindex="0" @keydown="handleKeydown" ref="rootEl">
    <!-- 顶部进度 -->
    <div class="progress-header">
      <div class="progress-left">
        <button class="exit-btn" @click="confirmExit">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          退出
        </button>
      </div>
      <el-progress
        :percentage="progressPercent"
        :stroke-width="10"
        :show-text="false"
        color="#7a9e7e"
        class="progress-bar"
      />
      <div class="progress-text">{{ currentIndex + 1 }} / {{ words.length }}</div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-block">
      <div class="loading-spinner"></div>
      <p>正在挑选适合主动召回的词...</p>
    </div>

    <!-- 没有候选词 -->
    <div v-else-if="!loading && !words.length && !sessionDone" class="state-block empty">
      <div class="empty-icon">&#x1F331;</div>
      <h3>暂时还没有合适的召回词</h3>
      <p>主动召回适合"半熟"状态的词。先用快速记忆或语境深度学几个回合，再回来吧~</p>
      <el-button type="primary" @click="$router.push('/word-select')">去选词学习</el-button>
    </div>

    <!-- 召回主区 -->
    <div v-else-if="currentWord && !sessionDone" class="recall-card">
      <div class="recall-prompt">
        <div class="prompt-tag">中 &#x2192; 英</div>
        <div class="prompt-meanings">{{ currentMeaningsText }}</div>
        <div class="prompt-pos" v-if="currentPos">{{ currentPos }}.</div>
        <div class="prompt-hint" v-if="currentHintText">提示：{{ currentHintText }}</div>
      </div>

      <div class="input-area">
        <input
          ref="inputEl"
          v-model="userInput"
          :disabled="phase !== 'input'"
          class="recall-input"
          :class="resultClass"
          type="text"
          autocomplete="off"
          autocapitalize="none"
          spellcheck="false"
          placeholder="想一想这个词怎么写..."
          @keydown.enter.prevent="phase === 'input' ? confirm() : nextWord()"
        />
        <div class="feedback" :class="resultClass" v-if="phase === 'feedback'">
          <template v-if="lastResult === 'correct'">
            <span class="feedback-icon">&#x2705;</span>
            <span class="feedback-text">正确！</span>
          </template>
          <template v-else-if="lastResult === 'typo'">
            <span class="feedback-icon">&#x26A0;&#xFE0F;</span>
            <span class="feedback-text">差一个字母：正确是 <b>{{ currentWord.word }}</b></span>
          </template>
          <template v-else>
            <span class="feedback-icon">&#x274C;</span>
            <span class="feedback-text">正确答案：<b>{{ currentWord.word }}</b></span>
          </template>
        </div>
      </div>

      <div class="action-row" v-if="phase === 'input'">
        <button class="ghost-btn" :disabled="hintLevel >= 2" @click="useHint">
          &#x1F4A1; 提示 <span v-if="hintLevel > 0" class="hint-badge">已用 {{ hintLevel }} 次</span>
        </button>
        <button class="primary-btn" :disabled="!userInput.trim()" @click="confirm">
          确认
        </button>
      </div>
      <div class="action-row" v-else>
        <span class="next-tip">{{ remainingMs > 0 ? `${Math.ceil(remainingMs/1000)}s 后自动进入下一词` : '' }}</span>
        <button class="primary-btn" @click="nextWord">下一词 &#x1F449;</button>
      </div>
    </div>

    <!-- 会话总结 -->
    <div v-else-if="sessionDone" class="summary-card">
      <div class="summary-emoji">&#x1F389;</div>
      <h2>主动召回完成</h2>
      <div class="summary-stats">
        <div class="summary-stat">
          <span class="summary-value">{{ correctCount }}</span>
          <span class="summary-label">完全正确</span>
        </div>
        <div class="summary-stat">
          <span class="summary-value">{{ typoCount }}</span>
          <span class="summary-label">仅差一字母</span>
        </div>
        <div class="summary-stat">
          <span class="summary-value">{{ accuracy }}%</span>
          <span class="summary-label">正确率</span>
        </div>
        <div class="summary-stat">
          <span class="summary-value">{{ formatTime(elapsedMs) }}</span>
          <span class="summary-label">用时</span>
        </div>
      </div>

      <div class="summary-hardest" v-if="hardestWords.length">
        <div class="summary-section-title">&#x1F525; 最难召回的词</div>
        <div class="hardest-list">
          <div v-for="r in hardestWords" :key="r.wordId" class="hardest-item">
            <span class="hardest-word">{{ r.word }}</span>
            <span class="hardest-meaning">{{ r.meaning }}</span>
            <span class="hardest-grade">grade {{ r.grade.toFixed(1) }}</span>
          </div>
        </div>
      </div>

      <div class="summary-actions">
        <el-button @click="restart" :disabled="!hardestWords.length">错词重练</el-button>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReverseRecallCandidates, submitReverseRecall } from '../api/study'
import { classifySpelling } from '../utils/string'
import { useAgentStore } from '../stores/agent'
import { formatTime } from '../utils/formatTime'

const router = useRouter()
const agentStore = useAgentStore()
const rootEl = ref(null)
const inputEl = ref(null)

const loading = ref(false)
const words = ref([])           // candidate list (ReverseRecallCandidate[])
const currentIndex = ref(0)
const phase = ref('input')      // input | feedback
const userInput = ref('')
const hintLevel = ref(0)
const startTime = ref(Date.now())
const sessionStart = ref(Date.now())
const elapsedMs = ref(0)
const sessionDone = ref(false)
const lastResult = ref(null)    // 'correct' | 'typo' | 'wrong'
const sessionResults = ref([])  // { wordId, word, meaning, grade, result, reactionMs }
const remainingMs = ref(0)

let advanceTimer = null
let countdownTimer = null
const pendingTimeouts = []

function safeSetTimeout(fn, ms) {
  const id = setTimeout(() => {
    const idx = pendingTimeouts.indexOf(id)
    if (idx !== -1) pendingTimeouts.splice(idx, 1)
    fn()
  }, ms)
  pendingTimeouts.push(id)
  return id
}

function clearAllTimers() {
  if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  for (const id of pendingTimeouts) clearTimeout(id)
  pendingTimeouts.length = 0
}

// --- Computed ---

const currentWord = computed(() => words.value[currentIndex.value] || null)

const currentMeaningsText = computed(() => {
  const w = currentWord.value
  if (!w || !w.meanings || !w.meanings.length) return '（未知释义）'
  return w.meanings.map(m => m.meaning).filter(Boolean).join('; ')
})

const currentPos = computed(() => {
  const w = currentWord.value
  if (!w || !w.meanings || !w.meanings.length) return ''
  return w.meanings[0]?.partOfSpeech || ''
})

const currentHintText = computed(() => {
  const w = currentWord.value
  if (!w || hintLevel.value === 0) return ''
  const target = w.word || ''
  if (hintLevel.value === 1) {
    return target.charAt(0) + '_'.repeat(Math.max(target.length - 1, 0))
  }
  // hintLevel >= 2: first half revealed
  const half = Math.ceil(target.length / 2)
  return target.slice(0, half) + '_'.repeat(Math.max(target.length - half, 0))
})

const progressPercent = computed(() => {
  if (!words.value.length) return 0
  return Math.round(((currentIndex.value) / words.value.length) * 100)
})

const resultClass = computed(() => {
  if (phase.value !== 'feedback') return ''
  return lastResult.value || ''
})

const correctCount = computed(() => sessionResults.value.filter(r => r.result === 'correct').length)
const typoCount = computed(() => sessionResults.value.filter(r => r.result === 'typo').length)
const accuracy = computed(() => {
  const n = sessionResults.value.length
  if (!n) return 0
  return Math.round(correctCount.value * 100 / n)
})

const hardestWords = computed(() => {
  return [...sessionResults.value]
    .filter(r => r.grade < 3.5)
    .sort((a, b) => a.grade - b.grade)
    .slice(0, 3)
})

// --- Lifecycle ---

async function loadCandidates() {
  loading.value = true
  try {
    const res = await getReverseRecallCandidates(10)
    words.value = res.data || []
    currentIndex.value = 0
    phase.value = 'input'
    sessionStart.value = Date.now()
    startTime.value = Date.now()
    await nextTick()
    focusInput()
  } catch {
    ElMessage.warning('候选词加载失败，请稍后再试')
    words.value = []
  } finally {
    loading.value = false
  }
}

function focusInput() {
  if (inputEl.value) {
    try { inputEl.value.focus() } catch { /* ignore */ }
  }
}

// --- Actions ---

function useHint() {
  if (hintLevel.value < 2) {
    hintLevel.value += 1
  }
}

async function confirm() {
  if (phase.value !== 'input') return
  const word = currentWord.value
  if (!word) return
  const trimmed = userInput.value.trim()
  const classification = classifySpelling(trimmed, word.word)
  lastResult.value = classification
  const reactionMs = Date.now() - startTime.value
  phase.value = 'feedback'

  // Submit to backend (the source of truth for grade)
  let grade = classification === 'correct'
    ? (hintLevel.value === 0 ? 4.0 : hintLevel.value === 1 ? 3.5 : 3.0)
    : classification === 'typo' ? 2.5 : 1.0

  try {
    const res = await submitReverseRecall({
      wordId: word.wordId,
      userInput: trimmed,
      reactionTimeMs: reactionMs,
      hintLevel: hintLevel.value
    })
    if (res?.data?.grade != null) grade = res.data.grade
  } catch {
    ElMessage.warning('答题结果保存失败，请检查网络')
  }

  sessionResults.value.push({
    wordId: word.wordId,
    word: word.word,
    meaning: (word.meanings && word.meanings[0]?.meaning) || '',
    grade,
    result: classification,
    reactionMs
  })

  // Auto-advance after 1.2 s
  startAutoAdvance(1200)
}

function startAutoAdvance(ms) {
  remainingMs.value = ms
  if (countdownTimer) clearInterval(countdownTimer)
  const t0 = Date.now()
  countdownTimer = setInterval(() => {
    const passed = Date.now() - t0
    remainingMs.value = Math.max(0, ms - passed)
    if (remainingMs.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 100)
  advanceTimer = safeSetTimeout(() => {
    nextWord()
  }, ms)
}

function nextWord() {
  clearAllTimers()
  remainingMs.value = 0
  if (currentIndex.value < words.value.length - 1) {
    currentIndex.value += 1
    userInput.value = ''
    hintLevel.value = 0
    phase.value = 'input'
    startTime.value = Date.now()
    nextTick(focusInput)
  } else {
    endSession()
  }
}

function endSession() {
  elapsedMs.value = Date.now() - sessionStart.value
  sessionDone.value = true
  // Light TiMo feedback
  if (accuracy.value >= 80) {
    agentStore.setTiMoState && agentStore.setTiMoState('success', 2500)
  }
}

function restart() {
  if (!hardestWords.value.length) return
  const ids = hardestWords.value.map(r => r.wordId)
  words.value = words.value.filter(w => ids.includes(w.wordId))
  currentIndex.value = 0
  userInput.value = ''
  hintLevel.value = 0
  phase.value = 'input'
  sessionResults.value = []
  sessionDone.value = false
  sessionStart.value = Date.now()
  startTime.value = Date.now()
  nextTick(focusInput)
}

function confirmExit() {
  if (sessionDone.value || sessionResults.value.length === 0) {
    router.push('/')
    return
  }
  ElMessageBox.confirm('当前进度不会保存，确定要退出吗？', '退出确认', {
    confirmButtonText: '退出',
    cancelButtonText: '继续',
    type: 'warning'
  }).then(() => router.push('/')).catch(() => {})
}

function handleKeydown(e) {
  if (e.target.tagName === 'INPUT') return
  if (phase.value === 'feedback' && (e.key === 'Enter' || e.code === 'Space')) {
    e.preventDefault()
    nextWord()
  }
}

onMounted(() => {
  agentStore.setCurrentPage && agentStore.setCurrentPage('reverseRecall')
  loadCandidates()
})

onBeforeUnmount(() => {
  clearAllTimers()
})
</script>

<style scoped>
.reverse-recall {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px;
  outline: none;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.progress-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.progress-left { flex-shrink: 0; }
.exit-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: var(--radius-full);
  background: transparent; border: 1px solid var(--color-border-base);
  color: var(--color-text-secondary); cursor: pointer;
  font-family: var(--font-family); font-size: 13px; font-weight: 700;
  transition: all 0.2s ease;
}
.exit-btn:hover { background: var(--color-bg-hover); color: var(--color-text-primary); }
.progress-bar { flex: 1; }
.progress-bar :deep(.el-progress-bar__outer) { border-radius: var(--radius-full); background: var(--color-border-lighter); }
.progress-text { font-size: 13px; font-weight: 800; color: var(--color-text-secondary); white-space: nowrap; }

.state-block {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 80px 24px; text-align: center;
}
.state-block.empty p { color: var(--color-text-secondary); font-weight: 600; max-width: 420px; }
.state-block h3 { font-size: 20px; font-weight: 800; color: var(--color-text-primary); }
.empty-icon { font-size: 48px; }

.loading-spinner {
  width: 36px; height: 36px;
  border: 4px solid var(--color-border-lighter); border-top-color: #7a9e7e;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.recall-card {
  background: #fff; border: 3px solid var(--color-border-lighter);
  border-radius: var(--radius-xl); padding: 28px;
  box-shadow: 0 6px 0 var(--color-border-lighter);
  display: flex; flex-direction: column; gap: 24px;
}

.recall-prompt { text-align: center; }
.prompt-tag {
  display: inline-block; padding: 4px 12px;
  font-size: 11px; font-weight: 800; letter-spacing: 0.1em;
  color: #fff;
  background: linear-gradient(135deg, #7a9e7e, #5d8366);
  border-radius: var(--radius-full); margin-bottom: 12px;
}
.prompt-meanings {
  font-size: 26px; font-weight: 800; color: var(--color-text-primary);
  line-height: 1.4; letter-spacing: -0.01em;
}
.prompt-pos { margin-top: 8px; color: var(--color-blue); font-size: 14px; font-weight: 700; }
.prompt-hint {
  margin-top: 14px;
  font-family: var(--font-mono);
  letter-spacing: 0.15em;
  font-size: 18px; font-weight: 800;
  color: #5d8366;
}

.input-area { display: flex; flex-direction: column; gap: 12px; }
.recall-input {
  width: 100%; box-sizing: border-box;
  padding: 14px 18px;
  font-size: 22px; font-weight: 700; font-family: var(--font-mono);
  border: 3px solid var(--color-border-base);
  border-radius: var(--radius-lg);
  background: #fff;
  color: var(--color-text-primary);
  text-align: center;
  letter-spacing: 0.04em;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
  outline: none;
}
.recall-input:focus { border-color: #7a9e7e; box-shadow: 0 0 0 4px rgba(122, 158, 126, 0.18); }
.recall-input.correct { border-color: var(--color-primary); background: var(--color-primary-bg); color: var(--color-primary-dark); }
.recall-input.typo { border-color: #f59e0b; background: #FFF8E1; color: #C2410C; }
.recall-input.wrong { border-color: var(--color-red); background: var(--color-red-light); color: var(--color-red-dark); }

.feedback {
  display: flex; align-items: center; gap: 8px; justify-content: center;
  padding: 10px 16px; border-radius: var(--radius-lg);
  font-size: 15px; font-weight: 700;
  animation: fadeSlide 0.2s ease-out;
}
.feedback.correct { color: var(--color-primary-dark); background: var(--color-primary-bg); }
.feedback.typo { color: #C2410C; background: #FFF8E1; }
.feedback.wrong { color: var(--color-red-dark); background: var(--color-red-light); }
.feedback-icon { font-size: 18px; }
.feedback-text b { font-family: var(--font-mono); }

@keyframes fadeSlide { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }

.action-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.primary-btn, .ghost-btn {
  padding: 12px 22px; border-radius: var(--radius-lg);
  font-family: var(--font-family); font-size: 15px; font-weight: 800;
  cursor: pointer; transition: all 0.15s ease;
  display: inline-flex; align-items: center; gap: 6px;
}
.primary-btn {
  background: #7a9e7e; border: 3px solid #5d8366; color: #fff;
  box-shadow: 0 4px 0 #4f6f54;
}
.primary-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.primary-btn:not(:disabled):active { transform: translateY(3px); box-shadow: 0 1px 0 #4f6f54; }
.ghost-btn {
  background: #fff; border: 2px solid var(--color-border-base);
  color: var(--color-text-primary); box-shadow: 0 3px 0 var(--color-border-base);
}
.ghost-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ghost-btn:not(:disabled):active { transform: translateY(2px); box-shadow: 0 1px 0 var(--color-border-base); }
.hint-badge {
  margin-left: 4px; padding: 2px 6px; border-radius: var(--radius-full);
  background: var(--color-primary-bg); color: var(--color-primary-dark);
  font-size: 11px; font-weight: 700;
}
.next-tip { font-size: 12px; color: var(--color-text-muted); font-weight: 600; }

.summary-card {
  background: #fff; border: 3px solid #7a9e7e;
  border-radius: var(--radius-xl); padding: 32px;
  box-shadow: 0 6px 0 #5d8366;
  text-align: center;
}
.summary-emoji { font-size: 48px; margin-bottom: 8px; }
.summary-card h2 { font-size: 24px; font-weight: 900; margin-bottom: 24px; color: var(--color-text-primary); }
.summary-stats {
  display: flex; justify-content: center; gap: 24px; flex-wrap: wrap; margin-bottom: 20px;
}
.summary-stat { display: flex; flex-direction: column; align-items: center; }
.summary-value { font-size: 26px; font-weight: 900; font-family: var(--font-mono); color: #5d8366; line-height: 1.1; }
.summary-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); margin-top: 4px; }

.summary-hardest {
  margin-top: 16px; padding: 16px;
  background: var(--color-bg-page); border-radius: var(--radius-md);
  text-align: left;
}
.summary-section-title {
  font-size: 13px; font-weight: 800; color: var(--color-text-primary); margin-bottom: 10px;
}
.hardest-list { display: flex; flex-direction: column; gap: 6px; }
.hardest-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px; background: #fff; border-radius: var(--radius-sm);
}
.hardest-word { font-weight: 800; color: var(--color-text-primary); min-width: 100px; font-family: var(--font-mono); }
.hardest-meaning { font-size: 13px; color: var(--color-text-secondary); flex: 1; }
.hardest-grade { font-size: 11px; color: var(--color-red-dark); font-weight: 700; font-family: var(--font-mono); }

.summary-actions { margin-top: 20px; display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
</style>
