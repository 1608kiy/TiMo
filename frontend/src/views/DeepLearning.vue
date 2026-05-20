<template>
  <div class="deep-learning" tabindex="0" @keydown="handleKeydown">
    <!-- 离线提醒横幅 -->
    <transition name="fade-slide">
      <div v-if="!isOnline" class="offline-banner">
        <span class="offline-banner-icon">&#x1F4F5;</span>
        <span class="offline-banner-text">网络已断开，部分功能暂不可用</span>
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

    <!-- 进度条 -->
    <div class="progress-bar-wrap">
      <el-progress
        :percentage="progressPercent"
        :stroke-width="8"
        :format="() => progressLabel"
      />
    </div>

    <!-- 空状态 -->
    <div v-if="!words.length && !loading" class="empty-state">
      <el-empty description="请从选词入口选择单词开始学习" />
      <el-button type="primary" @click="$router.push('/word-select')">去选词</el-button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="empty-state">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载单词中...</p>
    </div>

    <!-- Phase 1: 逐步学习 (Step 1 + Step 2) -->
    <div v-if="phase === 'learn' && currentWord" class="learn-area">
      <!-- Step 1: 词卡 + 真题考点 -->
      <div v-if="step === 1" class="step-content">
        <div class="step-title">Step 1: 认识这个词</div>
        <div class="word-card">
          <div class="word-main">
            {{ currentWord.word }}
            <button class="speak-btn" @click="speakWord" title="朗读">&#x1F50A;</button>
          </div>
          <div class="word-phonetic" v-if="currentWord.phonetic">{{ currentWord.phonetic }}</div>
          <div v-if="currentWord.examples && currentWord.examples.length > 0" class="exam-points">
            <div v-for="(ex, ei) in currentWord.examples.slice(0, 2)" :key="ex.id || ei" class="exam-point">
              <div class="exam-point-label">&#x1F4D6; 真题考点<span v-if="ex.source" class="exam-source">{{ ex.source }}</span></div>
              <div class="exam-point-sentence">{{ ex.sentence }}</div>
              <div v-if="ex.translation" class="exam-point-translation">{{ ex.translation }}</div>
            </div>
          </div>
          <div v-if="currentWord.mnemonic" class="mnemonic-box">
            <div class="mnemonic-label">&#x1F4A1; TiMo 助记</div>
            <div class="mnemonic-text">{{ currentWord.mnemonic }}</div>
          </div>
        </div>
        <el-button size="large" type="primary" @click="step1Done">已阅读，下一步</el-button>
      </div>

      <!-- Step 2: 选词义 (4选1) -->
      <div v-if="step === 2" class="step-content">
        <div class="step-title">Step 2: 选择正确释义</div>
        <div v-if="step2Sentence" class="step2-sentence" v-html="step2Sentence"></div>
        <div v-else class="word-display">{{ currentWord.word }}</div>
        <div class="options-grid">
          <el-button
            v-for="(opt, idx) in meaningOptions" :key="idx"
            size="large" class="option-btn"
            :type="selectedMeaning === idx ? (idx === correctMeaningIdx ? 'success' : 'danger') : ''"
            :disabled="selectedMeaning !== null"
            @click="selectMeaning(idx)"
          >
            {{ opt }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- Phase 2: 组匹配 (Step 3) -->
    <div v-if="phase === 'groupMatch'" class="learn-area">
      <div class="step-title batch-title">Step 3: 匹配释义</div>
      <p class="step-desc">将每个单词与正确的释义配对</p>

      <!-- 桌面端：点击/拖拽匹配 -->
      <div class="match-container" v-if="!isMobile">
        <div class="match-columns">
          <div class="match-col match-words-col">
            <div v-for="(w, idx) in words" :key="w.id"
                 class="match-card match-word-card"
                 :class="{
                   selected: matchSelectedWord === idx,
                   matched: matchAnswers[idx] !== null && !matchSubmitted,
                   correct: matchSubmitted && matchAnswers[idx] === getMeaning(w),
                   wrong: matchSubmitted && matchAnswers[idx] !== getMeaning(w),
                   'hint-highlight': !matchSubmitted && matchHintWordIdx === idx
                 }"
                 draggable="true"
                 @dragstart="onMatchDragStart(idx, 'word', $event)"
                 @click="onMatchCardClick(idx, 'word')">
              {{ w.word }}
              <span v-if="matchAnswers[idx] !== null && !matchSubmitted" class="match-remove"
                    @click.stop="removeMatch(idx)">&times;</span>
            </div>
          </div>
          <div class="match-col match-meanings-col">
            <div v-for="(m, idx) in matchShuffledMeanings" :key="idx"
                 class="match-card match-meaning-card"
                 :class="{
                   selected: matchSelectedMeaning === idx,
                   matched: matchMeaningUsed[idx] && !matchSubmitted,
                   correct: matchSubmitted && matchSubmittedAnswers[idx] === m.meaning,
                   wrong: matchSubmitted && matchSubmittedAnswers[idx] !== m.meaning,
                   'hint-highlight': !matchSubmitted && matchHintMeaningIdx === idx
                 }"
                 draggable="true"
                 @dragstart="onMatchDragStart(idx, 'meaning', $event)"
                 @dragover.prevent="onMatchDragOver"
                 @drop="onMatchDrop(idx, $event)"
                 @click="onMatchCardClick(idx, 'meaning')">
              {{ m.meaning }}
            </div>
          </div>
        </div>
        <svg class="match-lines-svg">
          <line v-for="(line, li) in matchLines" :key="li"
                :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
                :stroke="line.correct === true ? 'var(--color-primary)' : (matchSubmitted && line.correct === false ? 'var(--color-red)' : '#999')"
                stroke-width="2" />
        </svg>
      </div>

      <!-- 移动端：下拉选择 -->
      <div class="match-grid" v-else>
        <div v-for="(w, idx) in words" :key="w.id" class="match-row"
             :class="{
               correct: matchSubmitted && matchAnswers[idx] === getMeaning(w),
               wrong: matchSubmitted && matchAnswers[idx] !== getMeaning(w),
               'hint-highlight': !matchSubmitted && matchHintWordIdx === idx
             }">
          <span class="match-word-label">{{ w.word }}</span>
          <el-select v-model="matchAnswers[idx]" placeholder="选择释义" size="large"
                     :disabled="matchSubmitted" class="match-select">
            <el-option v-for="(opt, oi) in matchShuffledMeanings" :key="oi" :label="opt.meaning" :value="opt.meaning" />
          </el-select>
          <span v-if="matchSubmitted" class="match-result-icon">
            {{ matchAnswers[idx] === getMeaning(w) ? '&#x2713;' : '&#x2717;' }}
          </span>
        </div>
      </div>

      <div class="match-actions">
        <el-button v-if="!matchSubmitted" @click="useMatchHint" size="small" class="hint-btn">
          &#x1F4A1; 提示 ({{ matchHintUsed }}/{{ words.length }})
        </el-button>
        <el-button v-if="!matchSubmitted" type="primary" size="large"
                   :disabled="matchAnswers.some(a => a === null)" @click="submitGroupMatch">
          确认提交
        </el-button>
        <el-button v-if="matchSubmitted" type="primary" size="large" @click="enterGroupPassage">
          下一步
        </el-button>
      </div>
    </div>

    <!-- Phase 3: 组填空 (Step 4) -->
    <div v-if="phase === 'groupPassage'" class="learn-area">
      <div class="step-title batch-title">Step 4: 语境填空</div>
      <p class="step-desc">点击单词，再点击空格填入</p>
      <div v-if="passageLoading" class="passage-loading">&#x23F3; AI 正在生成语境...</div>
      <template v-else>
        <!-- 单词池 -->
        <div class="passage-pool">
          <span v-for="(w, wi) in words" :key="w.id" class="pool-word"
                :class="{ selected: selectedPoolWord === wi, used: usedPoolWords.has(wi) }"
                @click="selectPoolWord(wi)">{{ w.word }}</span>
        </div>
        <!-- 短文 -->
        <div class="passage-box group-passage-box">
          <template v-for="(seg, si) in passageSegments" :key="si">
            <span v-if="seg.type === 'text'">{{ seg.content }}</span>
            <span v-else-if="seg.type === 'blank'" class="blank-slot"
                  :class="{ filled: blankAnswers[seg.blankIdx], active: selectedPoolWord !== null && !blankSubmitted,
                           'hint-highlight-slot': !blankSubmitted && showPassageHint && passageHintIdx === seg.blankIdx }"
                  @click="fillBlank(seg.blankIdx)">
              {{ blankAnswers[seg.blankIdx] || seg.placeholder }}
            </span>
          </template>
        </div>
      </template>
      <el-button v-if="!blankSubmitted && !passageLoading" @click="usePassageHint" size="small" class="hint-btn">
        &#x1F4A1; 提示 ({{ passageHintUsed }}/3)
      </el-button>
      <el-button v-if="!blankSubmitted && !passageLoading" type="primary" size="large"
                 :disabled="blankAnswers.some(a => !a)" @click="submitGroupPassage">
        确认提交
      </el-button>
      <div v-if="blankSubmitted" class="passage-results">
        <div v-for="(blankIdx, ri) in blankToWordIdx" :key="ri" class="passage-result-item"
             :class="getBlankTrimmed(blankIdx) === words[blankToWordIdx[blankIdx]]?.word?.toLowerCase() ? 'correct' : 'wrong'">
          <span class="pr-word">{{ words[blankToWordIdx[blankIdx]]?.word }}</span>
          <span class="pr-answer">你的答案: {{ blankAnswers[blankIdx] || '—' }}</span>
          <span v-if="getBlankTrimmed(blankIdx) !== words[blankToWordIdx[blankIdx]]?.word?.toLowerCase()" class="pr-correct">
            正确: {{ words[blankToWordIdx[blankIdx]]?.word }}
          </span>
        </div>
      </div>
      <el-button v-if="blankSubmitted" type="primary" size="large" @click="enterSpellPhase">
        下一步
      </el-button>
    </div>

    <!-- Phase 4: 逐词拼写 (Step 5) -->
    <div v-if="phase === 'spell'" class="learn-area">
      <div class="step-title">Step 5: 补全单词</div>
      <div class="spell-progress">{{ spellIndex + 1 }} / {{ spellQueue.length }}</div>
      <div class="word-display meaning-display">{{ currentSpellWord ? getMeaning(currentSpellWord) : '' }}</div>
      <div class="complete-hint">{{ completeHint }}</div>
      <el-button v-if="!completeSubmitted && hintLevel < 2"
        size="default" @click="showMoreHints" class="hint-btn">
        &#x1F4A1; 提示 ({{ hintLevel }}/2)
      </el-button>
      <div class="complete-input-wrap">
        <span v-if="!completeAnswer && !completeSubmitted && currentSpellWord" class="first-letter-hint">{{ currentSpellWord.word[0] }}</span>
        <el-input ref="completeInput" v-model="completeAnswer" size="large" class="complete-input"
          placeholder="输入完整单词..." :disabled="completeSubmitted" @keyup.enter="checkComplete" />
      </div>
      <el-button v-if="!completeSubmitted" size="large" type="primary" @click="checkComplete">确认</el-button>
      <div v-if="completeSubmitted" class="blank-result" :class="completeCorrect ? 'correct' : 'wrong'">
        {{ completeCorrect ? '&#x2713; 正确' : '正确答案: ' + (currentSpellWord ? currentSpellWord.word : '') }}
      </div>
    </div>

    <!-- Phase 5: 组总结 (Step 6) -->
    <div v-if="phase === 'summary' && !sessionDone" class="session-done">
      <div class="done-card">
        <div style="font-size: 48px; margin-bottom: 8px;">&#x1F389;</div>
        <h2>组学习完成！</h2>
        <div class="done-stats">
          <div class="done-stat">
            <span class="done-stat-value blue">{{ words.length }}</span>
            <span class="done-stat-label">总数</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value green">{{ avgAccuracy }}%</span>
            <span class="done-stat-label">正确率</span>
          </div>
          <div class="done-stat">
            <span class="done-stat-value orange">{{ formatTime(elapsedMs) }}</span>
            <span class="done-stat-label">用时</span>
          </div>
        </div>
        <!-- 薄弱词列表 -->
        <div v-if="weakWords.length" class="weak-section">
          <div class="weak-title">&#x1F525; 薄弱词</div>
          <div class="weak-list">
            <div v-for="w in weakWords" :key="w.id" class="weak-item">
              <span class="weak-word">{{ w.word }}</span>
              <span class="weak-meaning" v-if="w.meanings && w.meanings[0]">{{ w.meanings[0].meaning }}</span>
            </div>
          </div>
          <el-button size="default" type="warning" @click="retryWeak" class="retry-weak-btn">
            重学薄弱词 ({{ weakWords.length }})
          </el-button>
        </div>
        <div class="done-actions">
          <el-button type="primary" :loading="submitting" :disabled="!isOnline" @click="submitGroupResults">
            {{ !isOnline ? '网络断开' : submitting ? '提交中...' : '完成提交' }}
          </el-button>
          <el-button @click="$router.push('/')">返回首页 &#x1F3E0;</el-button>
        </div>
      </div>
    </div>

    <!-- Session Done -->
    <div v-if="sessionDone" class="session-done">
      <div class="done-card">
        <div style="font-size: 48px; margin-bottom: 8px;">&#x2705;</div>
        <h2>全部完成！</h2>
        <div class="done-actions">
          <el-button type="primary" @click="$router.push('/review')">去复习 &#x1F504;</el-button>
          <el-button @click="$router.push('/')">返回首页 &#x1F3E0;</el-button>
        </div>
      </div>
    </div>

    <!-- TiMo FAB -->
    <TiMoFAB />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getWordBatch, getWordList } from '../api/words'
