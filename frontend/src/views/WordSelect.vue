<template>
  <div class="word-select fade-in-up">
    <!-- 今日学习配额横幅 -->
    <div class="quota-banner" :class="{ 'no-plan': !quota?.hasActivePlan }">
      <template v-if="quota?.hasActivePlan">
        <div class="quota-row">
          <div class="quota-block">
            <span class="quota-label">&#x1F4D6; 今日新词</span>
            <span class="quota-value">{{ quota.todayNewWordsLearned }} / {{ quota.dailyNewWordsTarget }}</span>
            <el-progress
              :percentage="newWordsPct"
              :stroke-width="10"
              :show-text="false"
              :color="newProgressColor"
              class="quota-progress"
            />
            <span class="quota-remain" :class="{ exceeded: quota.newWordsRemaining === 0 }">
              {{ quota.newWordsRemaining === 0 ? '已达上限' : `剩 ${quota.newWordsRemaining}` }}
            </span>
          </div>
          <div class="quota-block">
            <span class="quota-label">&#x1F504; 今日复习</span>
            <span class="quota-value">{{ quota.todayReviewsCompleted }} / {{ quota.dailyReviewWordsTarget }}</span>
            <el-progress
              :percentage="reviewsPct"
              :stroke-width="10"
              :show-text="false"
              :color="reviewProgressColor"
              class="quota-progress"
            />
            <span class="quota-remain" :class="{ exceeded: quota.reviewsRemaining === 0 }">
              {{ quota.reviewsRemaining === 0 ? '已达上限' : `剩 ${quota.reviewsRemaining}` }}
            </span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="no-plan-row">
          <span class="no-plan-text">&#x1F4DD; 你还没有备考规划，无法评估每日学习量</span>
          <el-button size="small" type="primary" @click="goToExamPlan">前往规划</el-button>
        </div>
      </template>
    </div>

    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <h1>&#x1F4DA; 选择要学习的单词</h1>
        <p>从词库中选择单词，开始今日学习</p>
      </div>
      <div class="header-stats">
        <div class="stat-pill">
          <span class="stat-num">{{ total }}</span>
          <span class="stat-text">可用单词</span>
        </div>
        <div class="stat-pill selected">
          <span class="stat-num">{{ selectedIds.length }}</span>
          <span class="stat-text">已选择</span>
        </div>
      </div>
    </div>

    <div class="main-layout">
      <!-- 左侧筛选 -->
      <aside class="filter-panel fade-in-up fade-in-up-delay-1">
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-icon">&#x1F50D;</span>
            <span class="panel-title">筛选条件</span>
          </div>

          <div class="search-box">
            <el-input v-model="search" placeholder="搜索单词..." prefix-icon="Search" clearable @input="onSearchInput" />
          </div>

          <div class="filter-group">
            <p class="filter-label">&#x1F4DD; 考试类型</p>
            <div class="tag-grid">
              <button
                v-for="t in examTypeOptions"
                :key="t.value"
                class="filter-tag"
                :class="{ active: examTypes.includes(t.value) }"
                @click="toggleExamType(t.value)"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <div class="filter-group">
            <p class="filter-label">&#x1F504; 熟悉度</p>
            <div class="tag-grid">
              <button class="filter-tag" :class="{ active: familiarityFilter === '' }" @click="setFamiliarityFilter('')">全部</button>
              <button class="filter-tag" :class="{ active: familiarityFilter === 'unlearned' }" @click="setFamiliarityFilter('unlearned')">未学习</button>
              <button class="filter-tag" :class="{ active: familiarityFilter === 'learning' }" @click="setFamiliarityFilter('learning')">学习中</button>
              <button class="filter-tag" :class="{ active: familiarityFilter === 'mastered' }" @click="setFamiliarityFilter('mastered')">已掌握</button>
            </div>
          </div>

          <div class="filter-group">
            <p class="filter-label">&#x1F4DD; 词性</p>
            <div class="tag-grid">
              <button
                v-for="p in posOptions"
                :key="p.value"
                class="filter-tag"
                :class="{ active: posFilter === p.value }"
                @click="togglePosFilter(p.value)"
              >
                {{ p.label }}
              </button>
            </div>
          </div>

          <div class="filter-actions">
            <el-button text @click="selectAllPage">全选当前页</el-button>
            <el-button text @click="invertSelection">反选</el-button>
            <el-button text @click="selectUnmastered">仅选未掌握</el-button>
            <el-button text @click="clearSelection">清空选择</el-button>
          </div>
        </div>
      </aside>

      <!-- 中间单词列表 -->
      <main class="word-list-panel fade-in-up fade-in-up-delay-2">
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-icon">&#x1F4D6;</span>
            <span class="panel-title">单词列表</span>
            <span class="word-count-badge">{{ total }} 个</span>
          </div>

          <el-table
            ref="tableRef"
            v-loading="loading"
            :data="wordList"
            @selection-change="onSelectionChange"
            stripe
            height="520"
            row-key="id"
          >
            <el-table-column type="selection" width="45" reserve-selection />
            <el-table-column prop="word" label="单词" width="150">
              <template #default="{ row }">
                <span class="word-cell">{{ row.word }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="phonetic" label="音标" width="130">
              <template #default="{ row }">
                <span v-if="row.phonetic" class="phonetic">{{ row.phonetic }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="释义" min-width="220">
              <template #default="{ row }">
                <span v-if="row.meanings && row.meanings.length">
                  {{ row.meanings[0].partOfSpeech ? row.meanings[0].partOfSpeech + '. ' : '' }}{{ row.meanings[0].meaning }}
                </span>
                <span v-else class="text-muted">暂无释义</span>
              </template>
            </el-table-column>
            <el-table-column prop="examType" label="考试" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="examTagType(row.examType)">{{ row.examType }}</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              @current-change="loadWords"
            />
          </div>
        </div>
      </main>

      <!-- 右侧已选+开始 -->
      <aside class="selected-panel fade-in-up fade-in-up-delay-3">
        <div class="panel-card selected-card">
          <div class="timo-mini-section">
            <TiMoFAB compact />
            <span class="timo-mini-hint">需要帮助？点击我</span>
          </div>
          <div class="panel-header">
            <span class="panel-icon">&#x2705;</span>
            <span class="panel-title">已选清单</span>
          </div>

          <div class="selected-count">
            <span class="count-num">{{ selectedIds.length }}</span>
            <span class="count-label">个单词</span>
          </div>

          <div class="selected-list">
            <div v-if="!selectedIds.length" class="empty-selected">
              <div class="empty-icon">&#x1F447;</div>
              <p>从左侧选择单词</p>
            </div>
            <el-tag
              v-for="w in selectedWordsPreview"
              :key="w.id"
              closable
              size="small"
              @close="removeWord(w.id)"
            >
              {{ w.word }}
            </el-tag>
          </div>

          <div class="mode-section">
            <p class="filter-label">&#x1F3AF; 学习模式</p>
            <div class="mode-options">
              <button
                class="mode-option"
                :class="{ active: studyMode === 'quick_memory' }"
                @click="studyMode = 'quick_memory'"
              >
                <span class="mode-emoji">&#x26A1;</span>
                <span class="mode-name">快速记忆</span>
                <span class="mode-desc">闪电过词</span>
              </button>
              <button
                class="mode-option"
                :class="{ active: studyMode === 'context_deep' }"
                @click="studyMode = 'context_deep'"
              >
                <span class="mode-emoji">&#x1F30A;</span>
                <span class="mode-name">语境深度</span>
                <span class="mode-desc">深度理解</span>
              </button>
            </div>
            <label v-if="studyMode === 'context_deep'" class="review-first-label">
              <el-checkbox v-model="reviewFirst" /> 学习前先复习到期单词
            </label>
          </div>

          <div class="recommend-section" v-if="recommendedWords.length">
            <div class="recommend-header">
              <p class="filter-label">&#x1F916; TiMo 推荐薄弱词</p>
              <el-button text size="small" @click="adoptRecommended">一键采纳</el-button>
            </div>
            <div class="recommend-list">
              <div v-for="w in recommendedWords" :key="w.id" class="recommend-item">
                <span class="recommend-word">{{ w.word }}</span>
                <span class="recommend-meaning">{{ w.meanings?.[0]?.meaning || '' }}</span>
              </div>
            </div>
          </div>
          <div class="recommend-section" v-else-if="recommendLoading">
            <p class="filter-label">&#x1F916; TiMo 推荐中...</p>
          </div>
          <div class="recommend-section recommend-empty" v-else>
            <p class="recommend-empty-title">暂无薄弱词推荐</p>
            <p class="recommend-empty-sub">完成更多练习后，TiMo 会帮你找到薄弱环节</p>
          </div>

          <!-- 超额提示（只针对 quick_memory + 有 active plan） -->
          <div v-if="overQuotaHint" class="quota-hint" :class="{ 'quota-hint-block': quotaBlockMode }">
            <span class="quota-hint-icon">&#x26A0;&#xFE0F;</span>
            <span>{{ overQuotaHint }}</span>
          </div>

          <el-button
            type="primary"
            class="start-btn"
            :class="{ 'start-btn-warn': isOverQuota && !quotaBlockMode }"
            :disabled="!selectedIds.length"
            @click="startLearning"
          >
            {{ startBtnLabel }}
          </el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getWordList, searchWords, getWordBatch } from '../api/words'
import { getRecommend } from '../api/agent'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import { useExamPlanStore } from '../stores/examPlan'
import { storeToRefs } from 'pinia'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const router = useRouter()
const agentStore = useAgentStore()
const userStore = useUserStore()
const examPlanStore = useExamPlanStore()
const { quota } = storeToRefs(examPlanStore)

const tableRef = ref(null)
const search = ref('')
const examTypes = ref([])
const familiarityFilter = ref('')
const wordList = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)

