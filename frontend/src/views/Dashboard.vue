<template>
  <div class="dashboard">
    <ErrorState v-if="loadError" @retry="loadDashboard" />
    <!-- Skeleton State -->
    <template v-else-if="loading">
      <div class="greeting-section">
        <div class="greeting-row">
          <div class="greeting-text" style="flex:1;">
            <div class="skeleton-block skeleton-greeting-title"></div>
            <div class="skeleton-block skeleton-greeting-date"></div>
          </div>
          <div class="skeleton-block skeleton-greeting-fab"></div>
        </div>
      </div>
      <div class="skeleton-card">
        <div class="skeleton-row-between">
          <div class="skeleton-block skeleton-title"></div>
          <div class="skeleton-block skeleton-meta"></div>
        </div>
        <div class="skeleton-block skeleton-progress"></div>
      </div>
      <div class="skeleton-card skeleton-stats-grid">
        <div v-for="i in 4" :key="i" class="skeleton-stat">
          <div class="skeleton-block skeleton-stat-val"></div>
          <div class="skeleton-block skeleton-stat-lbl"></div>
        </div>
      </div>
      <div class="skeleton-section">
        <div class="skeleton-block skeleton-section-label"></div>
        <div class="skeleton-mode-list">
          <div v-for="i in 4" :key="i" class="skeleton-block skeleton-mode-item"></div>
        </div>
      </div>
    </template>
    <template v-else>
    <!-- Greeting -->
    <div class="greeting-section fade-in-up">
      <div class="greeting-row">
        <div class="greeting-text">
          <h1>{{ greeting }}，{{ userStore.userInfo?.nickname || '学习者' }}</h1>
          <p class="greeting-date">{{ todayStr }}</p>
          <div class="streak-inline" v-if="stats.currentStreak > 0">
            <span class="streak-dot"></span>
            <span class="streak-text">连续 {{ stats.currentStreak }} 天</span>
          </div>
        </div>
        <div class="timo-greeting">
          <TiMoFAB compact />
          <span class="timo-greeting-tip">点我帮你制定计划</span>
        </div>
      </div>
    </div>

    <!-- Daily Goal -->
    <div class="card fade-in-up fade-in-up-delay-1">
      <div class="card-header">
        <span class="card-title">每日目标</span>
        <span class="card-meta">{{ dailyCompleted }} / {{ dailyGoal }} 个单词</span>
      </div>
      <div class="progress-track">
        <div class="progress-fill" :class="{ 'animate-progress': mounted }" :style="{ width: (dailyGoal > 0 ? Math.min(100, (dailyCompleted / dailyGoal) * 100) : 0) + '%' }"></div>
      </div>
    </div>

    <!-- Stats -->
    <div class="stats-grid fade-in-up fade-in-up-delay-2">
      <div class="stat-item">
        <div class="stat-value">{{ animatedStats.mastered }}</div>
        <div class="stat-label">已掌握</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-value">{{ animatedStats.review }}</div>
        <div class="stat-label">待复习</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-value">{{ animatedStats.accuracy }}</div>
        <div class="stat-label">正确率</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-value">{{ animatedStats.streak }}</div>
        <div class="stat-label">连续</div>
      </div>
    </div>

    <!-- Weekly Insight -->
    <div class="card weekly-insight-card fade-in-up fade-in-up-delay-3" v-if="showWeeklyInsight">
      <div class="card-header">
        <span class="card-title">📊 本周洞察</span>
        <span class="card-meta">每周一更新</span>
      </div>
      <!-- Loading State -->
      <div v-if="weeklyLoading" class="weekly-skeleton">
        <div class="skeleton-row">
          <div class="skeleton-chip"></div>
          <div class="skeleton-chip"></div>
          <div class="skeleton-chip"></div>
          <div class="skeleton-chip"></div>
        </div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
      <!-- Error State -->
      <div v-else-if="weeklyError" class="weekly-error">
        <span class="weekly-error-icon">⚠️</span>
        <span>周报加载失败，请刷新重试</span>
      </div>
      <!-- Data State -->
      <template v-else-if="weeklyReport">
        <div class="weekly-stats-row">
          <div class="weekly-stat-chip">
            <span class="weekly-stat-val">{{ weeklyReport.studyDays }}</span>
            <span class="weekly-stat-lbl">学习天</span>
          </div>
          <div class="weekly-stat-chip">
            <span class="weekly-stat-val">{{ weeklyReport.totalWords ?? weeklyReport.totalStudied }}</span>
            <span class="weekly-stat-lbl">练习词</span>
          </div>
          <div class="weekly-stat-chip">
            <span class="weekly-stat-val">{{ weeklyReport.avgAccuracy }}%</span>
            <span class="weekly-stat-lbl">正确率</span>
          </div>
          <div class="weekly-stat-chip" :class="weeklyReport.accuracyDelta >= 0 ? 'delta-up' : 'delta-down'">
            <span class="weekly-stat-val">{{ weeklyReport.accuracyDelta >= 0 ? '+' : '' }}{{ weeklyReport.accuracyDelta }}%</span>
            <span class="weekly-stat-lbl">较上周</span>
          </div>
        </div>
        <div class="weekly-insight-list" v-if="weeklyReport.insights?.length">
          <div v-for="(item, idx) in weeklyReport.insights.slice(0, 2)" :key="idx" class="weekly-insight-item">
            <span class="weekly-insight-icon">{{ idx === 0 ? '📝' : '📈' }}</span>
            <span>{{ item }}</span>
          </div>
        </div>
        <div class="weekly-suggestion" v-if="weeklyReport.suggestion">
          <span class="weekly-suggestion-icon">💡</span>
          <span>{{ weeklyReport.suggestion }}</span>
        </div>
        <div class="weekly-actions">
          <button class="weekly-action-btn" @click="router.push('/stats')">
            查看完整周报 →
          </button>
        </div>
      </template>
      <!-- Empty State (Monday but no data) -->
      <div v-else class="weekly-empty">
        <div class="weekly-empty-icon">📚</div>
        <div class="weekly-empty-text">本周还没有学习记录</div>
        <div class="weekly-empty-hint">完成一次学习后，这里会展示你的周报洞察</div>
        <button class="weekly-action-btn" @click="router.push('/word-select')">
          开始学习 →
        </button>
      </div>
    </div>

    <!-- Learning Modes -->
    <div class="section fade-in-up fade-in-up-delay-3">
      <div class="section-label">学习模式</div>
      <!-- Smart Session Banner -->
      <button class="smart-session-banner" @click="openSmartSession">
        <div class="ss-banner-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          </svg>
        </div>
        <div class="ss-banner-body">
          <div class="ss-banner-title">智能时段</div>
          <div class="ss-banner-desc">告诉 TiMo 你有多少分钟，自动编排今日清单</div>
        </div>
        <div class="ss-banner-arrow">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </div>
      </button>
      <div class="mode-list">
        <div class="mode-item" v-for="(mode, i) in modes" :key="mode.name"
             :class="mode.accent ? `mode-accent-${mode.accent}` : ''"
             :style="{ animationDelay: (0.3 + i * 0.06) + 's' }"
             @click="$router.push(mode.route)">
          <div class="mode-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" v-html="mode.icon"></svg>
          </div>
          <div class="mode-body">
            <div class="mode-name">{{ mode.name }}</div>
            <div class="mode-desc">{{ mode.desc }}</div>
          </div>
          <div class="mode-arrow">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- TiMo Suggestion + Sliders -->
    <div class="card fade-in-up fade-in-up-delay-4">
      <template v-if="recommend">
      <div class="card-header">
        <div class="suggestion-label">
          <svg class="suggestion-logo" width="16" height="16" viewBox="0 0 32 32" fill="none">
            <path d="M16 2 L16 30" stroke="#7a9e7e" stroke-width="2" stroke-linecap="round"/>
            <path d="M8 8 L16 14 L24 8" stroke="#7a9e7e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M8 16 L16 22 L24 16" stroke="#a3c4a6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          </svg>
          <span>为你推荐</span>
        </div>
      </div>
      <div class="suggestion-body">
        <div class="suggestion-stats">
          <div class="suggestion-stat">
            <span class="suggestion-stat-value">{{ localNewWords }}</span>
            <span class="suggestion-stat-label">新词</span>
          </div>
          <span class="suggestion-dot">·</span>
          <div class="suggestion-stat">
            <span class="suggestion-stat-value">{{ localReviewWords }}</span>
            <span class="suggestion-stat-label">复习</span>
          </div>
          <span class="suggestion-dot">·</span>
          <div class="suggestion-stat">
            <span class="suggestion-stat-value">{{ recommend.suggestedMode === 'context_deep' ? '语境' : '快速' }}</span>
            <span class="suggestion-stat-label">模式</span>
          </div>
        </div>
        <button class="action-btn" @click="showModeDialog = true">
          开始
        </button>
      </div>
      <!-- Sliders -->
      <div class="slider-group">
        <div class="slider-row">
          <span class="slider-label">新词 {{ localNewWords }}</span>
          <el-slider v-model="localNewWords" :min="5" :max="50" :step="5" size="small" @input="onSliderChange" />
        </div>
        <div class="slider-row">
          <span class="slider-label">复习 {{ localReviewWords }}</span>
          <el-slider v-model="localReviewWords" :min="5" :max="100" :step="5" size="small" @input="onSliderChange" />
        </div>
        <div class="estimated-time">预计用时 {{ estimatedTime }} 分钟</div>
      </div>
      </template>
      <template v-else>
        <EmptyState icon="✨" title="暂无推荐" description="完成首次学习后，TiMo 会为你个性化推荐学习内容" />
      </template>
    </div>

    <!-- Forgetting Awakening -->
    <div class="card fade-in-up">
      <template v-if="nearForgottenWords.length">
      <div class="card-header">
        <span class="card-title">即将遗忘</span>
        <span class="card-meta">{{ nearForgottenWords.length }} 个单词即将过期</span>
      </div>
      <div class="forgetting-list">
        <div v-for="w in nearForgottenWords" :key="w.wordId" class="forgetting-item">
          <div class="forgetting-word">{{ w.word }}</div>
          <div class="forgetting-meta">
            <span class="forgetting-time">{{ formatReviewTime(w.nextReviewTime) }}</span>
            <el-tag size="small" type="warning">{{ Math.round(w.retrievability * 100) }}%</el-tag>
          </div>
          <button class="forgetting-action" @click="triggerAgentSuggestion(w)">唤醒</button>
        </div>
      </div>
      </template>
      <template v-else>
        <EmptyState icon="🎉" title="记忆状态良好" description="没有即将遗忘的单词，继续保持！" />
      </template>
    </div>

    <!-- Mode Selection Dialog -->
    <el-dialog v-model="showModeDialog" title="选择学习模式" width="480px" :show-close="true" class="mode-dialog">
      <div class="mode-dialog-options">
        <button class="mode-dialog-btn" :class="{ active: selectedGlobalMode === 'quick_memory' }" @click="selectedGlobalMode = 'quick_memory'">
          <span class="mode-emoji">&#x26A1;</span>
          <span class="mode-dialog-name">快速记忆</span>
          <span class="mode-dialog-desc">闪电过词</span>
        </button>
        <button class="mode-dialog-btn" :class="{ active: selectedGlobalMode === 'context_deep' }" @click="selectedGlobalMode = 'context_deep'">
          <span class="mode-emoji">&#x1F30A;</span>
          <span class="mode-dialog-name">语境深度</span>
          <span class="mode-dialog-desc">深度理解</span>
        </button>
        <button class="mode-dialog-btn" :class="{ active: selectedGlobalMode === 'unified_review' }" @click="selectedGlobalMode = 'unified_review'">
          <span class="mode-emoji">&#x1F504;</span>
          <span class="mode-dialog-name">统一复习</span>
          <span class="mode-dialog-desc">智能检测</span>
        </button>
      </div>
      <template #footer>
        <el-button @click="showModeDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmStart">开始学习</el-button>
      </template>
    </el-dialog>

    <!-- Smart Session Dialog -->
    <el-dialog v-model="showSmartSessionDialog" title="TiMo 智能编排" width="520px" :show-close="true" class="smart-dialog">
      <div class="smart-section">
        <div class="smart-section-label">可用时间</div>
        <div class="smart-time-options">
          <button
            v-for="opt in smartTimeOptions"
            :key="opt"
            class="smart-time-btn"
            :class="{ active: smartSelectedMinutes === opt }"
            @click="selectSmartMinutes(opt)">
            {{ opt }} 分钟
          </button>
        </div>
      </div>

      <div class="smart-section" v-if="smartLoading">
        <div class="smart-loading">TiMo 正在编排清单……</div>
      </div>

      <div class="smart-section" v-else-if="smartError">
        <div class="smart-error">编排失败，请稍后重试</div>
      </div>

      <template v-else-if="smartPlan">
        <div class="smart-section">
          <div class="smart-summary" v-if="smartPlan.summary">{{ smartPlan.summary }}</div>
          <div class="smart-fatigue" v-if="smartPlan.fatigueWarning">
            <span class="smart-fatigue-dot"></span>
            最近练习较多，已为你切到短时间冲刺
          </div>
        </div>
        <div class="smart-section">
          <div class="smart-section-label">推荐步骤</div>
          <div class="smart-steps">
            <div v-for="(step, idx) in smartPlan.steps" :key="idx" class="smart-step-card">
              <div class="smart-step-index">{{ idx + 1 }}</div>
              <div class="smart-step-body">
                <div class="smart-step-title">
                  <span class="smart-step-mode">{{ smartModeLabel(step.mode) }}</span>
                  <span class="smart-step-meta">{{ step.wordCount }} 词 · 约 {{ step.estimatedMinutes }} 分钟</span>
                </div>
                <div class="smart-step-reason">{{ step.reason }}</div>
              </div>
            </div>
          </div>
          <div class="smart-total">合计约 {{ smartPlan.totalEstimatedMinutes }} 分钟</div>
        </div>
      </template>

      <template #footer>
        <el-button @click="showSmartSessionDialog = false">取消</el-button>
        <el-button type="primary"
          :disabled="!smartPlan || !smartPlan.steps || smartPlan.steps.length === 0 || smartLoading"
          @click="startSmartSession">
          开始
        </el-button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useAgentStore } from '../stores/agent'