import { submitContextDeepGroup } from '../api/study'
import { generatePassage } from '../api/agent'
import { getReviewQueue } from '../api/review'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import { useNetwork } from '../composables/useNetwork'
import { useFatigueCheck } from '../composables/useFatigueCheck'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const route = useRoute()
const agentStore = useAgentStore()
const userStore = useUserStore()
const { isOnline } = useNetwork()

// ===== 核心状态 =====
const loading = ref(false)
const words = ref([])
const phase = ref('learn') // 'learn' | 'groupMatch' | 'groupPassage' | 'spell' | 'summary'
const sessionDone = ref(false)
const submitting = ref(false)
const sessionStartTime = ref(Date.now())

// ===== 疲劳提醒 =====
const { fatigueMinutes, showFatigueReminder, startFatigueCheck, dismissFatigue } = useFatigueCheck(sessionDone, sessionStartTime)

// ===== 通用工具 =====
let alive = true
const pendingTimeouts = []
function stepTimeout(fn, ms) {
  const id = setTimeout(() => {
    const idx = pendingTimeouts.indexOf(id)
    if (idx !== -1) pendingTimeouts.splice(idx, 1)
    if (alive) fn()
  }, ms)
  pendingTimeouts.push(id)
  return id
}

import { shuffle } from '../utils/shuffle'
import { formatTime } from '../utils/formatTime'
const elapsedMs = ref(0)

