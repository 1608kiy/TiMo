<template>
  <div class="quick-memory" tabindex="0" @keydown="handleKeydown" ref="rootEl">
    <!-- 离线提醒横幅 -->
    <transition name="fade-slide">
      <div v-if="!isOnline" class="offline-banner">
        <span class="offline-banner-icon">&#x1F4F5;</span>
        <span class="offline-banner-text">网络已断开，学习进度将暂存本地</span>
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

    <!-- 顶部进度条 -->
    <div class="progress-header">
      <el-progress :percentage="progressPercent" :stroke-width="12" :show-text="false" color="#58CC02" />
      <div class="progress-info">
        <span class="progress-text">{{ currentIndex + 1 }} / {{ currentQueueLength }}</span>
        <div v-if="phase === 'verify'" class="timer" :class="timerClass">
          <div class="timer-bar" :style="{ width: timerPercent + '%' }"></div>
          <span class="timer-num">{{ countdown }}</span>
          <span class="timer-unit">s</span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!words.length && !loading" class="empty-state">
      <div class="empty-icon">&#x1F4D6;</div>
      <h3>还没有选择单词</h3>
      <p>请从选词入口选择单词开始学习</p>
      <el-button type="primary" @click="$router.push('/word-select')">去选词 &#x1F680;</el-button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="empty-state">
      <div class="loading-spinner"></div>
      <p>加载单词中...</p>
    </div>

    <!-- 学习中 -->
    <div v-if="currentWord && !sessionDone" class="card-area">
      <!-- 单词卡片 -->
      <div class="word-card">
        <div class="card-front">
          <div class="word-main">{{ currentWord.word }}</div>
          <div class="word-phonetic" v-if="currentWord.phonetic">{{ currentWord.phonetic }}</div>
          <button class="speak-btn" @click.stop="speakWord" title="点击发音">
            <span>&#x1F50A;</span>
          </button>
        </div>
      </div>

      <!-- "不认识"后展开释义 -->
      <transition name="fade-slide">
        <div v-if="showMeaningsPanel" class="meanings-expanded">
          <div class="meanings-list">
            <div v-for="(m, i) in currentWord.meanings" :key="m.id" class="meaning-item" :class="{ primary: i === 0 }">
              <span class="pos-tag" v-if="m.partOfSpeech">{{ m.partOfSpeech }}.</span>
              {{ m.meaning }}
            </div>
          </div>
          <div class="examples-section" v-if="currentWord.examples && currentWord.examples.length">
            <div class="examples-title">&#x1F4D6; 例句</div>
            <div v-for="(ex, i) in currentWord.examples.slice(0, 2)" :key="i" class="example-item">
              <div class="example-en">{{ ex.sentence }}</div>
              <div class="example-cn" v-if="ex.translation">{{ ex.translation }}</div>
            </div>
          </div>
          <div class="tip-section" v-if="currentWord.examTip">
            <span class="tip-label">&#x1F4A1; 助记</span>
            {{ currentWord.examTip }}
          </div>
        </div>
      </transition>

      <!-- 阶段1: 认识/不认识 -->
      <div v-if="phase === 'decide'" class="action-bar">
        <button class="action-btn btn-no" @click="decide(false)" :disabled="!isOnline">
          <span class="btn-emoji">&#x274C;</span> 不认识
        </button>
        <button class="action-btn btn-yes" @click="decide(true)" :disabled="!isOnline">
          <span class="btn-emoji">&#x2705;</span> 认识
        </button>
      </div>

      <!-- 阶段2: 闪电验证 — 四选一释义（3 秒倒计时） -->
      <div v-if="phase === 'verify'" class="action-bar verify-bar">
        <div class="verify-timer-bar">
          <div class="verify-timer-fill" :class="timerClass" :style="{ width: timerPercent + '%' }"></div>
        </div>
        <p class="verify-hint">&#x26A1; 快速选择正确释义：</p>
        <div class="verify-options">
          <button
            v-for="(opt, idx) in verifyOptions" :key="idx"
            class="verify-option"
            :class="{
              'correct': verifySelected !== null && opt.correct,
              'wrong': verifySelected === idx && !opt.correct,
              'idle': verifySelected === null
            }"
            :disabled="verifySelected !== null"
            @click="verify(idx)"
          >
            {{ opt.text }}
          </button>
        </div>
      </div>

      <!-- 阶段3: 结果 -->
      <div v-if="phase === 'show'" class="action-bar">
        <div class="result-badge" :class="lastCorrect ? 'correct' : 'wrong'">
          {{ lastResultType === 'correct' ? '&#x2705; 正确！' : (lastResultType === 'timeout' ? '&#x23F0; 超时' : (lastResultType === 'unknown' ? '&#x1F50D; 不认识' : '&#x274C; 错误')) }}
        </div>
        <button class="action-btn btn-next" @click="nextWord" :disabled="!isOnline">
          下一个 &#x1F449;
        </button>
      </div>
    </div>

    <!-- TiMo 助记助手 -->
    <div v-if="currentWord && !sessionDone" class="timo-study-fab">
      <TiMoFAB compact />
      <span class="timo-study-hint">问我助记技巧</span>
    </div>

    <!-- 学习完成 -->
    <div v-if="sessionDone" class="session-done">
      <div class="done-card">
        <div class="done-emoji">&#x1F389;</div>
        <h2>学习完成！</h2>
        <div class="done-stats">
          <div class="done-stat">
            <span class="done-stat-value green">{{ totalWordsStudied }}</span>
            <span class="done-stat-label">总数</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value blue">{{ correctCount }}</span>
            <span class="done-stat-label">正确</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value orange">{{ accuracy }}%</span>
            <span class="done-stat-label">正确率</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value purple">{{ formatTime(elapsedMs) }}</span>
            <span class="done-stat-label">用时</span>
          </div>
        </div>
          <div class="done-extra">
            <span>错词数：{{ finalWrongWords.length }}</span>
          </div>
          <!-- TiMo Coach Summary -->
          <div v-if="coachSummary" class="coach-summary">
            <div class="coach-header">
              <span class="coach-icon">&#x1F9D1;&#x200D;&#x1F3EB;</span>
              <span class="coach-label">TiMo 教练点评</span>
            </div>
            <p class="coach-text">{{ coachSummary }}</p>
          </div>
        <!-- 易错词列表 -->
        <div v-if="finalWrongWords.length" class="wrong-words-section">
          <div class="wrong-words-title">&#x1F525; 易错词</div>
          <div class="wrong-words-list">
            <div v-for="w in finalWrongWords" :key="w.id" class="wrong-word-item">
              <span class="wrong-word-text">{{ w.word }}</span>
              <span class="wrong-word-meaning" v-if="w.meanings && w.meanings[0]">{{ w.meanings[0].meaning }}</span>
            </div>
          </div>
        </div>
        <div class="done-actions">
          <el-button v-if="finalWrongWords.length" @click="retryWrong">
            错词重练 ({{ finalWrongWords.length }})
          </el-button>
          <el-button type="primary" @click="$router.push('/deep-learning?words=' + wrongWordIds)">
            去深度学习 &#x1F9E0;
          </el-button>
          <el-button @click="$router.push('/')">
            返回首页 &#x1F3E0;
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { getWordBatch } from '../api/words'
import { submitQuickMemory } from '../api/study'
import { getSessionReport, evaluateRealtimeNudge } from '../api/agent'
import { useStudyStore } from '../stores/study'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import { useNetwork } from '../composables/useNetwork'
import { useFatigueCheck } from '../composables/useFatigueCheck'
import { formatTime } from '../utils/formatTime'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const route = useRoute()
const studyStore = useStudyStore()
const agentStore = useAgentStore()
const userStore = useUserStore()
const { isOnline } = useNetwork()
const rootEl = ref(null)