const selectedIds = ref([])
const selectedMap = ref({})
const studyMode = ref('quick_memory')
const reviewFirst = ref(false)
const posFilter = ref('')
const recommendedWords = ref([])
const recommendLoading = ref(false)

const posOptions = [
  { label: 'n.', value: 'n.' },
  { label: 'v.', value: 'v.' },
  { label: 'adj.', value: 'adj.' },
  { label: 'adv.', value: 'adv.' },
  { label: 'prep.', value: 'prep.' },
  { label: 'conj.', value: 'conj.' },
  { label: 'pron.', value: 'pron.' },
  { label: 'det.', value: 'det.' }
]

import { examTypeOptions } from '../constants/examTypes'

const selectedWordsPreview = computed(() => {
  return Object.values(selectedMap.value).slice(0, 50)
})

// ====== 配额相关 ======
const newWordsPct = computed(() => {
  const q = quota.value
  if (!q?.hasActivePlan || !q.dailyNewWordsTarget) return 0
  return Math.min(100, Math.round((q.todayNewWordsLearned / q.dailyNewWordsTarget) * 100))
})
const reviewsPct = computed(() => {
  const q = quota.value
  if (!q?.hasActivePlan || !q.dailyReviewWordsTarget) return 0
  return Math.min(100, Math.round((q.todayReviewsCompleted / q.dailyReviewWordsTarget) * 100))
})
const newProgressColor = computed(() => {
  const pct = newWordsPct.value
  if (pct >= 100) return '#67c23a'
  if (pct >= 60) return '#409eff'
  return '#909399'
})
const reviewProgressColor = computed(() => {
  const pct = reviewsPct.value
  if (pct >= 100) return '#67c23a'
  if (pct >= 60) return '#409eff'
  return '#909399'
})