// Phase 1: 逐步学习
const wordIndex = ref(0)
const step = ref(1) // 1 or 2
const wordStartTime = ref(Date.now())
const readingDuration = ref(0)

// Step 2
const meaningOptions = ref([])
const selectedMeaning = ref(null)
const correctMeaningIdx = ref(0)

// Phase 2: 组匹配 (Step 3)
const isMobile = ref(false)
const matchAnswers = ref([])
const matchSubmitted = ref(false)
const matchHintUsed = ref(0)
const matchSelectedWord = ref(-1)
const matchSelectedMeaning = ref(-1)
const matchShuffledMeanings = ref([])
const matchMeaningUsed = ref([])
const matchSubmittedAnswers = ref([])
const matchHintWordIdx = ref(-1)
const matchHintMeaningIdx = ref(-1)
const matchHintedWords = ref(new Set())
const matchLines = ref([])

// Phase 3: 组填空 (Step 4)
const passageData = ref(null)
const passageSegments = ref([])
const blankAnswers = ref([])
const blankToWordIdx = ref([]) // blankIdx → wordIdx mapping
const blankSubmitted = ref(false)
const passageLoading = ref(false)
const passageHintUsed = ref(0)
const showPassageHint = ref(false)
const passageHintIdx = ref(-1)
const passageHintedBlanks = ref(new Set())
const selectedPoolWord = ref(null)
const usedPoolWords = ref(new Set())

// Phase 4: 逐词拼写 (Step 5)
const spellIndex = ref(0)
const spellQueue = ref([])
const spellRetryQueue = ref([])
const completeHint = ref('')
const completeAnswer = ref('')
const completeSubmitted = ref(false)
const completeCorrect = ref(false)
const hintLevel = ref(0)
const hintTotalAccum = ref(0)
const completeInput = ref(null)

// 评分数据 — 每个词独立记录
const wordScores = ref([])
// 结构: [{ wordId, s2, s3, s4, s5, hintTotal, reactionTimeMs, dwellTimeMs }]

// Agent 生成的短文
const passageDataReady = ref(false)

const currentWord = computed(() => words.value[wordIndex.value] || null)
const currentSpellWord = computed(() => spellQueue.value[spellIndex.value] || null)

function getMeaning(w) {
  return w.meanings && w.meanings.length > 0 ? w.meanings[0].meaning : '未知'
}

function getBlankTrimmed(idx) {
  return (blankAnswers.value[idx] || '').trim().toLowerCase()
}

const step2Sentence = computed(() => {
  if (!currentWord.value) return ''
  const w = currentWord.value
  // 优先使用数据库中的例句
  if (w.examples && w.examples.length > 0) {
    const sentence = w.examples[0].sentence || ''
    // 高亮目标单词（按长度排序避免短词匹配长词中的子串）
    const pattern = new RegExp('\\b(' + w.word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')\\b', 'gi')
    return sentence.replace(pattern, '<span class="highlight-word">$1</span>')
  }
  // 无例句时回退
  return 'What does <span class="highlight-word">' + w.word + '</span> mean?'
})

// ===== 进度计算 =====
const progressPercent = computed(() => {
  if (!words.value.length) return 0
  const total = words.value.length
  switch (phase.value) {
    case 'learn':
      return Math.round(((wordIndex.value + step.value / 2) / total) * 50)
    case 'groupMatch':
      return 55
    case 'groupPassage':
      return 70
    case 'spell':
      return 70 + Math.round(((spellIndex.value + 1) / total) * 25)
    case 'summary':
      return 100
    default:
      return 0
  }
})

const progressLabel = computed(() => {
  switch (phase.value) {
    case 'learn':
      return `词 ${wordIndex.value + 1}/${words.value.length} | ${step.value === 1 ? '词卡' : '释义'}`
    case 'groupMatch':
      return 'Step 3: 匹配释义'
    case 'groupPassage':
      return 'Step 4: 语境填空'
    case 'spell':
      return `Step 5: 拼写 ${spellIndex.value + 1}/${words.value.length}`
    case 'summary':
      return '完成'
    default:
      return ''
  }
})

// ===== 薄弱词 =====
function getCompositeGrade(s) {
  const composite = 1.0 + 3.0 * (0.20 * s.s2 / 4 + 0.25 * s.s3 / 4 + 0.30 * s.s4 / 4 + 0.25 * s.s5 / 4)
  const penalty = 1.0 - 0.2 * (1.0 - s.s5 / 4.0)
  return composite * penalty
}

const weakWords = computed(() => {
  return words.value.filter(w => {
    const s = wordScores.value.find(sc => sc.wordId === w.id)
    if (!s) return false
    const composite = getCompositeGrade(s)
    return s.s5 <= 1 || composite < 2.5
  })
})

const avgAccuracy = computed(() => {
  if (!wordScores.value.length) return 0
  const total = wordScores.value.reduce((sum, s) => {
    return sum + getCompositeGrade(s)
  }, 0)
  return Math.round((total / wordScores.value.length / 4) * 100)
})

// ===== Phase 1: 逐步学习 =====
function initWord() {
  step.value = 1
  selectedMeaning.value = null
  wordStartTime.value = Date.now()
}

function speakWord() {
  if (!currentWord.value) return
  const utterance = new SpeechSynthesisUtterance(currentWord.value.word)
  utterance.lang = 'en-US'
  utterance.rate = 0.8
  speechSynthesis.speak(utterance)
}

// ===== 键盘快捷键 =====
function handleKeydown(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  // learn/step1: Space/Enter → 下一步
  if (phase.value === 'learn' && step.value === 1 && (e.code === 'Space' || e.key === 'Enter')) {
    e.preventDefault()
    step1Done()
  }
  // learn/step2: 1-4 → 选择释义
  if (phase.value === 'learn' && step.value === 2 && selectedMeaning.value === null) {
    const idx = parseInt(e.key) - 1
    if (idx >= 0 && idx < meaningOptions.value.length) {
      e.preventDefault()
      selectMeaning(idx)
    }
  }
  // groupMatch: Enter → 提交/下一步
  if (phase.value === 'groupMatch' && e.key === 'Enter') {
    e.preventDefault()
    if (matchSubmitted.value) enterGroupPassage()
    else if (!matchAnswers.value.some(a => a === null)) submitGroupMatch()
  }
  // groupPassage: Enter → 提交/下一步
  if (phase.value === 'groupPassage' && e.key === 'Enter' && !passageLoading.value) {
    e.preventDefault()
    if (blankSubmitted.value) enterSpellPhase()
    else if (!blankAnswers.value.some(a => !a)) submitGroupPassage()
  }
  // summary: Enter → 提交
  if (phase.value === 'summary' && !sessionDone.value && e.key === 'Enter') {
    e.preventDefault()
    submitGroupResults()
  }
}