let alive = true
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
const phase = ref('decide') // decide | verify | show
const countdown = ref(3)
const lastCorrect = ref(false)
const lastResultType = ref('') // 'correct' | 'wrong' | 'timeout' | 'unknown'
const sessionDone = ref(false)
const coachSummary = ref('')
const startTime = ref(Date.now())
const elapsedMs = ref(0)
const sessionStartTime = ref(Date.now())

// 结果收集（逐个提交）
const sessionResults = ref([]) // { wordId, recognized, verifiedCorrect, reactionTimeMs, repeat_error }

// 闪电验证
const verifyOptions = ref([])
const verifySelected = ref(null)
const verifyTimedOut = ref(false)

// 错词一次性重复
const wrongQueue = ref([]) // 错词重复队列
const isRepeatPhase = ref(false)
const firstPassWrongIds = ref(new Set()) // 第一轮中答错的 wordId
const finalWrongWords = ref([])

// Scenario 1: Agent intervention — track stubborn words needing deep learning
const suggestDeepLearningWords = ref([]) // words with consecutiveErrors >= 3

// Wave 6 Feature A — realtime nudge state
const recentFailures = ref(0)   // 连续失败计数（用于触发实时介入）
const nudgeShown = ref(false)   // 一次 session 内最多弹一次，避免骚扰