import { useExamPlanStore } from '../stores/examPlan'
import dayjs from 'dayjs'
import { getRecommend, getProgressAlert, getWeeklyReport, planSmartSession } from '../api/agent'
import { getOverview } from '../api/statistics'
import { getExamPlanStatus } from '../api/examPlan'
import { getNearForgotten } from '../api/review'
import TiMoFAB from '../components/agent/TiMoFAB.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'

const router = useRouter()
const userStore = useUserStore()
const agentStore = useAgentStore()
const examPlanStore = useExamPlanStore()
const recommend = ref(null)
const dailyGoal = ref(30)
const dailyCompleted = ref(0)
const mounted = ref(false)
const loading = ref(true)
const loadError = ref(false)
const weeklyReport = ref(null)
const weeklyLoading = ref(false)
const weeklyError = ref(false)

const todayStr = dayjs().format('YYYY年M月D日')
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '下午好'
  return '晚上好'
})
const isWeeklyInsightDay = computed(() => dayjs().day() === 1)
const showWeeklyInsight = computed(() => isWeeklyInsightDay.value && weeklyReport.value)

const modes = [
  {
    name: '快速记忆',
    desc: '快速过词，建立初步印象',
    route: '/quick-memory',
    icon: '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>'
  },
  {
    name: '语境深度',
    desc: '沉浸语境，深度理解',
    route: '/deep-learning',
    icon: '<path d="M2 12c0-3.5 3.5-7 10-7s10 3.5 10 7-3.5 7-10 7-10-3.5-10-7z"/><circle cx="12" cy="12" r="3"/>'
  },
  {
    name: '统一复习',
    desc: '智能检测，精准复习',
    route: '/review',
    icon: '<path d="M21 12a9 9 0 11-6.22-8.56"/><path d="M21 3v6h-6"/>'
  }
]