function step1Done() {
  readingDuration.value = Date.now() - wordStartTime.value
  if (readingDuration.value < 3000) {
    // 记录 hint_total，后面合并到 wordScores
    if (!wordScores.value[wordIndex.value]) {
      wordScores.value[wordIndex.value] = { wordId: currentWord.value.id, s2: 0, s3: 0, s4: 0, s5: 0, hintTotal: 1, reactionTimeMs: 0, dwellTimeMs: readingDuration.value }
    } else {
      wordScores.value[wordIndex.value].hintTotal++
      wordScores.value[wordIndex.value].dwellTimeMs = readingDuration.value
    }
  }
  step.value = 2
  buildMeaningOptions()
}

function buildMeaningOptions() {
  const meanings = currentWord.value.meanings || []
  const correct = meanings.length > 0 ? meanings[0].meaning : '未知'

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

  const opts = [correct, ...distractors]

  // 统一长度：超过12字截断加...
  const MAX_LEN = 12
  for (let i = 0; i < opts.length; i++) {
    if (opts[i].length > MAX_LEN) {
      opts[i] = opts[i].slice(0, MAX_LEN) + '...'
    }
  }

  shuffle(opts)
  correctMeaningIdx.value = opts.indexOf(correct)
  meaningOptions.value = opts
  selectedMeaning.value = null
}

function selectMeaning(idx) {
  selectedMeaning.value = idx
  const correct = idx === correctMeaningIdx.value
  const s2 = correct ? 4 : 0
  const reactionTimeMs = Date.now() - wordStartTime.value

  wordScores.value[wordIndex.value] = {
    wordId: currentWord.value.id,
    s2, s3: 0, s4: 0, s5: 0,
    hintTotal: wordScores.value[wordIndex.value]?.hintTotal || 0,
    reactionTimeMs,
    dwellTimeMs: readingDuration.value || 0
  }

  stepTimeout(() => {
    if (wordIndex.value < words.value.length - 1) {
      wordIndex.value++
      initWord()
    } else {
      // 所有词完成 Step 1+2，进入 Phase 2: 组匹配
      enterGroupMatch()
    }
  }, 1500)
}

// ===== Phase 2: 组匹配 (Step 3) =====
function detectMobile() {
  isMobile.value = window.innerWidth <= 768 || 'ontouchstart' in window
}

function enterGroupMatch() {
  phase.value = 'groupMatch'
  window.addEventListener('resize', updateMatchLines)
  matchSubmitted.value = false
  matchHintUsed.value = 0
  matchSelectedWord.value = -1
  matchSelectedMeaning.value = -1
  matchHintWordIdx.value = -1
  matchHintMeaningIdx.value = -1
  matchHintedWords.value = new Set()
  matchLines.value = []

  const meanings = words.value.map((w, i) => ({
    meaning: w.meanings && w.meanings.length > 0 ? w.meanings[0].meaning : '未知',
    wordIdx: i
  }))
  matchShuffledMeanings.value = shuffle([...meanings])
  matchMeaningUsed.value = new Array(matchShuffledMeanings.value.length).fill(false)
  matchSubmittedAnswers.value = []
  matchAnswers.value = new Array(words.value.length).fill(null)

  detectMobile()
}

function onMatchDragStart(idx, type, event) {
  event.dataTransfer.setData('text/plain', JSON.stringify({ idx, type }))
  event.dataTransfer.effectAllowed = 'move'
}

function onMatchDragOver(event) {
  event.dataTransfer.dropEffect = 'move'
}

function onMatchDrop(meaningIdx, event) {
  if (matchSubmitted.value) return
  const data = JSON.parse(event.dataTransfer.getData('text/plain'))
  if (data.type !== 'word') return
  const wordIdx = data.idx
  // 释放该释义之前的匹配
  const prevWord = matchAnswers.value.findIndex(a => a === matchShuffledMeanings.value[meaningIdx].meaning)
  if (prevWord >= 0) {
    matchAnswers.value[prevWord] = null
  }
  // 释放该单词之前的释义
  if (matchAnswers.value[wordIdx] !== null) {
    const oldIdx = matchShuffledMeanings.value.findIndex(m => m.meaning === matchAnswers.value[wordIdx])
    if (oldIdx >= 0) matchMeaningUsed.value[oldIdx] = false
  }
  matchAnswers.value[wordIdx] = matchShuffledMeanings.value[meaningIdx].meaning
  matchMeaningUsed.value[meaningIdx] = true
  matchSelectedWord.value = -1
  matchSelectedMeaning.value = -1
  updateMatchLines()
}

function onMatchCardClick(idx, type) {
  if (matchSubmitted.value) return
  if (type === 'word') {
    if (matchAnswers.value[idx] !== null) {
      removeMatch(idx)
      return
    }
    matchSelectedWord.value = matchSelectedWord.value === idx ? -1 : idx
  } else {
    if (matchSelectedWord.value >= 0) {
      const wordIdx = matchSelectedWord.value
      if (matchAnswers.value[wordIdx] !== null) {
        const oldIdx = matchShuffledMeanings.value.findIndex(m => m.meaning === matchAnswers.value[wordIdx])
        if (oldIdx >= 0) matchMeaningUsed.value[oldIdx] = false
      }
      matchAnswers.value[wordIdx] = matchShuffledMeanings.value[idx].meaning
      matchMeaningUsed.value[idx] = true
      matchSelectedWord.value = -1
      matchSelectedMeaning.value = -1
      updateMatchLines()
    } else {
      matchSelectedMeaning.value = matchSelectedMeaning.value === idx ? -1 : idx
    }
  }
}

function removeMatch(wordIdx) {
  const meaning = matchAnswers.value[wordIdx]
  if (meaning) {
    const mIdx = matchShuffledMeanings.value.findIndex(m => m.meaning === meaning)
    if (mIdx >= 0) matchMeaningUsed.value[mIdx] = false
  }
  matchAnswers.value[wordIdx] = null
  updateMatchLines()
}

function updateMatchLines() {
  nextTick(() => {
    const lines = []
    const wordsCol = document.querySelector('.match-words-col')
    const meaningsCol = document.querySelector('.match-meanings-col')
    if (!wordsCol || !meaningsCol) { matchLines.value = []; return }
    const containerRect = wordsCol.parentElement.getBoundingClientRect()

    matchAnswers.value.forEach((answer, wordIdx) => {
      if (answer === null) return
      const wordEl = wordsCol.children[wordIdx]
      const meaningIdx = matchShuffledMeanings.value.findIndex(m => m.meaning === answer)
      if (meaningIdx < 0) return
      const meaningEl = meaningsCol.children[meaningIdx]
      if (!wordEl || !meaningEl) return

      const wRect = wordEl.getBoundingClientRect()
      const mRect = meaningEl.getBoundingClientRect()
      const correct = matchSubmitted.value ? answer === getMeaning(words.value[wordIdx]) : null
      lines.push({
        x1: wRect.right - containerRect.left,
        y1: wRect.top + wRect.height / 2 - containerRect.top,
        x2: mRect.left - containerRect.left,
        y2: mRect.top + mRect.height / 2 - containerRect.top,
        correct
      })
    })
    matchLines.value = lines
  })
}

function useMatchHint() {
  for (let i = 0; i < words.value.length; i++) {
    if (matchAnswers.value[i] === null && !matchHintedWords.value.has(i)) {
      const correctMeaning = words.value[i].meanings && words.value[i].meanings.length > 0
        ? words.value[i].meanings[0].meaning : '未知'
      const meaningIdx = matchShuffledMeanings.value.findIndex(m => m.meaning === correctMeaning)
      matchHintWordIdx.value = i
      matchHintMeaningIdx.value = meaningIdx
      matchHintedWords.value.add(i)
      matchHintUsed.value++
      return
    }
  }
}