const { progress, setSessionQueue, updateProgress } = studyStore

const currentWord = computed(() => {
  if (isRepeatPhase.value && wrongQueue.value.length > 0) {
    return wrongQueue.value[currentIndex.value] || null
  }
  return words.value[currentIndex.value] || null
})
const showMeaningsPanel = computed(() => {
  return phase.value === 'show' && lastResultType.value === 'unknown'
})

const totalWordsStudied = computed(() => sessionResults.value.length)
const correctCount = computed(() => sessionResults.value.filter(r => r.grade_raw >= 3.0).length)
const accuracy = computed(() => {
  if (!sessionResults.value.length) return 0
  return Math.round(correctCount.value * 100 / sessionResults.value.length)
})

const progressPercent = computed(() => {
  const total = currentQueueLength.value
  return total > 0 ? Math.round((currentIndex.value / total) * 100) : 0
})

const wrongWordIds = computed(() => {
  if (!finalWrongWords.value.length) return ''
  return finalWrongWords.value.map(w => w.id).join(',')
})

// Timer 颜色
const timerPercent = computed(() => (countdown.value / 3) * 100)
const timerClass = computed(() => {
  if (countdown.value <= 1) return 'timer-red'
  if (countdown.value <= 2) return 'timer-yellow'
  return 'timer-green'
})

// 疲劳提醒
const { fatigueMinutes, showFatigueReminder, startFatigueCheck, dismissFatigue } = useFatigueCheck(sessionDone, sessionStartTime)

function startTimer(seconds) {
  if (mainTimer) { clearInterval(mainTimer); mainTimer = null }
  countdown.value = seconds
  const t0 = Date.now()
  mainTimer = setInterval(() => {
    const elapsed = Date.now() - t0
    countdown.value = Math.max(0, seconds - elapsed / 1000)
    if (countdown.value <= 0) {
      clearInterval(mainTimer)
      mainTimer = null
      onTimeout()
    }
  }, 50)
}

function onTimeout() {
  if (phase.value === 'verify') {
    verifyTimedOut.value = true
    verify(-1) // 超时
  }
}

// 键盘支持
function handleKeydown(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  if (e.code === 'Space' || e.key === 'Enter') {
    e.preventDefault()
    if (phase.value === 'decide') decide(true)
    else if (phase.value === 'show') nextWord()
  }
  // 数字键 1-4 选择验证选项
  if (phase.value === 'verify' && verifySelected.value === null) {
    const num = parseInt(e.key)
    if (num >= 1 && num <= 4) {
      e.preventDefault()
      verify(num - 1)
    }
  }
}

// 发音
function speakWord() {
  if (!currentWord.value) return
  const utterance = new SpeechSynthesisUtterance(currentWord.value.word)
  utterance.lang = 'en-US'
  utterance.rate = 0.8
  speechSynthesis.speak(utterance)
}

// 认识/不认识
function decide(recognized) {
  clearInterval(mainTimer)
  mainTimer = null
  verifyTimedOut.value = false

  if (!recognized) {
    // 不认识 → 展示释义+例句
    lastCorrect.value = false
    lastResultType.value = 'unknown'
    recordResult(currentWord.value, false, false, null)
    phase.value = 'show'
  } else {
    // 认识 → 闪电验证
    buildVerifyOptions()
    verifySelected.value = null
    verifyTimedOut.value = false
    startTime.value = Date.now()
    phase.value = 'verify'
    startTimer(3)
  }
}