const stats = ref({
  masteredWords: 0,
  pendingReview: 0,
  avgAccuracy: 0,
  currentStreak: 0
})

const animatedStats = reactive({
  mastered: '0',
  review: '0',
  accuracy: '0%',
  streak: '0'
})

// Slider state
const localNewWords = ref(15)
const localReviewWords = ref(30)
const estimatedTime = ref(0)
let sliderTimer = null

function onSliderChange() {
  clearTimeout(sliderTimer)
  sliderTimer = setTimeout(() => {
    if (alive) estimatedTime.value = Math.round((localNewWords.value + localReviewWords.value) * 15 / 60)
  }, 300)
}

// Mode dialog
const showModeDialog = ref(false)
const selectedGlobalMode = ref('quick_memory')

// Smart Session dialog
const showSmartSessionDialog = ref(false)
const smartSelectedMinutes = ref(10)
const smartTimeOptions = [5, 10, 15, 30]
const smartPlan = ref(null)
const smartLoading = ref(false)
const smartError = ref(false)

const SMART_QUEUE_KEY = 'timo_smart_session_queue'

function smartModeLabel(mode) {
  switch (mode) {
    case 'quick_memory': return '快速记忆'
    case 'context_deep': return '语境深度'
    case 'unified_review': return '统一复习'
    default: return mode
  }
}