function submitGroupMatch() {
  matchSubmitted.value = true
  matchSelectedWord.value = -1
  matchSelectedMeaning.value = -1
  matchHintWordIdx.value = -1
  matchHintMeaningIdx.value = -1

  matchSubmittedAnswers.value = [...matchAnswers.value]

  // Partial scoring: count correct matches out of total
  const totalWords = words.value.length
  const correctCount = words.value.filter((w, idx) => {
    const correctMeaning = w.meanings && w.meanings.length > 0 ? w.meanings[0].meaning : ''
    return matchAnswers.value[idx] === correctMeaning
  }).length
  const s3 = Math.round((correctCount / totalWords) * 4)

  words.value.forEach((w, idx) => {
    if (!wordScores.value[idx]) {
      wordScores.value[idx] = { wordId: w.id, s2: 0, s3, s4: 0, s5: 0, hintTotal: 0, reactionTimeMs: 0, dwellTimeMs: 0 }
    } else {
      wordScores.value[idx].s3 = s3
    }
    if (matchHintedWords.value.has(idx)) {
      wordScores.value[idx].hintTotal = (wordScores.value[idx].hintTotal || 0) + 1
    }
  })

  updateMatchLines()
}

function enterGroupPassage() {
  phase.value = 'groupPassage'
  blankSubmitted.value = false
  passageHintUsed.value = 0
  showPassageHint.value = false
  passageHintedBlanks.value = new Set()
  selectedPoolWord.value = null
  usedPoolWords.value = new Set()

  // 解析 passageData 为 segments
  if (passageData.value?.passage) {
    parsePassage(passageData.value.passage)
  } else {
    // 回退: 使用例句拼接，确保每个句子包含目标单词
    const parts = words.value.map(w => {
      const ex = w.examples?.[0]?.sentence
      if (ex && ex.toLowerCase().includes(w.word.toLowerCase())) {
        return { sentence: ex, word: w.word }
      }
      // 例句不含目标词时，用简单句
      return { sentence: `In English, ${w.word} means ${w.meanings?.[0]?.meaning || 'unknown'}.`, word: w.word }
    })
    const combined = parts.map(p => p.sentence).join(' ')
    parsePassage(combined)
  }

  blankAnswers.value = new Array(blankToWordIdx.value.length).fill('')
}