// 当前是否处于"超额选词"状态（仅 quick_memory 模式 + 有计划 才会触发警告）
const isOverQuota = computed(() => {
  const q = quota.value
  if (!q?.hasActivePlan) return false
  if (studyMode.value !== 'quick_memory') return false
  if (q.newWordsRemaining < 0) return false // 安全防御
  return selectedIds.value.length > q.newWordsRemaining
})
// newWordsRemaining===0 → 引导跳转复习
const quotaBlockMode = computed(() => {
  const q = quota.value
  return q?.hasActivePlan && studyMode.value === 'quick_memory' && q.newWordsRemaining === 0
})
const overQuotaHint = computed(() => {
  if (!isOverQuota.value && !quotaBlockMode.value) return ''
  if (quotaBlockMode.value) return '今日新词配额已用完，建议改做复习'
  const q = quota.value
  return `已选 ${selectedIds.value.length} 个新词，超过今日计划剩余 ${q.newWordsRemaining}。建议改做复习或减少选词。`
})
const startBtnLabel = computed(() => {
  if (quotaBlockMode.value) return '今日新词已达计划上限，建议复习 \u{1F504}'
  if (isOverQuota.value) return '继续（已超计划） \u{1F680}'
  return '开始学习 \u{1F680}'
})

function toggleExamType(type) {
  const idx = examTypes.value.indexOf(type)
  if (idx >= 0) {
    examTypes.value.splice(idx, 1)
  } else {
    examTypes.value.push(type)
  }
  loadWords()
}

