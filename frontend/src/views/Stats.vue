<template>
  <div class="stats-page fade-in-up" v-loading="loading">
    <div class="page-header-row">
      <h2 class="page-title">统计仪表盘</h2>
      <div class="header-right">
        <el-radio-group v-model="timeRange" size="small" @change="onTimeRangeChange">
          <el-radio-button :value="7">7天</el-radio-button>
          <el-radio-button :value="30">30天</el-radio-button>
          <el-radio-button :value="0">全部</el-radio-button>
        </el-radio-group>
        <div class="timo-inline">
          <TiMoFAB compact />
          <span class="timo-inline-text">点我帮你分析薄弱点</span>
        </div>
      </div>
    </div>

    <!-- Top indicator cards -->
    <el-row :gutter="16" class="stat-cards fade-in-up fade-in-up-delay-1">
      <el-col :lg="6" :md="12" :sm="12" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">{{ card.icon }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Retention trend + Daily study -->
    <el-row :gutter="16" class="chart-row fade-in-up fade-in-up-delay-2">
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header><span>正确率分布</span></template>
          <v-chart :option="retentionOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header><span>每日学习量</span></template>
          <v-chart :option="dailyOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <!-- Heatmap + Reaction time -->
    <el-row :gutter="16" class="chart-row fade-in-up fade-in-up-delay-3">
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header><span>学习热力图</span></template>
          <v-chart :option="heatmapOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header><span>反应时分布</span></template>
          <v-chart :option="reactionOption" style="height: 280px" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <!-- Weak words cloud + Forgetting curve -->
    <el-row :gutter="16" class="chart-row fade-in-up fade-in-up-delay-4">
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header><span>薄弱词云</span></template>
          <div ref="wordCloudRef" style="height: 280px"></div>
        </el-card>
      </el-col>
      <el-col :lg="12" :sm="24">
        <el-card shadow="hover">
          <template #header>
            <div class="forgetting-header">
              <span>遗忘曲线对比</span>
              <el-button text size="small" @click="forgettingExpanded = !forgettingExpanded">
                {{ forgettingExpanded ? '收起' : '展开' }}
              </el-button>
            </div>
          </template>
          <div v-show="forgettingExpanded">
            <v-chart :option="forgettingOption" style="height: 280px" autoresize />
          </div>
          <div v-if="!forgettingExpanded" class="forgetting-collapsed">
            点击展开查看遗忘曲线详情
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- TiMo 周报 -->
    <el-row :gutter="16" class="chart-row fade-in-up fade-in-up-delay-5">
      <el-col :span="24">
        <el-card shadow="hover" class="weekly-report-card">
          <template #header>
            <div class="report-header">
              <span>TiMo 学习周报</span>
              <el-button type="primary" size="small" @click="generateWeeklyReport" :loading="reportLoading">
                {{ reportLoading ? '生成中...' : '生成周报' }}
              </el-button>
            </div>
          </template>
          <div v-if="weeklyReport" class="report-content">
            <div class="report-section">
              <div class="report-title">&#x1F4CA; 本周概览</div>
              <div class="report-stats">
                <div class="report-stat">
                  <span class="report-stat-value">{{ weeklyReport.totalWords }}</span>
                  <span class="report-stat-label">学习单词</span>
                </div>
                <div class="report-stat">
                  <span class="report-stat-value">{{ weeklyReport.masteredWords }}</span>
                  <span class="report-stat-label">已掌握</span>
                </div>
                <div class="report-stat">
                  <span class="report-stat-value">{{ weeklyReport.avgAccuracy }}%</span>
                  <span class="report-stat-label">平均正确率</span>
                </div>
              </div>
            </div>
            <div class="report-section" v-if="weeklyReport.suggestion">
              <div class="report-title">&#x1F4A1; TiMo 建议</div>
              <div class="report-text">{{ weeklyReport.suggestion }}</div>
            </div>
            <div class="report-section" v-if="weeklyReport.weakness">
              <div class="report-title">&#x1F525; 薄弱环节</div>
              <div class="report-text">{{ weeklyReport.weakness }}</div>
            </div>
          </div>
          <div v-else class="report-empty">
            点击"生成周报"获取本周学习分析
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import VChart from 'vue-echarts'
import TiMoFAB from '../components/agent/TiMoFAB.vue'
import { ElMessage } from 'element-plus'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'