async function fetchSmartPlan(minutes) {
  smartLoading.value = true
  smartError.value = false
  smartPlan.value = null
  try {
    const res = await planSmartSession(minutes)
    smartPlan.value = res.data
  } catch {
    smartError.value = true
  } finally {
    smartLoading.value = false
  }
}

async function openSmartSession() {
  showSmartSessionDialog.value = true
  // Reset to default 10 min each open
  smartSelectedMinutes.value = 10
  await fetchSmartPlan(smartSelectedMinutes.value)
}

async function selectSmartMinutes(minutes) {
  smartSelectedMinutes.value = minutes
  await fetchSmartPlan(minutes)
}

function startSmartSession() {
  const plan = smartPlan.value
  if (!plan || !plan.steps || plan.steps.length === 0) return
  const [first, ...rest] = plan.steps
  // Persist remaining steps for chained flow (consumed by next step pages if they choose to).
  try {
    sessionStorage.setItem(SMART_QUEUE_KEY, JSON.stringify(rest))
  } catch {
    // sessionStorage may be unavailable (private mode) — non-fatal
  }
  showSmartSessionDialog.value = false
  if (first.navigationUrl) {
    router.push(first.navigationUrl)
  }
}

function confirmStart() {
  showModeDialog.value = false
  if (selectedGlobalMode.value === 'unified_review') {
    router.push('/review')
  } else {
    router.push({
      path: '/word-select',
      query: { newCount: localNewWords.value, reviewCount: localReviewWords.value }
    })
  }
}

// Forgetting awakening
const nearForgottenWords = ref([])

function formatReviewTime(time) {
  if (!time) return ''
  const diff = dayjs(time).diff(dayjs(), 'hour')
  if (diff < 1) return '不到1小时'
  return diff + '小时后'
}

function triggerAgentSuggestion(word) {
  agentStore.addMessage({ role: 'user', content: `帮我复习"${word.word}"` })
  if (!agentStore.isOpen) agentStore.toggleDialog()
}

let alive = true
const animTimers = []