function setFamiliarityFilter(val) {
  familiarityFilter.value = val
  page.value = 1
  loadWords()
}

function togglePosFilter(val) {
  posFilter.value = posFilter.value === val ? '' : val
  page.value = 1
  loadWords()
}

function invertSelection() {
  const currentIds = new Set(selectedIds.value)
  wordList.value.forEach(w => {
    tableRef.value.toggleRowSelection(w, !currentIds.has(w.id))
  })
}

function selectUnmastered() {
  wordList.value.forEach(w => {
    if (!selectedIds.value.includes(w.id)) {
      tableRef.value.toggleRowSelection(w, true)
    }
  })
}

async function fetchRecommendations() {
  recommendLoading.value = true
  try {
    const res = await getRecommend()
    const recs = res.data || []
    const wordIds = recs.map(r => r.wordId || r.id).filter(Boolean)
    if (!wordIds.length) { recommendedWords.value = []; return }
    const batchRes = await getWordBatch(wordIds)
    recommendedWords.value = batchRes.data || []
  } catch {
    recommendedWords.value = []
  } finally {
    recommendLoading.value = false
  }
}

function adoptRecommended() {
  recommendedWords.value.forEach(w => {
    if (!selectedMap.value[w.id]) {
      const row = wordList.value.find(r => r.id === w.id)
      if (row) {
        tableRef.value.toggleRowSelection(row, true)
      } else {
        selectedMap.value[w.id] = w
        selectedIds.value = [...selectedIds.value, w.id]
      }
    }
  })
}

let searchTimer = null
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadWords()
  }, 300)
}