const agentStore = useAgentStore()
const userStore = useUserStore()
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, ScatterChart, BoxplotChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, VisualMapComponent, CalendarComponent
} from 'echarts/components'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import {
  getOverview, getRetention, getDailyStats,
  getHeatmap, getReactionTime, getWeakWords, getForgettingCurve
} from '../api/statistics'
import { getWeeklyReport } from '../api/agent'

use([
  CanvasRenderer, LineChart, BarChart, ScatterChart, BoxplotChart, PieChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, VisualMapComponent, CalendarComponent
])

const chartColors = {
  primary: '#1CB0F6',
  success: '#58CC02',
  warning: '#FF9600',
  danger: '#FF4B4B',
  purple: '#CE82FF',
  yellow: '#FFC800'
}

const statCards = ref([
  { label: '学习天数', value: '-', icon: '📅' },
  { label: '总词量', value: '-', icon: '📚' },
  { label: '已掌握', value: '-', icon: '✅' },
  { label: '平均正确率', value: '-', icon: '📊' }
])

const loading = ref(true)
const timeRange = ref(7)
const forgettingExpanded = ref(true)
const reportLoading = ref(false)
const weeklyReport = ref(null)

const retentionOption = ref({})
const dailyOption = ref({})
const heatmapOption = ref({})
const reactionOption = ref({})
const forgettingOption = ref({})
const wordCloudRef = ref(null)

function buildRetentionOption(data) {
  const avg = data.retentionRates.length
    ? data.retentionRates.reduce((a, b) => a + b, 0) / data.retentionRates.length
    : 0
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['45%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: Math.round(avg * 10) / 10, name: '正确', itemStyle: { color: chartColors.success } },
        { value: Math.round((100 - avg) * 10) / 10, name: '错误', itemStyle: { color: chartColors.danger } }
      ]
    }]
  }
}

function buildDailyOption(data) {
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['快速记忆', '语境深度', '统一复习'], bottom: 0 },
    grid: { top: 10, bottom: 40, left: 40, right: 10 },
    xAxis: { type: 'category', data: data.dates.map(d => d.slice(5)) },
    yAxis: { type: 'value' },
    series: [
      { name: '快速记忆', type: 'bar', stack: 'total', data: data.quickMemory, itemStyle: { color: chartColors.primary } },
      { name: '语境深度', type: 'bar', stack: 'total', data: data.contextDeep, itemStyle: { color: chartColors.success } },
      { name: '统一复习', type: 'bar', stack: 'total', data: data.unifiedReview, itemStyle: { color: chartColors.warning } }
    ]
  }
}

function buildHeatmapOption(data) {
  return {
    tooltip: { formatter: p => `${p.data[0]}<br/>学习单词: ${p.data[1]}` },
    visualMap: {
      min: 0, max: data.maxCount, show: false,
      inRange: { color: ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39'] }
    },
    calendar: {
      range: String(new Date().getFullYear()), cellSize: [20, 20],
      itemStyle: { borderWidth: 3, borderColor: '#fff' },
      splitLine: { show: false },
      dayLabel: { nameMap: 'ZH' },
      monthLabel: { nameMap: 'ZH' }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: data.data }]
  }
}

function buildReactionOption(data) {
  const toBoxData = (arr) => {
    if (!arr.length) return []
    const sorted = [...arr].sort((a, b) => a - b)
    const q1 = sorted[Math.floor(sorted.length * 0.25)]
    const q2 = sorted[Math.floor(sorted.length * 0.5)]
    const q3 = sorted[Math.floor(sorted.length * 0.75)]
    return [sorted[0], q1, q2, q3, sorted[sorted.length - 1]]
  }
  return {
    tooltip: { trigger: 'item' },
    xAxis: { type: 'category', data: ['快速记忆', '语境深度', '统一复习'] },
    yAxis: { type: 'value', name: 'ms' },
    series: [{
      type: 'boxplot',
      data: [toBoxData(data.quickMemory), toBoxData(data.contextDeep), toBoxData(data.unifiedReview)],
      itemStyle: { color: chartColors.primary }
    }]
  }
}

