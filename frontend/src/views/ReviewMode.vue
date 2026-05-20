<template>
  <div class="review-mode" tabindex="0" @keydown="handleKeydown">
    <!-- 离线提醒横幅 -->
    <transition name="fade-slide">
      <div v-if="!isOnline" class="offline-banner">
        <span class="offline-banner-icon">&#x1F4F5;</span>
        <span class="offline-banner-text">网络已断开，复习进度将暂存本地</span>
      </div>
    </transition>

    <!-- 疲劳提醒横幅 -->
    <transition name="fade-slide">
      <div v-if="showFatigueReminder" class="fatigue-banner" @click="dismissFatigue">
        <span class="fatigue-icon">&#x2615;</span>
        <span class="fatigue-text">你已经学习了 {{ fatigueMinutes }} 分钟，休息一下吧</span>
        <span class="fatigue-close">&#x2715;</span>
      </div>
    </transition>

    <!-- 顶部进度 -->
    <div class="progress-header">
      <el-progress :percentage="progressPercent" :stroke-width="12" :show-text="false" color="#CE82FF" />
      <div class="progress-info">
        <span class="progress-text">待复习：{{ total }} 个（顽固 {{ stubbornCount }} 个）</span>
        <div class="step-indicator">
          <span class="step-dot" :class="{ active: step === 1 }">1</span>
          <span class="step-line"></span>
          <span class="step-dot" :class="{ active: step === 2 }">2</span>
          <span class="step-line"></span>
          <span class="step-dot" :class="{ active: step === 3 || step === 'spelling' }">3</span>
        </div>
        <div class="timer" :class="{ urgent: countdown <= 1 }">
          <span class="timer-num">{{ countdown }}</span>
          <span class="timer-unit">s</span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!words.length && !loading" class="empty-state">
      <div class="empty-icon">&#x1F504;</div>
      <h3>暂无待复习单词</h3>
      <p>继续学习新词吧</p>
      <el-button type="primary" @click="$router.push('/word-select')">去选词 &#x1F680;</el-button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="empty-state">
      <div class="loading-spinner"></div>
      <p>加载复习队列...</p>
    </div>

    <!-- 复习中 -->
    <div v-if="currentWord && !sessionDone" class="review-area">
      <!-- 顽固词标记 -->
      <div v-if="currentWord.stubborn" class="stubborn-badge">&#x1F525; 顽固词</div>

      <!-- Step 1: 闪电判断 — 认识/不认识（3秒） -->
      <div v-if="step === 1" class="step-content">
        <div class="review-card">
          <div class="word-display">{{ currentWord.word }}</div>
        </div>
        <p class="step-label">&#x26A1; 你认识这个单词吗？</p>
        <div class="action-bar">
          <button
            class="action-btn btn-know"
            :class="{ selected: step1Selected === 'know' }"
            :disabled="step1Selected !== null"
            @click="step1Select('know')"
          >
            <span class="btn-emoji">&#x2705;</span> 认识
          </button>
          <button
            class="action-btn btn-unknown"
            :class="{ selected: step1Selected === 'unknown' }"
            :disabled="step1Selected !== null"
            @click="step1Select('unknown')"
          >
            <span class="btn-emoji">&#x274C;</span> 不认识
          </button>
        </div>
      </div>

      <!-- Step 2: 语境选义（5秒四选一） -->
      <div v-if="step === 2" class="step-content">
        <div class="review-card sentence-card">
          <p class="sentence-display" v-html="step2SentenceHtml"></p>
        </div>
        <p class="step-label">选择正确的释义</p>
        <div class="meaning-options">
          <button
            v-for="(opt, idx) in meaningOptions" :key="idx"
            class="meaning-btn"
            :class="{
              selected: selectedMeaning === idx,
              correct: selectedMeaning !== null && idx === correctMeaningIndex,
              wrong: selectedMeaning === idx && idx !== correctMeaningIndex
            }"
            :disabled="selectedMeaning !== null"
            @click="selectMeaning(idx)"
          >
            {{ opt }}
          </button>
        </div>
      </div>

      <!-- Step 3: 词形辨析 — 中文释义 + 多拼写变体选择 -->
      <div v-if="step === 3" class="step-content">
        <div class="review-card large">
          <div class="word-prompt meaning-prompt">{{ currentMeaningDisplay }}</div>
        </div>
        <div class="spelling-options">
          <button
            v-for="(opt, idx) in spellingOptions" :key="idx"
            class="spelling-btn"
            :class="{
              selected: step3Selected === idx,
              correct: step3Selected !== null && idx === step3CorrectIdx,
              wrong: step3Selected === idx && idx !== step3CorrectIdx
            }"
            :disabled="step3Selected !== null"
            @click="step3Select(idx)"
          >
            {{ opt }}
          </button>
        </div>
      </div>

      <!-- Extra: 拼写补强 — 中文释义 + 无提示输入 -->
      <div v-if="step === 'spelling'" class="step-content">
        <div class="review-card">
          <div class="word-prompt meaning-prompt">{{ currentMeaningDisplay }}</div>
        </div>
        <el-input ref="spellingInputRef" v-model="spellingInput" size="large"
          placeholder="输入单词..." class="spelling-input" @keyup.enter="submitSpelling" />
        <div class="action-bar">
          <button class="action-btn btn-yes" @click="submitSpelling">
            <span class="btn-emoji">&#x2705;</span> 确认
          </button>
        </div>
      </div>
    </div>

    <!-- 学习完成 -->
    <div v-if="sessionDone" class="session-done">
      <div class="done-card">
        <div class="done-emoji">&#x1F3C6;</div>
        <h2>复习完成！</h2>
        <div class="done-stats">
          <div class="done-stat">
            <span class="done-stat-value purple">{{ total }}</span>
            <span class="done-stat-label">总数</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value green">{{ passed }}</span>
            <span class="done-stat-label">通过</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value orange">{{ spellingCount }}</span>
            <span class="done-stat-label">需拼写</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value blue">{{ formatTime(elapsedMs) }}</span>
            <span class="done-stat-label">用时</span>
          </div>
        </div>

        <!-- TiMo 复习诊断 -->
        <div v-if="reviewDiagnosis" class="timo-diagnosis">
          <div class="diagnosis-header">
            <span class="diagnosis-icon">&#x1F9E0;</span>
            <span class="diagnosis-title">TiMo 复习诊断</span>
          </div>
          <div class="diagnosis-content">
            <div class="diagnosis-item" v-if="reviewDiagnosis.weakness">
              <span class="diagnosis-label">&#x1F525; 薄弱环节</span>
              <span class="diagnosis-text">{{ reviewDiagnosis.weakness }}</span>
            </div>
            <div class="diagnosis-item" v-if="reviewDiagnosis.stubborn">
              <span class="diagnosis-label">&#x1F525; 顽固词警告</span>
              <span class="diagnosis-text">{{ reviewDiagnosis.stubborn }}</span>
            </div>
            <div class="diagnosis-item" v-if="reviewDiagnosis.suggestion">
              <span class="diagnosis-label">&#x1F4A1; 学习建议</span>
              <span class="diagnosis-text">{{ reviewDiagnosis.suggestion }}</span>
            </div>
          </div>
        </div>

        <div class="done-actions">
          <el-button type="primary" @click="$router.push('/stats')">查看统计 &#x1F4CA;</el-button>
          <el-button @click="$router.push('/')">返回首页 &#x1F3E0;</el-button>
        </div>
      </div>
    </div>

    <!-- TiMo FAB -->
    <TiMoFAB />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getReviewQueue, submitReviewResult } from '../api/review'