async function loadWords() {
  loading.value = true
  try {
    if (search.value.trim()) {
      const res = await searchWords(search.value.trim())
      wordList.value = res.data
      total.value = res.data.length
    } else {
      const params = { page: page.value - 1, size: pageSize }
      if (examTypes.value.length === 1) {
        params.examType = examTypes.value[0]
      }
      if (posFilter.value) {
        params.pos = posFilter.value
      }
      if (familiarityFilter.value) {
        params.familiarity = familiarityFilter.value
      }
      const res = await getWordList(params)
      wordList.value = res.data.content
      total.value = res.data.totalElements
    }
  } catch {
    wordList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  const newMap = {}
  rows.forEach(w => { newMap[w.id] = w })
  selectedMap.value = newMap
  selectedIds.value = rows.map(w => w.id)
}

function selectAllPage() {
  wordList.value.forEach(w => {
    tableRef.value.toggleRowSelection(w, true)
  })
}

function clearSelection() {
  tableRef.value.clearSelection()
}

function removeWord(id) {
  const word = selectedMap.value[id]
  if (word) {
    tableRef.value.toggleRowSelection(word, false)
  }
}

function examTagType(type) {
  const map = { '高考': 'success', 'CET4': 'success', 'CET6': '', '考研': 'warning' }
  return map[type] || 'info'
}

function startLearning() {
  if (!selectedIds.value.length) return
  // 配额已用完且当前是 quick_memory：直接导航到 ReviewMode
  if (quotaBlockMode.value) {
    router.push('/review')
    return
  }
  const wordIds = selectedIds.value.join(',')
  sessionStorage.setItem('timo_study_word_ids', wordIds)
  if (studyMode.value === 'quick_memory') {
    router.push('/quick-memory')
  } else {
    router.push({ path: '/deep-learning', query: reviewFirst.value ? { reviewFirst: '1' } : {} })
  }
}

function goToExamPlan() {
  router.push('/exam-plan')
}

onMounted(() => {
  agentStore.setCurrentPage('wordSelect')
  if (userStore.token) {
    loadWords()
    fetchRecommendations()
    examPlanStore.fetchQuota()
  }
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.word-select {
  width: 100%;
}

/* ====== 今日配额横幅 ====== */
.quota-banner {
  margin-bottom: 16px;
  padding: 14px 20px;
  background: linear-gradient(135deg, #e8f5ff, #fff8e1);
  border: 2px solid var(--color-primary-lighter);
  border-radius: var(--radius-lg);
  box-shadow: 0 3px 0 var(--color-border-lighter);
}

.quota-banner.no-plan {
  background: linear-gradient(135deg, #fff4e6, #fffaf0);
  border-color: #ffcc80;
}

.quota-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.quota-block {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  align-items: center;
  gap: 10px;
}

.quota-label {
  font-size: 13px;
  font-weight: 800;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.quota-value {
  font-size: 13px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--color-primary-dark);
  white-space: nowrap;
}

.quota-progress { min-width: 60px; }

.quota-remain {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.quota-remain.exceeded {
  color: #67c23a;
}

.no-plan-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.no-plan-text {
  font-size: 14px;
  font-weight: 700;
  color: #e6700f;
}

/* ====== 超额提示 ====== */
.quota-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fff8e1;
  border: 1.5px solid #ffc107;
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 700;
  color: #b26a00;
  line-height: 1.45;
}

.quota-hint-block {
  background: #ffebee;
  border-color: #e57373;
  color: #c62828;
}

.quota-hint-icon { flex-shrink: 0; }

.start-btn-warn {
  background: #ff9800 !important;
  border-color: #ff9800 !important;
}

.start-btn-warn:hover {
  background: #fb8c00 !important;
  border-color: #fb8c00 !important;
}

/* ====== 页面标题 ====== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 24px 28px;
  background: linear-gradient(135deg, var(--color-primary-bg), var(--color-blue-light));
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-primary-lighter);
}

.header-content h1 {
  font-size: 24px;
  font-weight: 900;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.header-content p {
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.header-stats {
  display: flex;
  gap: 12px;
}

.stat-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 20px;
  background: #FFFFFF;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 3px 0 var(--color-border-lighter);
}

.stat-pill.selected {
  border-color: var(--color-primary);
  box-shadow: 0 3px 0 var(--color-primary-dark);
}

.stat-num {
  font-size: 24px;
  font-weight: 900;
  font-family: var(--font-mono);
  color: var(--color-text-primary);
  line-height: 1;
}

.stat-pill.selected .stat-num {
  color: var(--color-primary-dark);
}

.stat-text {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

/* ====== 主布局 ====== */
.main-layout {
  display: grid;
  grid-template-columns: 290px 1fr 280px;
  gap: 20px;
  align-items: start;
}

/* ====== 面板通用 ====== */
.panel-card {
  background: #FFFFFF;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: 0 4px 0 var(--color-border-lighter);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--color-border-lighter);
}

.panel-icon {
  font-size: 18px;
}

.panel-title {
  font-weight: 900;
  font-size: 15px;
  color: var(--color-text-primary);
  flex: 1;
}

/* ====== 筛选面板 ====== */
.search-box {
  margin-bottom: 16px;
}

.filter-group {
  margin-bottom: 16px;
}

.filter-label {
  font-size: 12px;
  font-weight: 800;
  color: var(--color-blue);
  margin-bottom: 8px;
}

.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-tag {
  padding: 6px 12px;
  border-radius: var(--radius-full);
  border: 2px solid var(--color-border-lighter);
  background: #FFFFFF;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-family);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
  box-shadow: 0 2px 0 var(--color-border-lighter);
}

.filter-tag:hover {
  border-color: var(--color-primary-lighter);
  background: var(--color-primary-bg);
}

.filter-tag.active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 2px 0 var(--color-primary-dark);
}

.filter-tag:active {
  transform: translateY(1px);
  box-shadow: 0 1px 0 var(--color-border-lighter);
}

.filter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-lighter);
}

/* ====== 单词列表面板 ====== */
.word-count-badge {
  font-size: 12px;
  font-weight: 800;
  color: var(--color-primary);
  background: var(--color-primary-bg);
  padding: 4px 10px;
  border-radius: var(--radius-full);
}

.word-cell {
  font-weight: 700;
  color: var(--color-text-primary);
}

.phonetic { color: var(--color-text-muted); font-size: 13px; }
.text-muted { color: var(--color-text-secondary); }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }

/* ====== 已选面板 ====== */
.selected-count {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}

.count-num {
  font-size: 32px;
  font-weight: 900;
  font-family: var(--font-mono);
  color: var(--color-primary-dark);
  line-height: 1;
}

.count-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.selected-list {
  min-height: 100px;
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--color-bg-page);
  border-radius: var(--radius-md);
}