function buildForgettingOption(data) {
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['FSRS预测', '实际保持率'], bottom: 0 },
    xAxis: { type: 'category', data: data.days },
    yAxis: { type: 'value', max: 1.0 },
    series: [
      { name: 'FSRS预测', type: 'line', data: data.expectedR, smooth: true, itemStyle: { color: chartColors.primary } },
      { name: '实际保持率', type: 'line', data: data.actualR, smooth: true, itemStyle: { color: chartColors.success } }
    ]
  }
}

async function generateWeeklyReport() {
  reportLoading.value = true
  try {
    const res = await getWeeklyReport()
    weeklyReport.value = res.data
  } catch {
    ElMessage.error('周报生成失败，请稍后重试')
  } finally {
    reportLoading.value = false
  }
}

let wordCloudChart = null

async function onTimeRangeChange() {
  loading.value = true
  const days = timeRange.value || 9999
  try {
    const [retentionRes, dailyRes, reactionRes, forgettingRes] =
      await Promise.allSettled([
        getRetention(days), getDailyStats(days),
        getReactionTime(days), getForgettingCurve(days)
      ])
    if (retentionRes.status === 'fulfilled') retentionOption.value = buildRetentionOption(retentionRes.value.data)
    if (dailyRes.status === 'fulfilled') dailyOption.value = buildDailyOption(dailyRes.value.data)
    if (reactionRes.status === 'fulfilled') reactionOption.value = buildReactionOption(reactionRes.value.data)
    if (forgettingRes.status === 'fulfilled') forgettingOption.value = buildForgettingOption(forgettingRes.value.data)
  } finally {
    loading.value = false
  }
}

function buildWordCloud(words) {
  if (!wordCloudRef.value || !words.length) return
  wordCloudChart = echarts.init(wordCloudRef.value)
  wordCloudChart.setOption({
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      gridSize: 8,
      sizeRange: [14, 40],
      rotationRange: [-30, 30],
      textStyle: {
        fontFamily: 'sans-serif',
        color: () => [chartColors.primary, chartColors.success, chartColors.warning, chartColors.danger][Math.floor(Math.random() * 4)]
      },
      data: words.map(w => ({ name: w.word || `word#${w.wordId}`, value: w.consecutiveErrors + w.difficulty }))
    }]
  })
}

onBeforeUnmount(() => {
  if (wordCloudChart) {
    wordCloudChart.dispose()
    wordCloudChart = null
  }
})

onMounted(async () => {
  agentStore.setCurrentPage('stats')

  if (!userStore.token) {
    loading.value = false
    return
  }

  const days = timeRange.value
  const [overviewRes, retentionRes, dailyRes, heatmapRes, reactionRes, weakRes, forgettingRes] =
    await Promise.allSettled([
      getOverview(), getRetention(days), getDailyStats(days),
      getHeatmap(), getReactionTime(days), getWeakWords(), getForgettingCurve(days)
    ])

  if (overviewRes.status === 'fulfilled') {
    const d = overviewRes.value.data
    statCards.value[0].value = d.studyDays
    statCards.value[1].value = d.totalWordsStudied
    statCards.value[2].value = d.masteredWords
    statCards.value[3].value = d.avgAccuracy + '%'
  }

  if (retentionRes.status === 'fulfilled') retentionOption.value = buildRetentionOption(retentionRes.value.data)
  if (dailyRes.status === 'fulfilled') dailyOption.value = buildDailyOption(dailyRes.value.data)
  if (heatmapRes.status === 'fulfilled') heatmapOption.value = buildHeatmapOption(heatmapRes.value.data)
  if (reactionRes.status === 'fulfilled') reactionOption.value = buildReactionOption(reactionRes.value.data)
  if (forgettingRes.status === 'fulfilled') forgettingOption.value = buildForgettingOption(forgettingRes.value.data)

  if (weakRes.status === 'fulfilled') {
    await nextTick()
    buildWordCloud(weakRes.value.data)
  }

  loading.value = false
})
</script>