import { getStubbornWords } from '../api/agent'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import { useNetwork } from '../composables/useNetwork'
import { useFatigueCheck } from '../composables/useFatigueCheck'
import { shuffle } from '../utils/shuffle'
import { formatTime } from '../utils/formatTime'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const agentStore = useAgentStore()
const userStore = useUserStore()
const { isOnline } = useNetwork()

// 组件存活标记，防止卸载后回调更新 DOM
let alive = true
// 统一管理所有定时器
let mainTimer = null
const pendingTimeouts = []

function safeSetTimeout(fn, ms) {
  const id = setTimeout(() => {
    const idx = pendingTimeouts.indexOf(id)
    if (idx !== -1) pendingTimeouts.splice(idx, 1)
    if (alive) fn()
  }, ms)
  pendingTimeouts.push(id)
  return id
}

function clearAllTimers() {
  if (mainTimer) { clearInterval(mainTimer); mainTimer = null }
  for (const id of pendingTimeouts) clearTimeout(id)
  pendingTimeouts.length = 0
}

const loading = ref(false)
const words = ref([])
const currentIndex = ref(0)
const step = ref(0)
const countdown = ref(3)
const sessionDone = ref(false)

// 疲劳提醒
const sessionStartTime = ref(Date.now())
const { fatigueMinutes, showFatigueReminder, startFatigueCheck, dismissFatigue } = useFatigueCheck(sessionDone, sessionStartTime)
const startTime = ref(0)
const elapsedMs = ref(0)
const completed = ref(0)
const passed = ref(0)
const spellingCount = ref(0)
const stubbornCount = ref(0)
const stepResults = ref({ s1: 0, s2: 0, s3: 0 })