.selected-list :deep(.el-tag) {
  background: var(--color-primary-bg);
  border-color: var(--color-primary-lighter);
  color: var(--color-primary-dark);
  border-radius: var(--radius-full);
  font-weight: 700;
}

.empty-selected {
  width: 100%;
  text-align: center;
  padding: 20px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.empty-icon {
  font-size: 28px;
  margin-bottom: 4px;
}

/* ====== 学习模式 ====== */
.mode-section {
  margin-bottom: 16px;
}

.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.mode-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 12px 8px;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  background: #FFFFFF;
  cursor: pointer;
  transition: all 0.15s ease;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  font-family: var(--font-family);
}

.mode-option:hover {
  border-color: var(--color-primary-lighter);
  background: var(--color-primary-bg);
}

.mode-option.active {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
  box-shadow: 0 3px 0 var(--color-primary-dark);
}

.mode-option:active {
  transform: translateY(2px);
  box-shadow: 0 1px 0 var(--color-border-lighter);
}

.mode-emoji { font-size: 20px; }

.mode-name {
  font-size: 13px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.mode-desc {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.review-first-label {
  display: flex; align-items: center; gap: 6px; margin-top: 10px;
  font-size: 13px; color: var(--color-text-regular); cursor: pointer;
}

.start-btn {
  width: 100%;
  height: 52px;
  font-size: 16px;
  font-weight: 800 !important;
}

.timo-mini-section {
  display: flex; align-items: center; gap: 8px; padding: 10px 0;
  border-bottom: 2px solid var(--color-border-lighter);
  margin-bottom: 12px;
}

.timo-mini-hint {
  font-size: 12px; font-weight: 700; color: var(--color-text-muted);
}

/* ====== Agent 推荐 ====== */
.recommend-section {
  margin-bottom: 16px;
  padding: 12px;
  background: var(--color-primary-bg);
  border: 2px solid var(--color-primary-lighter);
  border-radius: var(--radius-md);
}

.recommend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.recommend-list {
  max-height: 150px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recommend-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 8px;
  background: #FFFFFF;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-primary-lighter);
}

.recommend-word {
  font-weight: 800;
  font-size: 13px;
  color: var(--color-primary-dark);
  white-space: nowrap;
}

.recommend-meaning {
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-empty {
  text-align: center;
  padding: 16px 12px;
}

.recommend-empty-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-secondary);
  margin: 0 0 4px;
}

.recommend-empty-sub {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  line-height: 1.5;
}

/* ====== Responsive ====== */
@media (max-width: 1024px) {
  .main-layout {
    grid-template-columns: 1fr;
  }
  .filter-panel { order: 2; }
  .word-list-panel { order: 1; }
  .selected-panel { order: 3; }
  .panel-card { padding: 16px; }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
    padding: 20px;
  }
  .header-stats { align-self: flex-start; }
  .header-content h1 { font-size: 20px; }
}
</style>