<style scoped>
.stats-page { max-width: 1200px; margin: 0 auto; }
.page-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 900; color: var(--color-text-primary); margin: 0; }
.stat-cards { margin-bottom: 20px; }

.stat-card {
  text-align: center; padding: 20px 12px;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 0 var(--color-border-lighter);
  background: #FFFFFF;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 0 var(--color-border-lighter);
}

/* Use el-col nth-child to target the correct columns */
.stat-cards .el-col:nth-child(1) .stat-card { border-top: 3px solid var(--color-primary-dark); }
.stat-cards .el-col:nth-child(2) .stat-card { border-top: 3px solid var(--color-blue-dark); }
.stat-cards .el-col:nth-child(3) .stat-card { border-top: 3px solid var(--color-purple-dark); }
.stat-cards .el-col:nth-child(4) .stat-card { border-top: 3px solid var(--color-orange-dark); }

.stat-icon {
  font-size: 24px; margin-bottom: 8px;
}

.stat-value {
  font-size: 32px; font-weight: 900; color: var(--color-blue-dark);
  font-family: var(--font-mono); line-height: 1.2;
}

.stat-cards .el-col:nth-child(1) .stat-value { color: var(--color-primary-dark); }
.stat-cards .el-col:nth-child(2) .stat-value { color: var(--color-blue-dark); }
.stat-cards .el-col:nth-child(3) .stat-value { color: var(--color-purple-dark); }
.stat-cards .el-col:nth-child(4) .stat-value { color: var(--color-orange-dark); }

.stat-label {
  margin-top: 6px; color: var(--color-text-secondary); font-size: 13px; font-weight: 700;
}

.chart-row { margin-bottom: 20px; }

.header-right { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }

.forgetting-header { display: flex; justify-content: space-between; align-items: center; }
.forgetting-collapsed {
  height: 80px; display: flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); font-size: 13px; font-weight: 500;
}

.chart-row :deep(.el-card) {
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 4px 0 var(--color-border-lighter);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  margin-bottom: 4px;
}

.chart-row :deep(.el-card:hover) {
  transform: translateY(-1px);
  box-shadow: 0 6px 0 var(--color-border-lighter);
}

.chart-row :deep(.el-card__header) {
  font-weight: 800;
  font-size: 15px;
  color: var(--color-text-primary);
  padding: 16px 20px;
}

.timo-inline {
  display: flex; align-items: center; gap: 6px; cursor: pointer;
}

.timo-inline-text {
  font-size: 13px; font-weight: 600; color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .stat-cards .el-col { flex: 0 0 50% !important; max-width: 50% !important; margin-bottom: 12px; }
  .page-header-row { flex-direction: column; gap: 12px; align-items: flex-start; }
  .chart-row .el-col { margin-bottom: 16px; }
  .header-right { flex-wrap: wrap; }
}

/* TiMo 周报 */
.weekly-report-card { margin-top: 8px; }
.report-header { display: flex; justify-content: space-between; align-items: center; }
.report-header span { font-weight: 800; font-size: 15px; color: var(--color-text-primary); }
.report-content { display: flex; flex-direction: column; gap: 16px; }
.report-section {
  padding: 12px; background: var(--color-bg-hover);
  border-radius: var(--radius-md); border-left: 3px solid var(--color-primary);
}
.report-title { font-size: 13px; font-weight: 800; color: var(--color-primary-dark); margin-bottom: 8px; }
.report-stats { display: flex; justify-content: space-around; gap: 12px; }
.report-stat { display: flex; flex-direction: column; align-items: center; }
.report-stat-value { font-size: 20px; font-weight: 900; font-family: var(--font-mono); color: var(--color-text-primary); }
.report-stat-label { font-size: 11px; font-weight: 700; color: var(--color-text-secondary); margin-top: 2px; }
.report-text { font-size: 13px; font-weight: 600; color: var(--color-text-regular); line-height: 1.6; }
.report-empty {
  text-align: center; padding: 24px; color: var(--color-text-muted);
  font-size: 13px; font-weight: 600;
}
</style>