// Step 1: 闪电判断 — 认识/不认识
const step1Selected = ref(null)

// Step 2: 语境选义
const step2SentenceHtml = ref('')
const meaningOptions = ref([])
const selectedMeaning = ref(null)
const correctMeaningIndex = ref(0)

// Step 3: 词形辨析
const currentMeaningDisplay = ref('')
const spellingOptions = ref([])
const step3Selected = ref(null)
const step3CorrectIdx = ref(0)

// Extra: 拼写补强
const spellingInput = ref('')
const spellingInputRef = ref(null)

// Per-step timing
const stepStartTime = ref(0)
const reactionTimes = ref({ s1: 0, s2: 0, s3: 0 })

const total = computed(() => words.value.length)
const progressPercent = computed(() => total.value > 0 ? Math.round((completed.value / total.value) * 100) : 0)
const currentWord = computed(() => words.value[currentIndex.value] || null)
const reviewDiagnosis = ref(null)

function startTimer(seconds) {
  if (mainTimer) { clearInterval(mainTimer); mainTimer = null }
  countdown.value = seconds
  const t0 = Date.now()
  mainTimer = setInterval(() => {
    const elapsed = Date.now() - t0
    countdown.value = Math.max(0, seconds - Math.floor(elapsed / 1000))
    if (countdown.value <= 0) {
      clearInterval(mainTimer)
      mainTimer = null
      onTimeout()
    }
  }, 100)
}

function onTimeout() {
  if (step.value === 1) step1Select('unknown')
  else if (step.value === 2) autoSelectMeaning(-1)
  else if (step.value === 3) step3Select(-1)
  else if (step.value === 'spelling') submitSpelling()
}

// ===== 键盘快捷键 =====
function handleKeydown(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  // Step 1: Space/Enter → 跳过计时器直接进入 step2
  if (step.value === 1 && step1Selected.value === null && (e.code === 'Space' || e.key === 'Enter')) {
    e.preventDefault()
    step1Select('know')
  }
  // Step 2: 1-4 → 选择释义
  if (step.value === 2 && selectedMeaning.value === null) {
    const idx = parseInt(e.key) - 1
    if (idx >= 0 && idx < meaningOptions.value.length) {
      e.preventDefault()
      selectMeaning(idx)
    }
  }
  // Step 3: 1-4 → 选择拼写
  if (step.value === 3 && step3Selected.value === null) {
    const idx = parseInt(e.key) - 1
    if (idx >= 0 && idx < spellingOptions.value.length) {
      e.preventDefault()
      step3Select(idx)
    }
  }
}

// ===== Step 1: 闪电判断 — 认识/不认识 =====
function step1Select(answer) {
  clearInterval(mainTimer)
  mainTimer = null
  step1Selected.value = answer
  reactionTimes.value.s1 = Date.now() - stepStartTime.value
  stepResults.value.s1 = answer === 'know' ? 5 : 0

  safeSetTimeout(() => {
    buildMeaningDisplay()
    buildMeaningOptions()
    buildStep2Sentence()
    step.value = 2
    stepStartTime.value = Date.now()
    startTimer(5)
  }, 600)
}

