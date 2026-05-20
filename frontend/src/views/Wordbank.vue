<template>
  <div class="wordbank">
    <div class="wordbank-header">
      <h2>词库</h2>
      <span class="word-count">共 {{ total }} 个单词</span>
    </div>

    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="filter-group">
            <el-select v-model="selectedExamType" placeholder="考试类型" clearable @change="onFilterChange" style="width:100%; margin-bottom:8px">
              <el-option v-for="item in examTypes" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
            <el-select v-model="selectedFamiliarity" placeholder="熟悉度" clearable @change="onFilterChange" style="width:100%; margin-bottom:8px">
              <el-option label="未学习" value="new" />
              <el-option label="学习中" value="learning" />
              <el-option label="已掌握" value="mastered" />
              <el-option label="顽固词" value="stubborn" />
            </el-select>
            <el-input v-model="searchKeyword" placeholder="搜索单词" prefix-icon="Search" clearable
              @input="debounceSearch" />
          </div>
          <div class="timo-mini-section">
            <TiMoFAB compact />
            <span class="timo-mini-hint">问我查词更高效</span>
          </div>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card shadow="never" v-loading="loading">
          <div class="word-cards">
            <div v-for="word in wordList" :key="word.id" class="word-card" @click="showDetail(word)">
              <div class="word-card-header">
                <span class="word-card-word">{{ word.word }}</span>
                <el-tag size="small" :type="familiarityTagType(word.familiarity)">
                  {{ familiarityLabel(word.familiarity) }}
                </el-tag>
              </div>
              <div class="word-card-phonetic">{{ word.phonetic }}</div>
              <div class="word-card-meaning">{{ word.meanings?.[0]?.meaning || '-' }}</div>
              <div class="word-card-progress" v-if="word.stability != null">
                <el-progress :percentage="stabilityPercent(word.stability)" :stroke-width="6"
                  :color="progressColor(word.stability)" :show-text="false" />
                <span class="progress-label">掌握度 {{ stabilityPercent(word.stability) }}%</span>
              </div>
            </div>
          </div>
          <div v-if="!loading && wordList.length === 0" class="empty-state">
            暂无匹配的单词
          </div>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[20, 50, 100]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @size-change="loadWords"
              @current-change="loadWords"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="drawerVisible" :title="currentWord?.word" size="400px">
      <template v-if="currentWord">
        <div class="detail-section">
          <p><strong>音标：</strong>{{ currentWord.phonetic }}</p>
          <p><strong>词性：</strong>{{ currentWord.pos }}</p>
          <p v-if="currentWord.collins"><strong>柯林斯：</strong><span class="collins-stars"><span v-for="i in currentWord.collins" :key="i" class="star">&#9733;</span></span> ({{ currentWord.collins }}星)</p>
          <p v-if="currentWord.bncFreq"><strong>BNC词频：</strong>第 {{ currentWord.bncFreq }} 位</p>
          <p v-if="currentWord.frqFreq"><strong>当代词频：</strong>第 {{ currentWord.frqFreq }} 位</p>
        </div>
        <el-divider />
        <div class="detail-section">
          <h4>释义</h4>
          <div v-for="m in currentWord.meanings" :key="m.id" class="meaning-item">
            <el-tag size="small" type="info">{{ m.partOfSpeech }}</el-tag>
            <span>{{ m.meaning }}</span>
          </div>
        </div>
        <el-divider v-if="currentWord.examples?.length" />
        <div class="detail-section" v-if="currentWord.examples?.length">
          <h4>例句</h4>
          <div v-for="e in currentWord.examples" :key="e.id" class="example-item">
            <p class="en">{{ e.sentence }}</p>
            <p class="zh" v-if="e.translation">{{ e.translation }}</p>
          </div>
        </div>
        <el-divider v-if="fsrsState" />
        <div class="detail-section" v-if="fsrsState">
          <h4>学习状态</h4>
          <div class="fsrs-mini-chart">
            <div class="fsrs-stat">
              <span class="fsrs-label">稳定性</span>
              <span class="fsrs-value">{{ fsrsState.stability != null ? fsrsState.stability.toFixed(1) : '-' }}</span>
            </div>
            <div class="fsrs-stat">
              <span class="fsrs-label">难度</span>
              <span class="fsrs-value">{{ fsrsState.difficulty != null ? fsrsState.difficulty.toFixed(1) : '-' }}</span>
            </div>
            <div class="fsrs-stat">
              <span class="fsrs-label">可检索性</span>
              <span class="fsrs-value">{{ fsrsState.retrievability != null ? (fsrsState.retrievability * 100).toFixed(0) + '%' : '-' }}</span>
            </div>
            <div class="fsrs-stat">
              <span class="fsrs-label">下次复习</span>
              <span class="fsrs-value">{{ fsrsState.nextReviewTime ? formatDate(fsrsState.nextReviewTime) : '-' }}</span>
            </div>
          </div>
        </div>

        <!-- TiMo 深度解析 -->
        <el-divider />
        <div class="detail-section">
          <h4>TiMo 深度解析</h4>
          <div class="timo-analysis">
            <button class="timo-analysis-btn" @click="analyzeWord" :disabled="analyzing">
              <span v-if="analyzing" class="analyzing-spinner"></span>
              <span v-else>&#x1F9E0;</span>
              {{ analyzing ? '分析中...' : '让 TiMo 帮你分析这个词' }}
            </button>
            <div v-if="wordAnalysis" class="analysis-content">
              <div class="analysis-item" v-if="wordAnalysis.etymology">
                <span class="analysis-label">&#x1F333; 词根词缀</span>
                <span class="analysis-text">{{ wordAnalysis.etymology }}</span>
              </div>
              <div class="analysis-item" v-if="wordAnalysis.mnemonic">
                <span class="analysis-label">&#x1F4A1; 助记技巧</span>
                <span class="analysis-text">{{ wordAnalysis.mnemonic }}</span>
              </div>
              <div class="analysis-item" v-if="wordAnalysis.usage">
                <span class="analysis-label">&#x1F4DD; 常见搭配</span>
                <span class="analysis-text">{{ wordAnalysis.usage }}</span>
              </div>
              <div class="analysis-item" v-if="wordAnalysis.synonyms">
                <span class="analysis-label">&#x1F500; 近义词辨析</span>
                <span class="analysis-text">{{ wordAnalysis.synonyms }}</span>
              </div>
              <div class="analysis-item" v-if="wordAnalysis.example">
                <span class="analysis-label">&#x1F4D6; 地道例句</span>
                <span class="analysis-text">{{ wordAnalysis.example }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getWordList, getWordDetail, getWordFsrsState } from '../api/words'