function parsePassage(text) {
  // 找到 passage 中所有单词的位置，构建 segments
  const segments = []
  let remaining = text
  let blankIdx = 0
  const wordPatterns = words.value.map(w => ({
    word: w.word,
    regex: new RegExp(`\\b${w.word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi')
  }))

  // 按位置排序匹配
  const matches = []
  for (const wp of wordPatterns) {
    let m
    wp.regex.lastIndex = 0
    while ((m = wp.regex.exec(text)) !== null) {
      matches.push({ start: m.index, end: m.index + m[0].length, word: wp.word, original: m[0] })
    }
  }
  // 优先长匹配，避免短词匹配到长词内部
  matches.sort((a, b) => (b.end - b.start) - (a.end - a.start))

  const usedWords = new Set()
  const occupied = [] // 已占用的区间
  const filteredMatches = matches.filter(m => {
    if (usedWords.has(m.word)) return false
    // 检查是否与已占用区间重叠
    const overlaps = occupied.some(o => m.start < o.end && m.end > o.start)
    if (overlaps) return false
    usedWords.add(m.word)
    occupied.push({ start: m.start, end: m.end })
    return true
  })
  // 按位置重新排序
  filteredMatches.sort((a, b) => a.start - b.start)

  // 构建 segments
  let lastEnd = 0
  for (const m of filteredMatches) {
    if (m.start > lastEnd) {
      segments.push({ type: 'text', content: text.slice(lastEnd, m.start) })
    }
    const wordObj = words.value.find(w => w.word === m.word)
    const wordIdx = wordObj ? words.value.indexOf(wordObj) : -1
    const placeholder = '_'.repeat(m.word.length)
    segments.push({ type: 'blank', wordId: wordObj?.id, word: m.word, blankIdx, wordIdx, placeholder })
    blankIdx++
    lastEnd = m.end
  }
  if (lastEnd < text.length) {
    segments.push({ type: 'text', content: text.slice(lastEnd) })
  }

  passageSegments.value = segments
  blankToWordIdx.value = segments
    .filter(s => s.type === 'blank')
    .sort((a, b) => a.blankIdx - b.blankIdx)
    .map(s => s.wordIdx)
}

function selectPoolWord(wi) {
  if (blankSubmitted.value) return
  if (usedPoolWords.value.has(wi)) return
  selectedPoolWord.value = selectedPoolWord.value === wi ? null : wi
}

function fillBlank(blankIdx) {
  if (blankSubmitted.value) return
  if (selectedPoolWord.value === null) return
  // 如果该空已有词，释放旧词
  const oldWord = blankAnswers.value[blankIdx]
  if (oldWord) {
    const oldIdx = words.value.findIndex(w => w.word.toLowerCase() === oldWord.toLowerCase())
    if (oldIdx >= 0) usedPoolWords.value.delete(oldIdx)
  }
  blankAnswers.value[blankIdx] = words.value[selectedPoolWord.value].word
  usedPoolWords.value.add(selectedPoolWord.value)
  selectedPoolWord.value = null
}

function usePassageHint() {
  if (passageHintUsed.value >= 3) return
  // 找到第一个未填的空，显示首字母提示
  const unfilled = blankAnswers.value.findIndex(a => !a)
  if (unfilled >= 0) {
    // 找到对应空的单词
    const seg = passageSegments.value.find(s => s.type === 'blank' && s.blankIdx === unfilled)
    if (seg && seg.word) {
      blankAnswers.value[unfilled] = seg.word[0]
      // 标记对应词池为已用
      const wordIdx = words.value.findIndex(w => w.word === seg.word)
      if (wordIdx >= 0) usedPoolWords.value.add(wordIdx)
    }
    passageHintIdx.value = unfilled
    showPassageHint.value = true
    passageHintUsed.value++
    passageHintedBlanks.value.add(unfilled)
  }
}

function submitGroupPassage() {
  blankSubmitted.value = true
  showPassageHint.value = false

  // Partial scoring: count correct blanks vs total blanks
  const totalBlanks = blankToWordIdx.value.length
  let correctCount = 0
  blankToWordIdx.value.forEach((wordIdx, blankIdx) => {
    const answer = (blankAnswers.value[blankIdx] || '').trim().toLowerCase()
    const w = words.value[wordIdx]
    if (w && answer === w.word.toLowerCase()) correctCount++
  })
  const s4 = totalBlanks > 0 ? Math.round((correctCount / totalBlanks) * 4) : 0

  blankToWordIdx.value.forEach((wordIdx, blankIdx) => {
    const w = words.value[wordIdx]
    if (!w) return
    if (!wordScores.value[wordIdx]) {
      wordScores.value[wordIdx] = { wordId: w.id, s2: 0, s3: 0, s4, s5: 0, hintTotal: 0, reactionTimeMs: 0, dwellTimeMs: 0 }
    } else {
      wordScores.value[wordIdx].s4 = s4
    }
    if (passageHintedBlanks.value.has(blankIdx)) {
      wordScores.value[wordIdx].hintTotal = (wordScores.value[wordIdx].hintTotal || 0) + 1
    }
  })
}

// ===== Phase 4: 逐词拼写 (Step 5) =====
function enterSpellPhase() {
  phase.value = 'spell'
  spellQueue.value = [...words.value]
  spellRetryQueue.value = []
  spellIndex.value = 0
  hintLevel.value = 0
  hintTotalAccum.value = 0
  completeSubmitted.value = false
  completeAnswer.value = ''
  buildCompleteHint()
}

function buildCompleteHint() {
  hintLevel.value = 0
  const w = spellQueue.value[spellIndex.value]
  const word = w ? w.word : ''
  completeHint.value = word[0] + '_'.repeat(Math.max(0, word.length - 1))
  completeAnswer.value = ''
  completeSubmitted.value = false
  nextTick(() => {
    if (completeInput.value) {
      const el = completeInput.value.$el?.querySelector('input') || completeInput.value
      if (el.focus) el.focus()
    }
  })
}

function showMoreHints() {
  const word = spellQueue.value[spellIndex.value]?.word || ''
  if (hintLevel.value === 0) {
    hintLevel.value = 1
    const revealLen = Math.max(2, Math.ceil(word.length / 2))
    completeHint.value = word.slice(0, revealLen) + '_'.repeat(Math.max(0, word.length - revealLen))
    hintTotalAccum.value++
  } else if (hintLevel.value === 1) {
    hintLevel.value = 2
    completeHint.value = word
    hintTotalAccum.value += 2
  }
}

function checkComplete() {
  const answer = completeAnswer.value.trim().toLowerCase()
  const currentSpellWord = spellQueue.value[spellIndex.value]
  const word = currentSpellWord ? currentSpellWord.word : ''
  const correct = answer === word.toLowerCase()
  completeSubmitted.value = true
  completeCorrect.value = correct

  let s5 = 0
  if (correct) {
    if (hintLevel.value <= 0) s5 = 4
    else if (hintLevel.value === 1) s5 = 2
    else s5 = 1
  }

  // 找到该词在 words 数组中的原始索引
  const origIdx = words.value.findIndex(w => w.id === currentSpellWord?.id)
  if (origIdx >= 0) {
    if (!wordScores.value[origIdx]) {
      wordScores.value[origIdx] = { wordId: words.value[origIdx].id, s2: 0, s3: 0, s4: 0, s5, hintTotal: hintTotalAccum.value, reactionTimeMs: 0, dwellTimeMs: 0 }
    } else {
      wordScores.value[origIdx].s5 = s5
      wordScores.value[origIdx].hintTotal += hintTotalAccum.value
    }
  }

  // 错误强化：答错的词加入重试队列
  if (!correct && currentSpellWord) {
    spellRetryQueue.value.push(currentSpellWord)
  }

  stepTimeout(() => {
    if (spellIndex.value < spellQueue.value.length - 1) {
      // 继续当前队列
      spellIndex.value++
      hintTotalAccum.value = 0
      buildCompleteHint()
    } else if (spellRetryQueue.value.length > 0) {
      // 主队列完成，开始处理重试队列
      spellQueue.value = [...spellRetryQueue.value]
      spellRetryQueue.value = []
      spellIndex.value = 0
      hintTotalAccum.value = 0
      buildCompleteHint()
    } else {
      // 全部完成
      enterSummary()
    }
  }, 800)
}

// ===== Phase 5: 组总结 (Step 6) =====
function enterSummary() {
  phase.value = 'summary'
  elapsedMs.value = Date.now() - sessionStartTime.value
}

// ===== Phase 6: 提交 =====
async function submitGroupResults() {
  submitting.value = true
  try {
    const groupResults = words.value.map(w => {
      const scores = wordScores.value.find(s => s.wordId === w.id) || {}
      return {
        word_id: w.id,
        s2_raw: scores.s2 || 0,
        s3_raw: scores.s3 || 0,
        s4_raw: scores.s4 || 0,
        s5_raw: scores.s5 || 0,
        hint_total: scores.hintTotal || 0,
        dwell_time_ms: scores.dwellTimeMs || 0
      }
    })
    await submitContextDeepGroup({ group_results: groupResults })
    sessionDone.value = true
  } catch (e) {
    ElMessage.error('提交失败，请检查网络后重试')
  }
  submitting.value = false
}

// ===== 重试薄弱词 =====
function retryWeak() {
  words.value = [...weakWords.value]
  wordScores.value = words.value.map(w => ({ wordId: w.id, s2: 0, s3: 0, s4: 0, s5: 0, hintTotal: 0, reactionTimeMs: 0, dwellTimeMs: 0 }))
  wordIndex.value = 0
  phase.value = 'learn'
  sessionDone.value = false
  sessionStartTime.value = Date.now()
  initWord()
  // 重新请求 passage
  passageDataReady.value = false
  passageLoading.value = true
  generatePassage(words.value.map(w => w.word)).then(res => {
    passageData.value = res.data
    passageDataReady.value = true
  }).catch(() => {}).finally(() => { passageLoading.value = false })
}

// ===== 加载数据 =====
async function loadWords() {
  const wordIdsParam = sessionStorage.getItem('timo_study_word_ids') || route.query.words
  if (sessionStorage.getItem('timo_study_word_ids')) sessionStorage.removeItem('timo_study_word_ids')
  loading.value = true
  try {
    let newWords
    if (wordIdsParam) {
      const ids = wordIdsParam.split(',').map(Number)
      const res = await getWordBatch(ids)
      newWords = res.data
    } else {
      // Default: fetch 10 words from review queue, fallback to word list
      try {
        const reviewRes = await getReviewQueue()
        newWords = (reviewRes.data.words || []).slice(0, 10).map(w => ({
          id: w.wordId,
          word: w.word,
          phonetic: w.phonetic,
          meanings: w.meanings || [],
          examples: w.examples || [],
          stubborn: w.stubborn
        }))
      } catch { /* fallback below */ }
      if (!newWords || newWords.length === 0) {
        const listRes = await getWordList({ page: 0, size: 10 })
        newWords = listRes.data?.content || listRes.data || []
      }
    }

    // 如果启用了"学习前先复习"，先获取复习队列并前置
    if (route.query.reviewFirst === '1') {
      try {
        const reviewRes = await getReviewQueue()
        const reviewWords = (reviewRes.data.words || []).map(w => ({
          id: w.wordId,
          word: w.word,
          phonetic: w.phonetic,
          meanings: w.meanings || [],
          examples: w.examples || [],
          stubborn: w.stubborn
        }))
        // 去重：复习词中已包含的新词不再重复
        const newWordIds = new Set(newWords.map(w => w.id))
        const uniqueReview = reviewWords.filter(w => !newWordIds.has(w.id))
        newWords = [...uniqueReview, ...newWords]
      } catch { /* 复习获取失败不影响新词学习 */ }
    }

    words.value = newWords
    wordScores.value = newWords.map(w => ({ wordId: w.id, s2: 0, s3: 0, s4: 0, s5: 0, hintTotal: 0, reactionTimeMs: 0, dwellTimeMs: 0 }))
    if (words.value.length > 0) {
      sessionStartTime.value = Date.now()
      initWord()
      // 异步请求 Agent 生成短文
      const wordTexts = words.value.map(w => w.word)
      passageLoading.value = true
      generatePassage(wordTexts).then(res => {
        passageData.value = res.data
        passageDataReady.value = true
      }).catch(() => {}).finally(() => { passageLoading.value = false })
    }
  } catch {
    words.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  agentStore.setCurrentPage('deepLearning')
  if (userStore.token) {
    loadWords()
  }
  startFatigueCheck()
})

onBeforeUnmount(() => {
  alive = false
  pendingTimeouts.forEach(id => clearTimeout(id))
  pendingTimeouts.length = 0
  window.removeEventListener('resize', updateMatchLines)
})
</script>

<style scoped>
.deep-learning { max-width: 720px; margin: 0 auto; padding: 20px; outline: none; }
.progress-bar-wrap { margin-bottom: 24px; }
.progress-bar-wrap :deep(.el-progress-bar__outer) { border-radius: var(--radius-full); background: var(--color-border-lighter); }
.progress-bar-wrap :deep(.el-progress__text) { font-weight: 800; font-size: 13px !important; color: var(--color-text-secondary); }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 0; }

.learn-area { display: flex; flex-direction: column; align-items: center; }
.step-content { display: flex; flex-direction: column; align-items: center; gap: 16px; width: 100%; }
.step-title {
  font-size: 13px; font-weight: 800; color: var(--color-blue-dark);
  text-transform: uppercase; letter-spacing: 1px;
  background: var(--color-blue-light); padding: 8px 20px; border-radius: var(--radius-full);
}
.step-desc { color: var(--color-text-muted); font-size: 14px; font-weight: 600; }

.word-card {
  width: 100%; min-height: 200px; border: 3px solid var(--color-blue);
  border-radius: var(--radius-xl);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 32px; background: #FFFFFF; box-shadow: 0 6px 0 var(--color-blue-dark);
}
.word-main { font-size: 40px; font-weight: 900; color: var(--color-text-primary); display: flex; align-items: center; gap: 12px; }
.speak-btn {
  background: none; border: 2px solid var(--color-border-lighter); border-radius: 50%;
  width: 40px; height: 40px; font-size: 20px; cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center;
}
.speak-btn:hover { background: var(--color-blue-light); border-color: var(--color-blue); transform: scale(1.1); }
.word-phonetic { font-size: 16px; color: var(--color-text-muted); margin-top: 8px; font-weight: 600; }
.meanings-box { margin-top: 16px; text-align: center; }
.meaning-line { font-size: 17px; color: var(--color-text-regular); line-height: 1.8; font-weight: 600; }
.pos { color: var(--color-blue); font-size: 14px; margin-right: 4px; font-weight: 700; }

.exam-points { display: flex; flex-direction: column; gap: 10px; width: 100%; margin-top: 16px; }
.exam-point {
  padding: 12px 16px; width: 100%;
  background: var(--color-orange-light); border-radius: var(--radius-md);
  border-left: 3px solid var(--color-orange);
}
.exam-point-label { font-size: 12px; font-weight: 800; color: var(--color-orange-dark); margin-bottom: 4px; }
.exam-source { margin-left: 8px; font-weight: 600; color: var(--color-text-muted); font-style: normal; }
.exam-point-sentence { font-size: 14px; font-weight: 600; color: var(--color-text-regular); font-style: italic; line-height: 1.6; }
.exam-point-translation { font-size: 13px; color: var(--color-text-secondary); margin-top: 4px; }

.mnemonic-box {
  margin-top: 12px; padding: 12px 16px; width: 100%;
  background: var(--color-blue-light); border-radius: var(--radius-md);
  border-left: 3px solid var(--color-blue);
}
.mnemonic-label { font-size: 12px; font-weight: 800; color: var(--color-blue-dark); margin-bottom: 4px; }
.mnemonic-text { font-size: 14px; font-weight: 600; color: var(--color-text-regular); line-height: 1.6; }

.word-display { font-size: 44px; font-weight: 900; color: var(--color-text-primary); }
.step2-sentence { font-size: 20px; line-height: 1.6; color: var(--color-text-primary); padding: 16px 24px; background: var(--color-bg-soft); border-radius: var(--radius-md); margin-bottom: 16px; text-align: center; max-width: 600px; }
.step2-sentence :deep(.highlight-word) { color: var(--color-primary); font-weight: 800; text-decoration: underline; text-underline-offset: 3px; }
.meaning-display { font-size: 28px; color: var(--color-blue-dark); }

.options-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; max-width: 480px;
}
.options-grid :deep(.option-btn) {
  text-align: center; height: 64px; padding: 10px 12px; width: 100%;
  border-radius: var(--radius-lg); font-weight: 700; font-size: 14px;
  border: 3px solid var(--color-border-lighter); box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: all 0.15s ease;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin: 0;
}
.options-grid :deep(.option-btn:hover:not(:disabled)) {
  transform: translateY(-2px); box-shadow: 0 5px 0 var(--color-border-lighter);
  border-color: var(--color-blue);
}
.options-grid :deep(.option-btn:active:not(:disabled)) { transform: translateY(2px); box-shadow: 0 1px 0 var(--color-border-lighter); }

/* Phase 2: 组匹配 */
.batch-title { font-size: 15px !important; padding: 10px 24px !important; }
.match-grid { width: 100%; max-width: 560px; display: flex; flex-direction: column; gap: 10px; }
.match-row {
  display: flex; align-items: center; gap: 16px;
  padding: 10px 16px; background: #fff; border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md); box-shadow: 0 2px 0 var(--color-border-lighter);
  transition: all 0.3s ease;
}
.match-row.correct { border-color: var(--color-primary); background: var(--color-primary-bg); }
.match-row.wrong { border-color: var(--color-red); background: var(--color-red-light); }
.match-row.hint-highlight { border-color: var(--color-orange); background: var(--color-orange-light); }
.match-word-label { font-weight: 800; font-size: 16px; min-width: 100px; color: var(--color-text-primary); }
.match-select { flex: 1; }
.match-result-icon { font-size: 20px; font-weight: 900; }
.match-row.correct .match-result-icon { color: var(--color-primary-dark); }
.match-row.wrong .match-result-icon { color: var(--color-red-dark); }

/* 匹配容器 - 桌面端 */
.match-container { position: relative; width: 100%; max-width: 700px; margin: 0 auto; }
.match-columns { display: flex; gap: 40px; justify-content: center; }
.match-col { display: flex; flex-direction: column; gap: 8px; flex: 1; max-width: 300px; }
.match-words-col { align-items: flex-end; }
.match-meanings-col { align-items: flex-start; }
.match-card {
  width: 100%; padding: 10px 16px; background: #fff; border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md); cursor: pointer; transition: all 0.2s ease;
  box-shadow: 0 2px 0 var(--color-border-lighter); user-select: none;
  font-size: 15px; text-align: center; position: relative;
}
.match-card:hover { border-color: var(--color-primary-light); transform: translateY(-1px); }
.match-card.selected { border-color: var(--color-primary); background: var(--color-primary-bg); box-shadow: 0 0 0 3px rgba(76,175,80,0.15); }
.match-card.matched { border-color: var(--color-primary); background: var(--color-primary-bg); opacity: 0.7; }
.match-card.correct { border-color: var(--color-primary); background: var(--color-primary-bg); opacity: 1; }
.match-card.wrong { border-color: var(--color-red); background: var(--color-red-light); opacity: 1; }
.match-card.hint-highlight { border-color: var(--color-orange); background: var(--color-orange-light); animation: hintPulse 1.5s ease infinite; }
.match-word-card { font-weight: 800; font-size: 16px; }
.match-meaning-card { font-size: 14px; color: var(--color-text-regular); word-break: break-word; }
.match-remove {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%; background: var(--color-red-light);
  color: var(--color-red); font-size: 12px; margin-left: 6px; cursor: pointer;
  transition: background 0.2s; vertical-align: middle;
}
.match-remove:hover { background: var(--color-red); color: #fff; }
.match-lines-svg {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  pointer-events: none; z-index: 1;
}
@keyframes hintPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255,152,0,0.3); }
  50% { box-shadow: 0 0 0 6px transparent; }
}
.match-actions { display: flex; gap: 12px; margin-top: 20px; justify-content: center; }

.hint-btn {
  border: 2px dashed var(--color-orange); background: var(--color-orange-light);
  color: var(--color-orange-dark); font-weight: 700; border-radius: var(--radius-full);
}
.hint-btn:hover { background: var(--color-orange); color: #fff; }

/* Phase 3: 组填空 */
.passage-box {
  font-size: 18px; line-height: 2; text-align: center; padding: 24px;
  background: var(--color-bg-hover); border-radius: var(--radius-lg); width: 100%; font-weight: 600;
}
.group-passage-box { min-height: 120px; }
.passage-loading {
  font-size: 14px; color: var(--color-text-muted); font-weight: 600;
  padding: 8px 16px; background: var(--color-orange-light); border-radius: var(--radius-full);
  margin-bottom: 8px; animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
.blank-input { width: 140px; margin: 0 4px; }
.blank-input :deep(.el-input__wrapper:focus-within) { box-shadow: 0 0 0 3px var(--color-primary-lighter) !important; }
.hint-highlight-input :deep(.el-input__wrapper) { box-shadow: 0 0 0 3px var(--color-orange) !important; border-color: var(--color-orange) !important; }

.passage-pool {
  display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;
  margin: 12px 0; padding: 12px; background: var(--color-bg-hover); border-radius: var(--radius-md);
  width: 100%; max-width: 560px;
}
.pool-word {
  font-weight: 800; font-size: 14px; padding: 6px 14px; background: #fff;
  border: 2px solid var(--color-border-lighter); border-radius: var(--radius-full);
  cursor: pointer; transition: all 0.2s ease; user-select: none;
}
.pool-word:hover:not(.used) { border-color: var(--color-blue); background: var(--color-blue-light); }
.pool-word.selected { border-color: var(--color-primary); background: var(--color-primary-bg); color: var(--color-primary-dark); box-shadow: 0 2px 0 var(--color-primary-dark); }
.pool-word.used { opacity: 0.4; cursor: default; text-decoration: line-through; }

.blank-slot {
  display: inline-block; min-width: 80px; padding: 2px 8px; margin: 0 3px;
  border-bottom: 3px solid var(--color-blue); font-weight: 800; font-size: inherit;
  color: var(--color-blue-dark); cursor: pointer; transition: all 0.2s ease;
  text-align: center;
}
.blank-slot.active:hover { background: var(--color-blue-light); border-radius: var(--radius-sm); }
.blank-slot.filled { border-bottom-color: var(--color-primary); color: var(--color-primary-dark); }
.blank-slot.hint-highlight-slot { background: var(--color-orange-light); border-bottom-color: var(--color-orange); border-radius: var(--radius-sm); }

.passage-blank-word-list {
  display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;
  margin: 12px 0; padding: 12px; background: var(--color-bg-hover); border-radius: var(--radius-md);
}
.passage-blank-word {
  font-weight: 800; font-size: 14px; padding: 4px 12px; background: #fff;
  border: 2px solid var(--color-border-lighter); border-radius: var(--radius-full);
}

.passage-results { width: 100%; max-width: 560px; display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.passage-result-item {
  display: flex; align-items: center; gap: 12px; padding: 8px 14px;
  border-radius: var(--radius-md); font-size: 14px; font-weight: 600;
}
.passage-result-item.correct { background: var(--color-primary-bg); border: 2px solid var(--color-primary); }
.passage-result-item.wrong { background: var(--color-red-light); border: 2px solid var(--color-red); }
.pr-word { font-weight: 800; min-width: 80px; }
.pr-answer { color: var(--color-text-secondary); }
.pr-correct { color: var(--color-red-dark); font-weight: 700; }

/* Phase 4: 拼写 */
.spell-progress { font-size: 13px; font-weight: 700; color: var(--color-text-secondary); margin-bottom: 8px; }
.complete-hint {
  font-size: 36px; letter-spacing: 8px; font-family: var(--font-mono);
  color: var(--color-text-primary); font-weight: 900;
}
.complete-input { max-width: 300px; }
.complete-input :deep(.el-input__wrapper:focus-within) { box-shadow: 0 0 0 3px var(--color-primary-lighter) !important; }
.complete-input :deep(.el-input__inner::placeholder) { color: var(--color-text-muted); font-weight: 600; opacity: 0.7; }
.complete-input-wrap { position: relative; display: inline-block; max-width: 300px; width: 100%; }
.first-letter-hint {
  position: absolute; left: 16px; top: 50%; transform: translateY(-50%);
  font-size: 16px; font-weight: 800; color: var(--color-text-muted); opacity: 0.5;
  pointer-events: none; z-index: 1;
}

.blank-result { font-size: 18px; font-weight: 800; padding: 12px 24px; border-radius: var(--radius-full); }
.blank-result.correct { color: var(--color-primary-dark); background: var(--color-primary-bg); }
.blank-result.wrong { color: var(--color-red-dark); background: var(--color-red-light); }

/* Phase 5: 总结 */
.session-done { padding-top: 20px; }
.done-card {
  max-width: 520px; margin: 0 auto; background: #FFFFFF; border: 3px solid var(--color-blue);
  border-radius: var(--radius-xl); padding: 32px; text-align: center;
  box-shadow: 0 6px 0 var(--color-blue-dark);
}
.done-card h2 { font-size: 24px; font-weight: 900; margin-bottom: 20px; }
.done-stats { display: flex; justify-content: center; gap: 24px; margin-bottom: 20px; }
.done-stat { display: flex; flex-direction: column; align-items: center; }
.done-stat-value { font-size: 28px; font-weight: 900; font-family: var(--font-mono); line-height: 1.1; }
.done-stat-value.blue { color: var(--color-blue-dark); }
.done-stat-value.green { color: var(--color-primary-dark); }
.done-stat-value.orange { color: var(--color-orange-dark); }
.done-stat-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); margin-top: 4px; }
.done-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

.weak-section {
  margin-bottom: 20px; padding: 16px; background: var(--color-orange-light);
  border-radius: var(--radius-md); border: 2px solid var(--color-orange); text-align: left;
}
.weak-title { font-size: 14px; font-weight: 800; color: var(--color-orange-dark); margin-bottom: 10px; }
.weak-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
.weak-item {
  display: flex; align-items: center; gap: 10px; padding: 6px 10px;
  background: #fff; border-radius: var(--radius-sm);
}
.weak-word { font-weight: 800; font-size: 14px; color: var(--color-text-primary); min-width: 80px; }
.weak-meaning { font-size: 13px; color: var(--color-text-secondary); }
.retry-weak-btn { width: 100%; }

/* 离线横幅 */
.offline-banner {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 16px; margin-bottom: 12px; border-radius: var(--radius-lg);
  background: var(--color-red-light); border: 2px solid var(--color-red);
}
.offline-banner-icon { font-size: 18px; }
.offline-banner-text { font-size: 14px; font-weight: 700; color: var(--color-red-dark); }

/* 疲劳提醒 */
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
</style>