// ===== Step 2: 语境选义（四选一） =====
function buildMeaningDisplay() {
  const meanings = currentWord.value.meanings || []
  if (meanings.length > 0) {
    const m = meanings[0]
    currentMeaningDisplay.value = m.partOfSpeech ? `${m.partOfSpeech}. ${m.meaning}` : m.meaning
  } else {
    currentMeaningDisplay.value = '未知释义'
  }
}

function buildMeaningOptions() {
  const meanings = currentWord.value.meanings || []
  const correct = meanings.length > 0 ? meanings[0].meaning : '未知释义'

  // 收集所有单词的全部释义作为干扰项池（包括当前单词的其他释义）
  const allDistractors = []
  for (const w of words.value) {
    if (w.meanings && w.meanings.length > 0) {
      for (const m of w.meanings) {
        if (m.meaning && m.meaning !== correct) {
          allDistractors.push(m.meaning)
        }
      }
    }
  }

  // 去重
  const uniqueDistractors = [...new Set(allDistractors)]

  // Fisher-Yates 洗牌
  for (let i = uniqueDistractors.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [uniqueDistractors[i], uniqueDistractors[j]] = [uniqueDistractors[j], uniqueDistractors[i]]
  }

  // 取前3个，不够则循环复用
  const distractors = []
  for (let i = 0; i < 3; i++) {
    if (uniqueDistractors.length > 0) {
      distractors.push(uniqueDistractors[i % uniqueDistractors.length])
    }
  }

  const options = [correct, ...distractors]
  shuffle(options)
  correctMeaningIndex.value = options.indexOf(correct)
  meaningOptions.value = options
  selectedMeaning.value = null
}