function animateNumber(target, end, suffix = '') {
  const duration = 600
  const steps = 20
  const stepTime = duration / steps
  let current = 0
  const increment = end / steps
  const timer = setInterval(() => {
    current += increment
    if (current >= end) {
      current = end
      clearInterval(timer)
    }
    if (alive) animatedStats[target] = Math.round(current) + suffix
  }, stepTime)
  animTimers.push(timer)
}

onBeforeUnmount(() => {
  alive = false
  clearTimeout(sliderTimer)
  animTimers.forEach(t => clearInterval(t))
  animTimers.length = 0
})

async function loadDashboard() {
  loadError.value = false
  loading.value = true
  try {
    const tasks = [getRecommend(), getOverview(), getNearForgotten()]
    if (isWeeklyInsightDay.value) {
      weeklyLoading.value = true
      weeklyError.value = false
      tasks.push(getWeeklyReport())
    }
    const results = await Promise.allSettled(tasks)
    const [recRes, overviewRes, nearForgottenRes, weeklyRes] = results
    if (recRes.status === 'fulfilled') {
      recommend.value = recRes.value.data
      localNewWords.value = recRes.value.data.dailyNewWords || 15
      localReviewWords.value = recRes.value.data.dailyReviewWords || 30
      dailyGoal.value = localNewWords.value + localReviewWords.value
      estimatedTime.value = Math.round((localNewWords.value + localReviewWords.value) * 15 / 60)
    } else {
      recommend.value = { dailyNewWords: 15, dailyReviewWords: 30, suggestedMode: 'quick_memory' }
    }
    if (overviewRes.status === 'fulfilled') {
      const d = overviewRes.value.data
      stats.value = {
        masteredWords: d.masteredWords ?? 0,
        pendingReview: d.pendingReview ?? 0,
        avgAccuracy: d.avgAccuracy ?? 0,
        currentStreak: d.currentStreak ?? 0
      }
      setTimeout(() => {
        if (!alive) return
        animateNumber('mastered', stats.value.masteredWords)
        animateNumber('review', stats.value.pendingReview)
        animateNumber('accuracy', stats.value.avgAccuracy, '%')
        animateNumber('streak', stats.value.currentStreak)
      }, 400)
    }
    if (nearForgottenRes.status === 'fulfilled') {
      nearForgottenWords.value = nearForgottenRes.value.data || []
    }
    if (weeklyRes?.status === 'fulfilled') {
      weeklyReport.value = weeklyRes.value.data || null
    } else if (weeklyRes?.status === 'rejected') {
      weeklyError.value = true
    } else if (!isWeeklyInsightDay.value) {
      weeklyReport.value = null
    }
    weeklyLoading.value = false
  } catch {
    loadError.value = true
    recommend.value = { dailyNewWords: 15, dailyReviewWords: 30, suggestedMode: 'quick_memory' }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  mounted.value = true
  agentStore.setCurrentPage('dashboard')

  if (!userStore.token) {
    loading.value = false
    recommend.value = { dailyNewWords: 15, dailyReviewWords: 30, suggestedMode: 'quick_memory' }
    return
  }

  await loadDashboard()

  // Auto-trigger exam plan dialog for newly registered users
  const justRegistered = localStorage.getItem('justRegistered') === 'true'
  if (justRegistered) {
    localStorage.removeItem('justRegistered')
    agentStore.setConversationType('exam_planning')
    if (!agentStore.isOpen) agentStore.toggleDialog()
  } else if (!examPlanStore.generatedPlan && !localStorage.getItem('timo_plan_skipped')) {
    try {
      const planRes = await getExamPlanStatus()
      if (planRes.data.planReady) {
        examPlanStore.setGeneratedPlan(planRes.data.planSummary)
      } else {
        agentStore.setConversationType('exam_planning')
        if (!agentStore.isOpen) agentStore.toggleDialog()
      }
    } catch {
      agentStore.setConversationType('exam_planning')
      if (!agentStore.isOpen) agentStore.toggleDialog()
    }
  }

  // Scenario 2: Agent intervention — check if study progress is behind plan
  try {
    const alertRes = await getProgressAlert()
    if (alertRes.data?.hasAlert) {
      agentStore.addMessage({
        role: 'assistant',
        content: alertRes.data.message + (alertRes.data.suggestedAction || ''),
        actions: ['开始深度学习', '查看规划']
      })
      agentStore.setTiMoState('alert', 5000)
      if (!agentStore.isOpen) agentStore.toggleDialog()
    }
  } catch {
    // Progress alert is non-critical, silently ignore
  }
})
</script>

<style scoped>
.dashboard {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 64px;
}