import { analyzeWord as analyzeWordApi } from '../api/agent'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import { examTypes } from '../constants/examTypes'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const agentStore = useAgentStore()
const userStore = useUserStore()

const loading = ref(false)
const wordList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedExamType = ref('')
const selectedFamiliarity = ref('')
const searchKeyword = ref('')
const drawerVisible = ref(false)
const currentWord = ref(null)
const fsrsState = ref(null)
const analyzing = ref(false)
const wordAnalysis = ref(null)

let searchTimer = null
function debounceSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadWords()
  }, 300)
}

function onFilterChange() {
  currentPage.value = 1
  loadWords()
}

function familiarityLabel(fam) {
  const map = { new: '未学习', learning: '学习中', mastered: '已掌握', stubborn: '顽固词' }
  return map[fam] || '未学习'
}

function familiarityTagType(fam) {
  const map = { new: 'info', learning: 'warning', mastered: 'success', stubborn: 'danger' }
  return map[fam] || 'info'
}

function stabilityPercent(s) {
  if (!s) return 0
  return Math.min(100, Math.round(s / 3.0 * 100))
}

function progressColor(s) {
  if (!s) return '#909399'
  if (s < 0.5) return '#F56C6C'
  if (s < 1.2) return '#E6A23C'
  return '#67C23A'
}

function formatDate(dt) {
  if (!dt) return '-'
  return dt.slice(0, 10)
}