function buildStep2Sentence() {
  const word = currentWord.value.word
  const examples = currentWord.value.examples || []

  if (examples.length > 0) {
    const sentence = examples[0].sentence
    const regex = new RegExp(`(${word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
    step2SentenceHtml.value = sentence.replace(regex, '<span class="highlight-word">$1</span>')
  } else {
    const meaning = currentMeaningDisplay.value || 'something'
    step2SentenceHtml.value = `What does <span class="highlight-word">${word}</span> mean?`
  }
}

function selectMeaning(idx) {
  if (selectedMeaning.value !== null) return
  selectedMeaning.value = idx
  clearInterval(mainTimer)
  mainTimer = null
  reactionTimes.value.s2 = Date.now() - stepStartTime.value
  stepResults.value.s2 = idx === correctMeaningIndex.value ? 5 : 0

  safeSetTimeout(() => {
    buildSpellingOptions()
    step.value = 3
    stepStartTime.value = Date.now()
    startTimer(5)
  }, 600)
}

function autoSelectMeaning(idx) {
  selectedMeaning.value = idx
  reactionTimes.value.s2 = Date.now() - stepStartTime.value
  stepResults.value.s2 = 0

  safeSetTimeout(() => {
    buildSpellingOptions()
    step.value = 3
    stepStartTime.value = Date.now()
    startTimer(5)
  }, 600)
}

// ===== Step 3: 词形辨析 — 多拼写变体选择 =====
function buildSpellingOptions() {
  const word = currentWord.value.word
  const variants = generateSpellingVariants(word)
  shuffle(variants)
  step3CorrectIdx.value = variants.indexOf(word)
  spellingOptions.value = variants
  step3Selected.value = null
}

function generateSpellingVariants(word) {
  const variants = new Set([word])
  const chars = word.split('')

  const substitutions = {
    a: ['e', 'o'], e: ['a', 'i'], i: ['e', 'y'], o: ['a', 'u'], u: ['o', 'a'],
    c: ['k', 's'], s: ['c', 'z'], t: ['d', 'f'], r: ['l', 'n'], n: ['m', 'r'],
    b: ['d', 'p'], p: ['b', 'd'], d: ['b', 't'], f: ['t', 'ph'], g: ['j', 'c'],
    l: ['r', 'i'], m: ['n', 'nn'], w: ['v', 'wh']
  }

  for (let i = 0; i < chars.length && variants.size < 4; i++) {
    const lower = chars[i].toLowerCase()
    if (substitutions[lower]) {
      for (const sub of substitutions[lower]) {
        if (variants.size >= 4) break
        const newChars = [...chars]
        newChars[i] = chars[i] === chars[i].toUpperCase() ? sub.toUpperCase() : sub
        variants.add(newChars.join(''))
      }
    }
  }

  for (let i = 0; i < chars.length - 1 && variants.size < 4; i++) {
    const swapped = [...chars]
    ;[swapped[i], swapped[i + 1]] = [swapped[i + 1], swapped[i]]
    variants.add(swapped.join(''))
  }

  let extra = 1
  while (variants.size < 4) {
    const pos = extra % chars.length
    const fake = chars.map((c, i) => i === pos ? '_' : c).join('')
    if (!variants.has(fake)) variants.add(fake)
    extra++
  }

  return [...variants].slice(0, 4)
}

function step3Select(idx) {
  clearInterval(mainTimer)
  mainTimer = null
  step3Selected.value = idx
  reactionTimes.value.s3 = Date.now() - stepStartTime.value
  const correct = idx >= 0 && idx === step3CorrectIdx.value
  stepResults.value.s3 = correct ? 5 : 0

  safeSetTimeout(() => nextStep(), 600)
}

function nextStep() {
  const grade = 1.0 + 3.0 * (
    0.35 * stepResults.value.s1 / 5.0 +
    0.30 * stepResults.value.s2 / 5.0 +
    0.35 * stepResults.value.s3 / 5.0
  )

  const needsSpelling = grade < 2.5

  if (needsSpelling) {
    spellingCount.value++
    step.value = 'spelling'
    nextTick(() => {
      if (spellingInputRef.value) spellingInputRef.value.focus()
    })
    startTimer(10)
  } else {
    submitAndNext(false, false)
  }
}

// ===== Extra: 拼写补强 =====
function submitSpelling() {
  const input = spellingInput.value.trim().toLowerCase()
  const correct = input === currentWord.value.word.toLowerCase()
  submitAndNext(true, correct)
}

function submitAndNext(spellingAttempted, spellingCorrect) {
  const totalReactionMs = reactionTimes.value.s1 + reactionTimes.value.s2 + reactionTimes.value.s3

  submitReviewResult({
    wordId: currentWord.value.wordId,
    step1: stepResults.value.s1,
    step2: stepResults.value.s2,
    step3: stepResults.value.s3,
    reactionTimeMs: totalReactionMs,
    spellingAttempted,
    spellingCorrect
  }).catch(() => {
    ElMessage.warning('复习结果保存失败，请检查网络')
  })

  completed.value++
  if (stepResults.value.s1 === 5 && stepResults.value.s2 === 5 && stepResults.value.s3 === 5) {
    passed.value++
  }

  spellingInput.value = ''

  if (currentIndex.value < words.value.length - 1) {
    currentIndex.value++
    step.value = 1
    stepResults.value = { s1: 0, s2: 0, s3: 0 }
    reactionTimes.value = { s1: 0, s2: 0, s3: 0 }
    step1Selected.value = null
    stepStartTime.value = Date.now()
    startTimer(3)
  } else {
    elapsedMs.value = Date.now() - sessionStartTime.value
    sessionDone.value = true
    sessionStorage.removeItem(REVIEW_STORAGE_KEY)
    clearAllTimers()
    generateReviewDiagnosis()
  }
}

// TiMo 复习诊断
async function generateReviewDiagnosis() {
  try {
    const res = await getStubbornWords()
    const stubbornWords = res.data?.words || []
    const accuracy = total.value > 0 ? Math.round((passed.value / total.value) * 100) : 0

    let weakness = ''
    let stubborn = ''
    let suggestion = ''

    // 薄弱环节分析
    if (accuracy < 60) {
      weakness = '整体正确率较低，建议增加每日学习量并加强复习频率'
    } else if (accuracy < 80) {
      weakness = '部分单词掌握不牢固，建议重点关注拼写和语境理解'
    } else {
      weakness = '掌握情况良好，继续保持当前学习节奏'
    }

    // 顽固词警告
    if (stubbornWords.length > 5) {
      stubborn = `有 ${stubbornWords.length} 个顽固词，建议使用深度学习模式重点攻克`
    } else if (stubbornWords.length > 0) {
      stubborn = `发现 ${stubbornWords.length} 个顽固词，可通过拼写练习加强记忆`
    }

    // 学习建议
    if (spellingCount.value > total.value * 0.5) {
      suggestion = '拼写错误较多，建议使用快速记忆模式先熟悉词形，再进行复习'
    } else if (elapsedMs.value > 600000) {
      suggestion = '学习时间较长，建议适当休息避免疲劳影响记忆效果'
    } else {
      suggestion = '复习完成，建议明天继续巩固以强化长期记忆'
    }

    reviewDiagnosis.value = { weakness, stubborn, suggestion }
  } catch {
    // 诊断失败不影响页面
  }
}

// 进度持久化
const REVIEW_STORAGE_KEY = 'timo_review_progress'

function saveReviewProgress() {
  if (!words.value.length || sessionDone.value) {
    sessionStorage.removeItem(REVIEW_STORAGE_KEY)
    return
  }
  sessionStorage.setItem(REVIEW_STORAGE_KEY, JSON.stringify({
    wordIds: words.value.map(w => w.wordId),
    currentIndex: currentIndex.value,
    completed: completed.value,
    passed: passed.value,
    spellingCount: spellingCount.value
  }))
}

async function loadQueue() {
  loading.value = true
  try {
    const res = await getReviewQueue()
    words.value = res.data.words || []
    stubbornCount.value = res.data.stubbornCount || 0
    if (words.value.length > 0) {
      step.value = 1
      sessionStartTime.value = Date.now()
      stepStartTime.value = Date.now()
      step1Selected.value = null
      startTimer(3)
    }
  } catch {
    words.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  agentStore.setCurrentPage('review')
  if (userStore.token) {
    loadQueue()
  } else {
    loading.value = false
  }
  startFatigueCheck()
  window.addEventListener('beforeunload', saveReviewProgress)
})

onUnmounted(() => {
  alive = false
  clearAllTimers()
  window.removeEventListener('beforeunload', saveReviewProgress)
  saveReviewProgress()
})
</script>

<style scoped>
.review-mode { max-width: 640px; margin: 0 auto; padding: 20px; outline: none; }

.progress-header { margin-bottom: 24px; }
.progress-header :deep(.el-progress-bar__outer) { border-radius: var(--radius-full); background: var(--color-border-lighter); }

.progress-info {
  display: flex; align-items: center; justify-content: space-between; margin-top: 8px;
}
.progress-text { font-weight: 800; font-size: 14px; color: var(--color-text-secondary); }

.step-indicator { display: flex; align-items: center; gap: 6px; }
.step-dot {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 800;
  background: var(--color-border-lighter); color: var(--color-text-muted); transition: all 0.3s;
}
.step-dot.active {
  background: var(--color-purple); color: #fff; box-shadow: 0 2px 0 var(--color-purple-dark);
}
.step-line { width: 20px; height: 2px; background: var(--color-border-lighter); border-radius: 1px; }

.timer {
  display: flex; align-items: baseline; gap: 2px;
  padding: 4px 12px; border-radius: var(--radius-full);
  background: var(--color-purple-light); transition: all 0.3s;
}
.timer-num { font-size: 20px; font-weight: 900; font-family: var(--font-mono); color: var(--color-purple-dark); }
.timer-unit { font-size: 12px; font-weight: 700; color: var(--color-purple-dark); }
.timer.urgent { background: var(--color-red-light); }
.timer.urgent .timer-num, .timer.urgent .timer-unit { color: var(--color-red); animation: pulse 0.5s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }

.review-area { display: flex; flex-direction: column; align-items: center; }

.stubborn-badge {
  padding: 6px 16px; border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-red), var(--color-orange));
  color: #fff; font-weight: 800; font-size: 13px; margin-bottom: 16px;
  animation: pulse 1.5s ease-in-out infinite;
}

.review-card {
  width: 100%; min-height: 180px;
  border: 3px solid var(--color-purple); border-radius: var(--radius-xl);
  display: flex; align-items: center; justify-content: center;
  background: #FFFFFF; box-shadow: 0 6px 0 var(--color-purple-dark); padding: 32px;
}
.review-card.large { min-height: 200px; }
.review-card.sentence-card { min-height: 120px; flex-direction: column; padding: 24px 32px; }

.step-content { display: flex; flex-direction: column; align-items: center; gap: 16px; width: 100%; }

.word-display { font-size: 48px; font-weight: 900; color: var(--color-text-primary); }
.word-prompt { font-size: 18px; font-weight: 800; color: var(--color-text-primary); }
.meaning-prompt { font-size: 24px; color: var(--color-text-primary); }

.sentence-display {
  font-size: 20px; font-weight: 700; color: var(--color-text-primary);
  line-height: 1.6; text-align: center; margin: 0;
}
.highlight-word {
  color: var(--color-primary-dark); font-weight: 900;
  text-decoration: underline; text-decoration-color: var(--color-primary);
  text-underline-offset: 4px;
}

.step-label { font-size: 16px; color: var(--color-text-secondary); font-weight: 700; }

.meaning-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; max-width: 400px; }

.meaning-btn {
  text-align: center; padding: 10px 12px; width: 100%; height: 64px;
  border: 3px solid var(--color-border-lighter); border-radius: var(--radius-lg);
  background: #FFFFFF; font-size: 14px; font-weight: 700; font-family: var(--font-family);
  cursor: pointer; transition: all 0.2s ease;
  box-shadow: 0 3px 0 var(--color-border-lighter); color: var(--color-text-primary);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meaning-btn:hover:not(:disabled) { border-color: var(--color-purple); background: var(--color-purple-light); }
.meaning-btn:active:not(:disabled) { transform: translateY(2px); box-shadow: 0 1px 0 var(--color-border-lighter); }
.meaning-btn.selected.correct {
  border-color: var(--color-primary); background: var(--color-primary-bg);
  color: var(--color-primary-dark); box-shadow: 0 3px 0 var(--color-primary-dark);
}
.meaning-btn.selected.wrong {
  border-color: var(--color-red); background: var(--color-red-light);
  color: var(--color-red-dark); box-shadow: 0 3px 0 var(--color-red-dark);
}
.meaning-btn:disabled { opacity: 0.6; cursor: default; }

/* Step 3: 拼写变体选择 */
.spelling-options {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; max-width: 400px;
}
.spelling-btn {
  padding: 10px 12px; text-align: center; width: 100%; height: 64px;
  border: 3px solid var(--color-border-lighter); border-radius: var(--radius-lg);
  background: #FFFFFF; font-size: 18px; font-weight: 800; font-family: var(--font-mono);
  cursor: pointer; transition: all 0.2s ease;
  box-shadow: 0 3px 0 var(--color-border-lighter); color: var(--color-text-primary);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.spelling-btn:hover:not(:disabled) { border-color: var(--color-purple); background: var(--color-purple-light); }
.spelling-btn:active:not(:disabled) { transform: translateY(2px); box-shadow: 0 1px 0 var(--color-border-lighter); }
.spelling-btn.selected.correct {
  border-color: var(--color-primary); background: var(--color-primary-bg);
  color: var(--color-primary-dark); box-shadow: 0 3px 0 var(--color-primary-dark);
}
.spelling-btn.selected.wrong {
  border-color: var(--color-red); background: var(--color-red-light);
  color: var(--color-red-dark); box-shadow: 0 3px 0 var(--color-red-dark);
  text-decoration: line-through;
}
.spelling-btn:disabled { opacity: 0.6; cursor: default; }

.spelling-input { max-width: 300px; }

.action-bar { display: flex; gap: 16px; margin-top: 8px; width: 100%; justify-content: center; }

.action-btn {
  flex: 1; max-width: 200px; padding: 16px 24px;
  border-radius: var(--radius-lg); border: 3px solid;
  font-size: 16px; font-weight: 800; font-family: var(--font-family);
  cursor: pointer; transition: all 0.15s ease;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}
.action-btn:active { transform: translateY(3px); }

.btn-yes {
  background: var(--color-primary); border-color: var(--color-primary-dark);
  color: #fff; box-shadow: 0 4px 0 var(--color-primary-darker);
}
.btn-yes:active { box-shadow: 0 1px 0 var(--color-primary-darker); }

.btn-know {
  background: var(--color-primary); border-color: var(--color-primary-dark);
  color: #fff; box-shadow: 0 4px 0 var(--color-primary-darker);
}
.btn-know:active { box-shadow: 0 1px 0 var(--color-primary-darker); }
.btn-know.selected { opacity: 0.8; }

.btn-unknown {
  background: var(--color-red); border-color: var(--color-red-dark);
  color: #fff; box-shadow: 0 4px 0 var(--color-red-darker, var(--color-red-dark));
}
.btn-unknown:active { box-shadow: 0 1px 0 var(--color-red-darker, var(--color-red-dark)); }
.btn-unknown.selected { opacity: 0.8; }

.btn-emoji { font-size: 18px; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 0;
}
.empty-icon { font-size: 48px; }
.empty-state h3 { font-size: 20px; font-weight: 800; color: var(--color-text-primary); }
.empty-state p { color: var(--color-text-secondary); font-weight: 600; }

.loading-spinner {
  width: 40px; height: 40px;
  border: 4px solid var(--color-border-lighter); border-top-color: var(--color-purple);
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.session-done { padding-top: 20px; }
.done-card {
  background: #FFFFFF; border: 3px solid var(--color-purple);
  border-radius: var(--radius-xl); padding: 32px; text-align: center;
  box-shadow: 0 6px 0 var(--color-purple-dark);
}
.done-emoji { font-size: 48px; margin-bottom: 8px; }
.done-card h2 { font-size: 24px; font-weight: 900; color: var(--color-text-primary); margin-bottom: 20px; }

.done-stats { display: flex; justify-content: center; gap: 24px; margin-bottom: 20px; }
.done-stat { display: flex; flex-direction: column; align-items: center; }
.done-stat-value { font-size: 28px; font-weight: 900; font-family: var(--font-mono); line-height: 1.1; }
.done-stat-value.green { color: var(--color-primary-dark); }
.done-stat-value.blue { color: var(--color-blue-dark); }
.done-stat-value.orange { color: var(--color-orange-dark); }
.done-stat-value.purple { color: var(--color-purple-dark); }
.done-stat-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); margin-top: 4px; }
.done-actions { display: flex; gap: 12px; justify-content: center; }

/* 离线横幅 */
.offline-banner {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 16px; margin-bottom: 12px; border-radius: var(--radius-lg);
  background: var(--color-red-light); border: 2px solid var(--color-red);
}
.offline-banner-icon { font-size: 18px; }
.offline-banner-text { font-size: 14px; font-weight: 700; color: var(--color-red-dark); }

.fatigue-banner {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 16px; margin-bottom: 12px; border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #FFF3E0, #FFE0B2);
  border: 2px solid #FFB74D; cursor: pointer; transition: all 0.3s ease;
}
.fatigue-banner:hover { background: linear-gradient(135deg, #FFE0B2, #FFCC80); }
.fatigue-icon { font-size: 18px; }
.fatigue-text { font-size: 14px; font-weight: 700; color: #E65100; }
.fatigue-close { font-size: 14px; color: #BF360C; margin-left: 8px; font-weight: 800; }

.fade-slide-enter-active, .fade-slide-leave-active { transition: all 0.3s ease; }
.fade-slide-enter-from, .fade-slide-leave-to { opacity: 0; transform: translateY(-10px); }

/* TiMo 复习诊断 */
.timo-diagnosis {
  margin: 20px 0; padding: 16px;
  background: linear-gradient(135deg, #E3F2FD, #BBDEFB);
  border: 2px solid #2196F3; border-radius: var(--radius-lg);
  text-align: left;
}
.diagnosis-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 12px;
}
.diagnosis-icon { font-size: 20px; }
.diagnosis-title { font-size: 14px; font-weight: 800; color: #1565C0; }
.diagnosis-content { display: flex; flex-direction: column; gap: 10px; }
.diagnosis-item { display: flex; flex-direction: column; gap: 4px; }
.diagnosis-label { font-size: 12px; font-weight: 800; color: #1976D2; }
.diagnosis-text { font-size: 13px; font-weight: 600; color: #333; line-height: 1.5; }
</style>