// 构建四选一验证选项
function buildVerifyOptions() {
  const meanings = currentWord.value.meanings || []
  const correctText = meanings.length > 0 ? meanings[0].meaning : '未知释义'

  // 收集所有单词的全部释义作为干扰项池（包括当前单词的其他释义）
  const allWords = [...words.value, ...wrongQueue.value]
  const allDistractors = []

  for (const w of allWords) {
    if (w.meanings && w.meanings.length > 0) {
      for (const m of w.meanings) {
        if (m.meaning && m.meaning !== correctText) {
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
      distractors.push({ text: uniqueDistractors[i % uniqueDistractors.length], correct: false })
    }
  }

  const options = [
    { text: correctText, correct: true },
    ...distractors
  ]

  // 统一长度：超过12字截断加...
  const MAX_LEN = 12
  for (const opt of options) {
    if (opt.text.length > MAX_LEN) {
      opt.text = opt.text.slice(0, MAX_LEN) + '...'
    }
  }

  // 随机打乱
  for (let i = options.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [options[i], options[j]] = [options[j], options[i]]
  }
  verifyOptions.value = options
}

// 闪电验证结果
function verify(idx) {
  clearInterval(mainTimer)
  mainTimer = null
  verifySelected.value = idx
  const isCorrect = idx >= 0 && verifyOptions.value[idx]?.correct
  lastCorrect.value = isCorrect
  lastResultType.value = isCorrect ? 'correct' : (idx < 0 ? 'timeout' : 'wrong')

  const reactionMs = Date.now() - startTime.value

  recordResult(currentWord.value, true, isCorrect, reactionMs)

  safeSetTimeout(() => {
    if (phase.value === 'verify') {
      phase.value = 'show'
      safeSetTimeout(() => {
        if (phase.value === 'show') nextWord()
      }, 1500)
    }
  }, 600)
}

// 记录并提交单个结果
async function recordResult(word, recognized, verifiedCorrect, reactionMs) {
  // grade 由后端根据 RT 5 档评分：
  //   不认识 → 1.0
  //   认识 + 验证错 → 2.0
  //   认识 + 验证对 + RT > 2500ms → 3.0
  //   认识 + 验证对 + 1500 < RT ≤ 2500 → 3.5
  //   认识 + 验证对 + RT ≤ 1500ms → 4.0
  // 前端仅上报原始信号 (recognized / verifiedCorrect / reactionTimeMs)。
  const grade = recognized ? (verifiedCorrect ? 4.0 : 2.0) : 1.0
  try {
    const res = await submitQuickMemory({
      wordId: word.id,
      recognized,
      verifiedCorrect,
      reactionTimeMs: reactionMs
    })
    // Scenario 1: Track words that triggered deep learning suggestion
    if (res.data?.suggestDeepLearning) {
      const alreadyTracked = suggestDeepLearningWords.value.some(w => w.id === word.id)
      if (!alreadyTracked) {
        suggestDeepLearningWords.value.push(word)
      }
    }
  } catch { ElMessage.warning('答题结果保存失败，请检查网络') }
  // Track first-pass wrong words for repeat
  if (!isRepeatPhase.value && grade <= 2.0) {
    firstPassWrongIds.value.add(word.id)
  }
  sessionResults.value.push({
    wordId: word.id,
    recognized,
    verifiedCorrect,
    grade_raw: grade,
    reactionTimeMs: reactionMs,
    repeat_error: isRepeatPhase.value
  })

  // Wave 6 Feature A — 失败累计 / 实时介入
  if (grade < 3.0) {
    recentFailures.value++
    if (recentFailures.value >= 3 && !nudgeShown.value) {
      nudgeShown.value = true
      maybeShowRealtimeNudge('quick_memory')
    }
  } else {
    recentFailures.value = 0
  }
}

// Wave 6 Feature A — 调用后端实时介入接口（返回空时静默）
async function maybeShowRealtimeNudge(studyMode) {
  try {
    const res = await evaluateRealtimeNudge(studyMode)
    const data = res?.data
    if (data && data.message) {
      ElNotification({
        title: 'TiMo 小提示',
        message: data.message,
        type: 'warning',
        position: 'top-right',
        duration: 8000
      })
    }
  } catch { /* nudge 是辅助功能，失败静默 */ }
}

function nextWord() {
  if (currentIndex.value < currentQueueLength.value - 1) {
    currentIndex.value++
    startTime.value = Date.now()
    phase.value = 'decide'
  } else if (!isRepeatPhase.value) {
    // 第一轮结束 → 错词一次性重复
    finishFirstPass()
  } else {
    // 重复轮结束 → 结束会话
    endSession()
  }
}

const currentQueueLength = computed(() => {
  if (isRepeatPhase.value) return wrongQueue.value.length
  return words.value.length
})

// 第一轮结束 → 错词一次性重复
function finishFirstPass() {
  const wrongWords = words.value.filter(w => firstPassWrongIds.value.has(w.id))

  if (wrongWords.length > 0) {
    // 开始重复阶段
    wrongQueue.value = wrongWords
    isRepeatPhase.value = true
    currentIndex.value = 0
    phase.value = 'decide'
  } else {
    // 无错词，直接结束
    endSession()
  }
}

// 会话结束
function endSession() {
  elapsedMs.value = Date.now() - sessionStartTime.value
  sessionDone.value = true
  sessionStorage.removeItem(STORAGE_KEY)
  finalWrongWords.value = words.value.filter(w => firstPassWrongIds.value.has(w.id))

  // Generate TiMo coach summary
  try {
    getSessionReport({
      studyMode: 'quick_memory',
      totalWords: sessionResults.value.length,
      correctCount: correctCount.value,
      wrongCount: sessionResults.value.length - correctCount.value,
      elapsedMs: elapsedMs.value,
      wordTexts: words.value.map(w => w.word),
      wrongWordTexts: finalWrongWords.value.map(w => w.word)
    }).then(res => {
      if (res.data?.summary) {
        coachSummary.value = res.data.summary
        // Also push to TiMo chat for continuity
        agentStore.addMessage({ role: 'assistant', content: res.data.summary, actions: res.data.actions || [] })
        if (res.data.tiMoState) {
          agentStore.setTiMoState(res.data.tiMoState, 5000)
        }
      }
    }).catch(() => { /* summary is non-critical */ })
  } catch { /* ignore */ }

  // Scenario 1: Agent intervention → suggest deep learning for stubborn words
  if (suggestDeepLearningWords.value.length > 0) {
    triggerDeepLearningSuggestion()
  }
}


// Scenario 1: Agent proactive intervention — suggest switching to deep learning
function triggerDeepLearningSuggestion() {
  const wordList = suggestDeepLearningWords.value.map(w => w.word).join('、')
  const count = suggestDeepLearningWords.value.length
  const wordIds = suggestDeepLearningWords.value.map(w => w.id).join(',')

  agentStore.addMessage({
    role: 'assistant',
    content: `你有 ${count} 个单词连续出错了（${wordList}），说明快速记忆模式对这些词效果有限。要不要试试语境深度学习？在真实语境中学习，记忆会更牢固哦！`,
    actions: ['开始深度学习']
  })

  // Store the deep learning navigation info for the action button
  agentStore._pendingDeepLearningWordIds = wordIds

  if (!agentStore.isOpen) {
    agentStore.toggleDialog()
  }
}

function retryWrong() {
  words.value = [...finalWrongWords.value]
  wrongQueue.value = []
  isRepeatPhase.value = false
  firstPassWrongIds.value = new Set()
  finalWrongWords.value = []
  currentIndex.value = 0
  sessionDone.value = false
  phase.value = 'decide'
  sessionResults.value = []
  suggestDeepLearningWords.value = []
  recentFailures.value = 0
  nudgeShown.value = false
  studyStore.resetSession()
  setSessionQueue(words.value)
  startTime.value = Date.now()
}

// 进度持久化
const STORAGE_KEY = 'timo_quick_memory_progress'

function saveProgress() {
  if (!words.value.length || sessionDone.value) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
    wordIds: words.value.map(w => w.id),
    currentIndex: currentIndex.value,
    isRepeatPhase: isRepeatPhase.value,
    wrongQueueIds: wrongQueue.value.map(w => w.id)
  }))
}