/* === Skeleton (loading state) === */
.skeleton-block {
  background: linear-gradient(90deg, var(--color-bg-hover) 25%, var(--color-border-lighter) 50%, var(--color-bg-hover) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-sm);
  animation: shimmer 1.5s infinite;
}
.skeleton-greeting-title { height: 28px; width: 60%; margin-bottom: 8px; border-radius: var(--radius-sm); }
.skeleton-greeting-date { height: 14px; width: 30%; border-radius: var(--radius-sm); }
.skeleton-greeting-fab { width: 56px; height: 40px; border-radius: var(--radius-md); flex-shrink: 0; }
.skeleton-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  box-shadow: var(--shadow-stack-md);
}
.skeleton-row-between { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.skeleton-title { height: 14px; width: 80px; }
.skeleton-meta { height: 12px; width: 100px; }
.skeleton-progress { height: 4px; width: 100%; border-radius: 2px; }
.skeleton-stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; padding: 28px 20px; }
.skeleton-stat { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.skeleton-stat-val { height: 26px; width: 50%; }
.skeleton-stat-lbl { height: 12px; width: 40%; }
.skeleton-section { display: flex; flex-direction: column; gap: 12px; }
.skeleton-section-label { height: 11px; width: 80px; }
.skeleton-mode-list { display: flex; flex-direction: column; gap: 8px; }
.skeleton-mode-item { height: 68px; border-radius: var(--radius-md); }
@media (max-width: 768px) {
  .skeleton-stats-grid { grid-template-columns: 1fr 1fr; gap: 16px 0; padding: 20px 12px; }
}

/* === Greeting === */
.greeting-section { padding-top: 8px; }
.greeting-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.greeting-text { flex: 1; }
.timo-greeting { display: flex; align-items: center; gap: 6px; flex-shrink: 0; cursor: pointer; }
.timo-greeting-tip { font-size: 13px; color: var(--color-primary-dark); font-weight: 700; white-space: nowrap; }
.greeting-section h1 { font-family: var(--font-display); font-size: 26px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 4px; letter-spacing: -0.01em; }
.greeting-date { font-size: 13px; color: var(--color-text-secondary); font-weight: 600; margin-bottom: 12px; }
.streak-inline { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px; background: var(--color-primary-bg); border-radius: var(--radius-full); animation: fadeIn 0.4s ease 0.5s forwards; opacity: 0; }
.streak-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-primary); animation: pulse-soft 2.5s ease-in-out infinite; }
.streak-text { font-size: 12px; color: var(--color-primary-dark); font-weight: 500; }

/* === Card === */
.card { background: #fff; border: 2px solid var(--color-border-lighter); border-radius: var(--radius-md); padding: 20px 24px; box-shadow: 0 4px 0 var(--color-border-lighter); transition: box-shadow 0.3s ease, transform 0.3s ease; }
.card:hover { transform: translateY(-2px); box-shadow: 0 6px 0 var(--color-border-lighter); }
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-title { font-size: 14px; font-weight: 800; color: var(--color-text-primary); }
.card-meta { font-size: 12px; color: var(--color-text-secondary); font-weight: 600; }

/* === Progress === */
.progress-track { height: 4px; background: var(--color-border-lighter); border-radius: 2px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--color-primary); border-radius: 2px; transition: width 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94); width: 0; }
.progress-fill.animate-progress { transition-delay: 0.3s; }

/* === Stats === */
.stats-grid { display: flex; align-items: center; justify-content: center; gap: 0; background: #fff; border: 2px solid var(--color-border-lighter); border-radius: var(--radius-md); padding: 28px 20px; box-shadow: 0 4px 0 var(--color-border-lighter); transition: box-shadow 0.3s ease; }
.stats-grid:hover { transform: translateY(-2px); box-shadow: 0 6px 0 var(--color-border-lighter); }
.stat-item { flex: 1; text-align: center; padding: 0 8px; }
.stat-value { font-size: 26px; font-weight: 900; color: var(--color-text-primary); font-family: var(--font-mono); line-height: 1.2; margin-bottom: 4px; }
.stat-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); letter-spacing: 0.03em; }
.stat-divider { width: 1px; height: 36px; background: var(--color-border-lighter); flex-shrink: 0; }