async function loadWords() {
  loading.value = true
  try {
    const res = await getWordList({
      keyword: searchKeyword.value || undefined,
      examType: selectedExamType.value || undefined,
      familiarity: selectedFamiliarity.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    wordList.value = res.data.content
    total.value = res.data.totalElements
  } catch (e) { /* handled */ } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  try {
    const res = await getWordDetail(row.id)
    currentWord.value = res.data
    drawerVisible.value = true
    wordAnalysis.value = null
    loadFsrsState(row.id)
  } catch (e) { /* handled */ }
}

async function loadFsrsState(wordId) {
  fsrsState.value = null
  try {
    const res = await getWordFsrsState(wordId)
    fsrsState.value = res.data
  } catch (e) { /* no FSRS record */ }
}

async function analyzeWord() {
  if (!currentWord.value) return
  analyzing.value = true
  wordAnalysis.value = null
  try {
    const res = await analyzeWordApi(currentWord.value.word)
    wordAnalysis.value = res.data
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  agentStore.setCurrentPage('wordbank')
  if (userStore.token) {
    loadWords()
  }
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.wordbank { max-width: 1200px; margin: 0 auto; }
.wordbank-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.wordbank-header h2 { font-size: 22px; font-weight: 900; color: var(--color-text-primary); margin: 0; }
.word-count { color: var(--color-text-secondary); font-size: 14px; font-weight: 700; background: var(--color-primary-bg); padding: 4px 12px; border-radius: var(--radius-full); }

.filter-group { display: flex; flex-direction: column; gap: 10px; }
.filter-group :deep(.el-input__wrapper) { border-radius: var(--radius-md) !important; }
.filter-group :deep(.el-select) { width: 100%; }

.word-cards {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.word-card {
  padding: 16px; background: #fff; border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md); cursor: pointer;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: all 0.2s ease;
}
.word-card:hover {
  transform: translateY(-2px); box-shadow: 0 5px 0 var(--color-border-lighter);
  border-color: var(--color-primary-light);
}
.word-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.word-card-word { font-size: 18px; font-weight: 900; color: var(--color-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 140px; }
.word-card-phonetic { font-size: 12px; color: var(--color-text-muted); margin-bottom: 6px; }
.word-card-meaning { font-size: 13px; color: var(--color-text-regular); line-height: 1.5; margin-bottom: 8px; }
.word-card-progress { margin-top: 4px; }
.progress-label { font-size: 11px; color: var(--color-text-muted); font-weight: 700; }

.empty-state {
  text-align: center; padding: 40px 0; color: var(--color-text-muted); font-size: 14px;
}

.collins-stars { color: var(--color-yellow); font-size: 16px; letter-spacing: -2px; text-shadow: 0 1px 2px rgba(0,0,0,0.1); }

.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

:deep(.el-drawer) { border-radius: var(--radius-xl) 0 0 var(--radius-xl); }
:deep(.el-drawer__header) { font-weight: 800; font-size: 18px; }
.detail-section { margin-bottom: 8px; }
.detail-section h4 { margin: 0 0 10px; font-size: 13px; font-weight: 800; color: var(--color-blue); text-transform: uppercase; letter-spacing: 0.5px; }
.detail-section p { margin: 6px 0; font-size: 14px; line-height: 1.6; }
.meaning-item { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-weight: 600; }
.example-item { margin-bottom: 12px; padding: 12px; background: var(--color-bg-hover); border-radius: var(--radius-md); }
.example-item .en { font-style: italic; color: var(--color-text-primary); font-weight: 600; }
.example-item .zh { color: var(--color-text-secondary); font-size: 13px; margin-top: 4px; }

.fsrs-mini-chart {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
}
.fsrs-stat { text-align: center; padding: 8px; background: var(--color-bg-hover); border-radius: var(--radius-md); }
.fsrs-label { display: block; font-size: 11px; color: var(--color-text-muted); font-weight: 700; margin-bottom: 4px; }
.fsrs-value { font-size: 16px; font-weight: 900; color: var(--color-text-primary); }
.fsrs-action { margin-top: 12px; text-align: center; }

.timo-mini-section {
  display: flex; align-items: center; gap: 8px; padding: 12px 0 0;
  margin-top: 16px; border-top: 1px solid var(--color-border-lighter);
}
.timo-mini-hint {
  font-size: 12px; font-weight: 700; color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .wordbank .el-row { flex-direction: column; }
  .wordbank .el-col { max-width: 100% !important; flex: 0 0 100% !important; margin-bottom: 12px; }
  .word-cards { grid-template-columns: 1fr; }
}

/* TiMo 深度解析 */
.timo-analysis { margin-top: 8px; }
.timo-analysis-btn {
  width: 100%; padding: 12px 16px;
  border: 2px dashed var(--color-primary); border-radius: var(--radius-md);
  background: var(--color-primary-bg); color: var(--color-primary-dark);
  font-weight: 700; font-size: 13px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  transition: all 0.2s ease; font-family: var(--font-family);
}
.timo-analysis-btn:hover:not(:disabled) {
  background: var(--color-primary); color: #fff;
  transform: translateY(-1px);
}
.timo-analysis-btn:disabled { opacity: 0.6; cursor: default; }
.analyzing-spinner {
  width: 14px; height: 14px;
  border: 2px solid var(--color-primary); border-top-color: transparent;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.analysis-content {
  margin-top: 12px; display: flex; flex-direction: column; gap: 10px;
}
.analysis-item {
  display: flex; flex-direction: column; gap: 4px;
  padding: 10px 12px; background: var(--color-bg-hover); border-radius: var(--radius-md);
}
.analysis-label { font-size: 12px; font-weight: 800; color: var(--color-primary-dark); }
.analysis-text { font-size: 13px; font-weight: 600; color: var(--color-text-regular); line-height: 1.5; }
</style>