function restoreProgress() {
  const saved = sessionStorage.getItem(STORAGE_KEY)
  if (!saved) return null
  try {
    return JSON.parse(saved)
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

async function loadWords() {
  // 优先从 sessionStorage 恢复进度
  const progress = restoreProgress()
  const wordIdsParam = progress
    ? progress.wordIds.join(',')
    : (sessionStorage.getItem('timo_study_word_ids') || route.query.words)
  if (!wordIdsParam) return
  sessionStorage.removeItem('timo_study_word_ids')
  loading.value = true
  try {
    const ids = wordIdsParam.split(',').map(Number)
    const res = await getWordBatch(ids)
    words.value = res.data
    setSessionQueue(words.value)
    sessionStartTime.value = Date.now()
    startTime.value = Date.now()
    // 恢复进度
    if (progress && progress.currentIndex > 0 && progress.currentIndex < words.value.length) {
      currentIndex.value = progress.currentIndex
      isRepeatPhase.value = progress.isRepeatPhase || false
      if (progress.wrongQueueIds && progress.wrongQueueIds.length) {
        wrongQueue.value = words.value.filter(w => progress.wrongQueueIds.includes(w.id))
      }
    }
    phase.value = 'decide'
  } catch {
    words.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  agentStore.setCurrentPage('quickMemory')
  if (userStore.token) {
    loadWords()
  }
  startFatigueCheck()
  window.addEventListener('beforeunload', saveProgress)
})

onBeforeUnmount(() => {
  alive = false
  clearAllTimers()
  window.removeEventListener('beforeunload', saveProgress)
  saveProgress()
})
</script>

<style scoped>
.quick-memory {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px;
  outline: none;
}

.progress-header { margin-bottom: 24px; }
.progress-header :deep(.el-progress-bar__outer) {
  border-radius: var(--radius-full);
  background: var(--color-border-lighter);
}

.progress-info {
  display: flex; align-items: center; justify-content: space-between; margin-top: 8px;
}
.progress-text { font-weight: 800; font-size: 14px; color: var(--color-text-secondary); }

.timer {
  display: flex; align-items: baseline; gap: 2px;
  padding: 4px 12px; border-radius: var(--radius-full);
  transition: all 0.3s; position: relative; overflow: hidden;
}
.timer-num { font-size: 20px; font-weight: 900; font-family: var(--font-mono); z-index: 1; }
.timer-unit { font-size: 12px; font-weight: 700; z-index: 1; }
.timer-green { background: var(--color-primary-bg); }
.timer-green .timer-num, .timer-green .timer-unit { color: var(--color-primary); }
.timer-yellow { background: #FFF8E1; }
.timer-yellow .timer-num, .timer-yellow .timer-unit { color: #F57C00; }
.timer-red { background: var(--color-red-light); }
.timer-red .timer-num, .timer-red .timer-unit { color: var(--color-red); animation: pulse 0.5s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }

.card-area { display: flex; flex-direction: column; align-items: center; }

.word-card {
  width: 100%; min-height: 280px;
  border: 3px solid var(--color-border-lighter); border-radius: var(--radius-xl);
  background: #FFFFFF; box-shadow: 0 6px 0 var(--color-border-lighter);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}
.card-front {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 32px;
}

.meanings-expanded {
  width: 100%; margin-top: 16px; padding: 20px;
  background: #FFFFFF; border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-lg); text-align: left;
}

.word-main { font-size: 40px; font-weight: 900; color: var(--color-text-primary); text-align: center; }
.word-main.small { font-size: 28px; margin-bottom: 12px; }
.word-phonetic { font-size: 16px; color: var(--color-text-muted); margin-top: 8px; font-weight: 600; }

.speak-btn {
  margin-top: 12px; width: 44px; height: 44px;
  border-radius: 50%; border: 2px solid var(--color-border-base);
  background: #fff; cursor: pointer; font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.speak-btn:hover { background: var(--color-primary-bg); border-color: var(--color-primary); transform: scale(1.1); }

.meanings-list { width: 100%; }
.meaning-item {
  font-size: 18px; color: var(--color-text-regular); line-height: 1.8; font-weight: 600;
  padding: 4px 0;
}
.meaning-item.primary { color: var(--color-text-primary); font-weight: 800; font-size: 20px; }
.pos-tag { color: var(--color-blue); font-size: 14px; margin-right: 4px; font-weight: 700; }

.examples-section {
  width: 100%; margin-top: 16px; padding-top: 12px;
  border-top: 1px solid var(--color-border-lighter);
}
.examples-title { font-size: 13px; font-weight: 800; color: var(--color-text-secondary); margin-bottom: 8px; }
.example-item { margin-bottom: 8px; }
.example-en { font-size: 14px; color: var(--color-text-primary); line-height: 1.6; font-style: italic; }
.example-cn { font-size: 13px; color: var(--color-text-muted); line-height: 1.5; margin-top: 2px; }

.tip-section {
  width: 100%; margin-top: 12px; padding: 10px 12px;
  background: var(--color-primary-bg); border-radius: var(--radius-md);
  font-size: 13px; color: var(--color-text-secondary); line-height: 1.6;
}
.tip-label { font-weight: 800; color: var(--color-primary-dark); margin-right: 4px; }

.action-bar {
  margin-top: 24px; display: flex; flex-direction: column; align-items: center;
  gap: 12px; width: 100%;
}

.action-btn {
  width: 100%; max-width: 320px; padding: 16px 24px;
  border-radius: var(--radius-lg); border: 3px solid;
  font-size: 17px; font-weight: 800; font-family: var(--font-family);
  cursor: pointer; transition: all 0.15s ease;
  display: flex; align-items: center; justify-content: center; gap: 8px;
}
.action-btn:active { transform: translateY(3px); }

.btn-yes {
  background: var(--color-primary); border-color: var(--color-primary-dark);
  color: #fff; box-shadow: 0 4px 0 var(--color-primary-darker);
}
.btn-yes:active { box-shadow: 0 1px 0 var(--color-primary-darker); }

.btn-no {
  background: #FFFFFF; border-color: var(--color-border-base);
  color: var(--color-text-primary); box-shadow: 0 4px 0 var(--color-border-base);
}
.btn-no:active { box-shadow: 0 1px 0 var(--color-border-base); }

.btn-next {
  background: var(--color-blue); border-color: var(--color-blue-dark);
  color: #fff; box-shadow: 0 4px 0 #1080C0;
}
.btn-next:active { box-shadow: 0 1px 0 #1080C0; }

.btn-emoji { font-size: 20px; }

/* 闪电验证 */
.verify-bar { gap: 16px; }
.verify-timer-bar {
  width: 100%; max-width: 320px; height: 6px;
  background: var(--color-border-lighter); border-radius: var(--radius-full);
  overflow: hidden;
}
.verify-timer-fill {
  height: 100%; border-radius: var(--radius-full);
  transition: width 0.1s linear;
}
.verify-timer-fill.timer-green { background: var(--color-primary); }
.verify-timer-fill.timer-yellow { background: #F57C00; }
.verify-timer-fill.timer-red { background: var(--color-red); }

.verify-hint {
  font-size: 15px; color: var(--color-text-secondary); font-weight: 700; margin: 0;
}

.verify-options {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; max-width: 400px;
}

.verify-option {
  padding: 10px 12px; width: 100%; box-sizing: border-box;
  border-radius: var(--radius-lg); border: 3px solid var(--color-border-base);
  background: #fff; cursor: pointer;
  font-size: 14px; font-weight: 800; font-family: var(--font-family);
  color: var(--color-text-primary);
  box-shadow: 0 4px 0 var(--color-border-base);
  transition: all 0.15s ease; text-align: center;
  height: 64px; display: flex; align-items: center; justify-content: center;
  line-height: 1.3;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin: 0;
}
.verify-option:active:not(:disabled) { transform: translateY(3px); box-shadow: 0 1px 0 var(--color-border-base); }
.verify-option:disabled { cursor: not-allowed; }

.verify-option.idle:hover:not(:disabled) {
  border-color: var(--color-primary); background: var(--color-primary-bg);
}
.verify-option.correct {
  background: var(--color-primary-bg) !important; border-color: var(--color-primary) !important;
  color: var(--color-primary-dark) !important; box-shadow: 0 4px 0 var(--color-primary-dark) !important;
  animation: correctPulse 0.3s ease;
}
.verify-option.wrong {
  background: var(--color-red-light) !important; border-color: var(--color-red) !important;
  color: var(--color-red-dark) !important; box-shadow: 0 4px 0 var(--color-red-dark) !important;
  animation: wrongShake 0.4s ease;
}

@keyframes correctPulse { 0%{transform:scale(1)} 50%{transform:scale(1.03)} 100%{transform:scale(1)} }
@keyframes wrongShake { 0%,100%{transform:translateX(0)} 25%{transform:translateX(-6px)} 75%{transform:translateX(6px)} }

.result-badge {
  padding: 12px 24px; border-radius: var(--radius-full);
  font-size: 18px; font-weight: 900; animation: bounceIn 0.4s ease;
}
.result-badge.correct { background: var(--color-primary-bg); color: var(--color-primary-dark); }
.result-badge.wrong { background: var(--color-red-light); color: var(--color-red-dark); }

.empty-state, .loading-state {
  display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 0;
}
.empty-icon { font-size: 48px; }
.empty-state h3 { font-size: 20px; font-weight: 800; color: var(--color-text-primary); }
.empty-state p { color: var(--color-text-secondary); font-weight: 600; }

.loading-spinner {
  width: 40px; height: 40px;
  border: 4px solid var(--color-border-lighter); border-top-color: var(--color-primary);
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes bounceIn { 0%{transform:scale(0.3);opacity:0} 50%{transform:scale(1.05)} 100%{transform:scale(1);opacity:1} }
@keyframes fadeSlideUp { 0%{opacity:0;transform:translateY(10px)} 100%{opacity:1;transform:translateY(0)} }

.session-done { padding-top: 20px; }

.done-card {
  background: #FFFFFF; border: 3px solid var(--color-primary);
  border-radius: var(--radius-xl); padding: 32px; text-align: center;
  box-shadow: 0 6px 0 var(--color-primary-dark);
}
.done-emoji { font-size: 48px; margin-bottom: 8px; }
.done-card h2 { font-size: 24px; font-weight: 900; color: var(--color-text-primary); margin-bottom: 20px; }

.done-stats { display: flex; justify-content: center; gap: 24px; margin-bottom: 16px; }
.done-stat { display: flex; flex-direction: column; align-items: center; }
.done-stat-value { font-size: 28px; font-weight: 900; font-family: var(--font-mono); line-height: 1.1; }
.done-stat-value.green { color: var(--color-primary-dark); }
.done-stat-value.blue { color: var(--color-blue-dark); }
.done-stat-value.orange { color: var(--color-orange-dark); }
.done-stat-value.purple { color: var(--color-purple-dark); }
.done-stat-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); margin-top: 4px; }

.done-extra {
  display: flex; justify-content: center; gap: 12px;
  font-size: 13px; color: var(--color-text-muted); font-weight: 600; margin-bottom: 20px;
}

.wrong-words-section {
  margin-bottom: 20px; padding: 16px; background: var(--color-red-light);
  border-radius: var(--radius-md); border: 2px solid var(--color-red);
  text-align: left;
}
.wrong-words-title { font-size: 14px; font-weight: 800; color: var(--color-red-dark); margin-bottom: 10px; }
.wrong-words-list { display: flex; flex-direction: column; gap: 6px; }
.wrong-word-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 10px;
  background: #fff; border-radius: var(--radius-sm);
}
.wrong-word-text { font-weight: 800; font-size: 14px; color: var(--color-text-primary); min-width: 80px; }
  .wrong-word-meaning { font-size: 13px; color: var(--color-text-secondary); }

  /* TiMo 教练点评 */
  .coach-summary {
    margin-bottom: 20px; padding: 14px 16px;
    background: linear-gradient(135deg, #E3F2FD, #BBDEFB);
    border: 2px solid #2196F3; border-radius: var(--radius-md);
    text-align: left; animation: fadeSlideUp 0.4s ease;
  }
  .coach-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
  .coach-icon { font-size: 18px; }
  .coach-label { font-size: 13px; font-weight: 800; color: #1565C0; }
  .coach-text { font-size: 14px; color: var(--color-text-primary); line-height: 1.6; margin: 0; }

  .done-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

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

.timo-study-fab {
  display: flex; align-items: center; gap: 8px; justify-content: center;
  margin-top: 16px; padding: 8px 0;
}
.timo-study-hint {
  font-size: 12px; font-weight: 700; color: var(--color-text-muted);
}
</style>