/* === Weekly Insight === */
.weekly-insight-card { border-left: 3px solid var(--color-primary); }
.weekly-stats-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.weekly-stat-chip {
  flex: 1;
  min-width: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 6px;
  background: var(--color-bg-hover);
  border-radius: var(--radius-md);
  transition: transform 0.2s ease;
}
.weekly-stat-chip:hover { transform: translateY(-1px); }
.weekly-stat-val {
  font-size: 18px;
  font-weight: 900;
  font-family: var(--font-mono);
  color: var(--color-text-primary);
  line-height: 1.2;
}
.weekly-stat-lbl {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin-top: 2px;
}
.delta-up .weekly-stat-val { color: var(--color-success); }
.delta-down .weekly-stat-val { color: var(--color-danger); }
.weekly-insight-list { display: flex; flex-direction: column; gap: 8px; }
.weekly-insight-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-bg-hover);
  border-radius: var(--radius-md);
  color: var(--color-text-regular);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
}
.weekly-insight-icon { flex-shrink: 0; font-size: 14px; }
.weekly-suggestion {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.6;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.weekly-suggestion-icon { flex-shrink: 0; font-size: 14px; }
.weekly-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.weekly-action-btn {
  padding: 8px 18px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: var(--font-family);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.weekly-action-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
/* Skeleton */
.weekly-skeleton { display: flex; flex-direction: column; gap: 12px; }
.skeleton-row { display: flex; gap: 8px; }
.skeleton-chip {
  flex: 1;
  height: 52px;
  background: linear-gradient(90deg, var(--color-bg-hover) 25%, var(--color-border-lighter) 50%, var(--color-bg-hover) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-md);
  animation: shimmer 1.5s infinite;
}
.skeleton-line {
  height: 14px;
  background: linear-gradient(90deg, var(--color-bg-hover) 25%, var(--color-border-lighter) 50%, var(--color-bg-hover) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-sm);
  animation: shimmer 1.5s infinite;
}
.skeleton-line.short { width: 60%; }
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
/* Error */
.weekly-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #FEF0F0;
  border-radius: var(--radius-md);
  color: var(--color-danger, #F56C6C);
  font-size: 13px;
  font-weight: 600;
}
.weekly-error-icon { font-size: 16px; }
/* Empty */
.weekly-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  text-align: center;
}
.weekly-empty-icon { font-size: 32px; }
.weekly-empty-text {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-text-primary);
}
.weekly-empty-hint {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

/* === Section === */
.section-label { font-size: 11px; font-weight: 700; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 12px; }

/* === Smart Session Banner === */
.smart-session-banner {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  margin-bottom: 12px;
  background: var(--color-primary);
  color: #fff;
  border: 2px solid var(--color-primary-dark, #5a7d5e);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-family: var(--font-family);
  box-shadow: 0 4px 0 var(--color-primary-dark, #5a7d5e);
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}
.smart-session-banner:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 0 var(--color-primary-dark, #5a7d5e);
}
.smart-session-banner:active {
  transform: translateY(1px);
  box-shadow: 0 3px 0 var(--color-primary-dark, #5a7d5e);
}
.ss-banner-icon {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.ss-banner-body { flex: 1; text-align: left; }
.ss-banner-title { font-size: 15px; font-weight: 800; letter-spacing: 0.01em; }
.ss-banner-desc { font-size: 12px; font-weight: 600; opacity: 0.92; margin-top: 2px; }
.ss-banner-arrow { flex-shrink: 0; opacity: 0.85; }

/* === Smart Session Dialog === */
.smart-dialog :deep(.el-dialog) { border-radius: var(--radius-xl); }
.smart-section { margin-bottom: 16px; }
.smart-section:last-child { margin-bottom: 0; }
.smart-section-label { font-size: 12px; font-weight: 800; color: var(--color-text-secondary); margin-bottom: 8px; letter-spacing: 0.04em; }
.smart-time-options { display: flex; gap: 8px; flex-wrap: wrap; }
.smart-time-btn {
  flex: 1;
  min-width: 70px;
  padding: 10px 14px;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-sm);
  background: #fff;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 0 var(--color-border-lighter);
}
.smart-time-btn:hover { background: var(--color-primary-bg); border-color: var(--color-primary-lighter); }
.smart-time-btn.active {
  background: var(--color-primary-bg);
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
  box-shadow: 0 2px 0 var(--color-primary-dark, #5a7d5e);
}
.smart-loading { padding: 14px; text-align: center; color: var(--color-text-muted); font-size: 13px; font-weight: 600; }
.smart-error { padding: 12px; background: #FEF0F0; color: var(--color-danger, #F56C6C); font-size: 13px; font-weight: 600; border-radius: var(--radius-md); text-align: center; }
.smart-summary {
  padding: 10px 12px;
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}
.smart-fatigue {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  background: #FFF7E6;
  color: #B26A00;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
}
.smart-fatigue-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #FFA200;
}
.smart-steps { display: flex; flex-direction: column; gap: 8px; }
.smart-step-card {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  background: var(--color-bg-page);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
}
.smart-step-index {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  background: var(--color-primary);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}
.smart-step-body { flex: 1; }
.smart-step-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.smart-step-mode { font-size: 14px; font-weight: 800; color: var(--color-text-primary); }
.smart-step-meta { font-size: 11px; color: var(--color-text-muted); font-weight: 600; font-family: var(--font-mono); }
.smart-step-reason { font-size: 12px; color: var(--color-text-secondary); font-weight: 600; margin-top: 2px; line-height: 1.5; }
.smart-total {
  margin-top: 10px;
  text-align: right;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-muted);
}

/* === Mode List === */
.mode-list { display: flex; flex-direction: column; gap: 8px; }
.mode-item { display: flex; align-items: center; gap: 14px; padding: 16px 20px; background: #fff; border: 2px solid var(--color-border-lighter); border-radius: var(--radius-md); cursor: pointer; transition: background 0.25s ease, transform 0.25s ease, box-shadow 0.25s ease; animation: fadeInUp 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards; opacity: 0; box-shadow: 0 3px 0 var(--color-border-lighter); }
.mode-item:hover { background: var(--color-bg-hover); transform: translateX(4px); box-shadow: 0 5px 0 var(--color-border-lighter); }
.mode-icon { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; color: var(--color-primary); flex-shrink: 0; transition: transform 0.25s ease; }
.mode-item:hover .mode-icon { transform: scale(1.08); }
.mode-body { flex: 1; }
.mode-name { font-size: 14px; font-weight: 800; color: var(--color-text-primary); margin-bottom: 1px; }
.mode-desc { font-size: 12px; color: var(--color-text-secondary); font-weight: 600; }
.mode-arrow { color: var(--color-text-muted); flex-shrink: 0; opacity: 0; transform: translateX(-4px); transition: opacity 0.25s ease, transform 0.25s ease; }
.mode-item:hover .mode-arrow { opacity: 1; transform: translateX(0); }

/* === Suggestion === */
.suggestion-label { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 800; color: var(--color-text-regular); }
.suggestion-logo { animation: breathe 3s ease-in-out infinite; }
.suggestion-body { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.suggestion-stats { display: flex; align-items: center; gap: 16px; }
.suggestion-stat { display: flex; flex-direction: column; align-items: center; }
.suggestion-stat-value { font-size: 20px; font-weight: 900; color: var(--color-text-primary); font-family: var(--font-mono); line-height: 1.2; }
.suggestion-stat-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); }
.suggestion-dot { color: var(--color-text-muted); font-size: 18px; }
.action-btn { padding: 10px 24px; border: 2px solid var(--color-primary); border-radius: var(--radius-sm); background: transparent; color: var(--color-primary-dark); font-family: var(--font-family); font-size: 14px; font-weight: 800; cursor: pointer; transition: all 0.25s ease; }
.action-btn:hover { background: var(--color-primary); color: #fff; box-shadow: 0 2px 8px rgba(122, 158, 126, 0.25); }
.action-btn:active { transform: scale(0.97); }

/* === Sliders === */
.slider-group { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--color-border-lighter); }
.slider-row { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.slider-label { font-size: 12px; font-weight: 700; color: var(--color-text-secondary); min-width: 80px; white-space: nowrap; }
.slider-row :deep(.el-slider) { flex: 1; }
.estimated-time { font-size: 12px; color: var(--color-primary-dark); font-weight: 600; text-align: right; margin-top: 4px; }

/* === Forgetting === */
.forgetting-list { display: flex; flex-direction: column; gap: 8px; }
.forgetting-item { display: flex; align-items: center; gap: 12px; padding: 10px 14px; background: var(--color-bg-page); border-radius: var(--radius-md); transition: background 0.2s; }
.forgetting-item:hover { background: var(--color-bg-hover); }
.forgetting-word { font-weight: 800; font-size: 14px; color: var(--color-text-primary); min-width: 80px; }
.forgetting-meta { flex: 1; display: flex; align-items: center; gap: 8px; }
.forgetting-time { font-size: 12px; color: var(--color-text-muted); }
.forgetting-action { padding: 4px 12px; border-radius: var(--radius-full); border: 1px solid var(--color-primary); background: transparent; color: var(--color-primary-dark); font-size: 11px; font-weight: 600; cursor: pointer; transition: all 0.2s; font-family: var(--font-family); }
.forgetting-action:hover { background: var(--color-primary); color: #fff; }

/* === Mode Dialog === */
.mode-dialog :deep(.el-dialog) { border-radius: var(--radius-xl); }
.mode-dialog-options { display: flex; gap: 10px; }
.mode-dialog-btn { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 16px 8px; border: 2px solid var(--color-border-lighter); border-radius: var(--radius-md); background: #fff; cursor: pointer; transition: all 0.2s; font-family: var(--font-family); box-shadow: 0 3px 0 var(--color-border-lighter); }
.mode-dialog-btn:hover { border-color: var(--color-primary-lighter); background: var(--color-primary-bg); }
.mode-dialog-btn.active { border-color: var(--color-primary); background: var(--color-primary-bg); box-shadow: 0 3px 0 var(--color-primary-dark); }
.mode-emoji { font-size: 24px; }
.mode-dialog-name { font-size: 13px; font-weight: 800; color: var(--color-text-primary); }
.mode-dialog-desc { font-size: 11px; font-weight: 600; color: var(--color-text-muted); }

@media (max-width: 768px) {
  .stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px 0;
    padding: 20px 12px;
  }
  .stat-divider { display: none; }
  .stat-item { width: auto; margin-bottom: 0; }
  .suggestion-body { flex-direction: column; gap: 16px; align-items: stretch; }
  .suggestion-stats { justify-content: center; }
  .greeting-row { flex-direction: column; gap: 12px; }
  .timo-greeting { align-self: flex-start; }
}
</style>
