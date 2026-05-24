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

    <!-- Monthly study calendar (collapsible) -->
    <el-row :gutter="16" class="chart-row fade-in-up fade-in-up-delay-3">
      <el-col :span="24">
        <el-collapse v-model="calendarCollapse" class="calendar-collapse">
          <el-collapse-item name="calendar" title="📅 学习日历">
            <div class="monthly-calendar">
              <div class="calendar-header">
                <el-button @click="changeMonth(-1)">&lt;</el-button>
                <span class="month-label">{{ currentYear }}年{{ currentMonth }}月</span>
                <el-button @click="changeMonth(1)">&gt;</el-button>
              </div>

              <div class="calendar-grid">
                <div class="weekday-row">
                  <div v-for="d in weekdays" :key="d" class="weekday-cell">{{ d }}</div>
                </div>
                <div class="days-grid">
                  <div v-for="(day, i) in calendarDays" :key="i"
                    :class="['day-cell', {
                      today: day.isToday, checked: day.checkedIn, empty: !day.date,
                      'streak-start': day.streakStart, 'streak-end': day.streakEnd
                    }]"
                    @click="day.date && showDetail(day)">
                    <span v-if="day.date" class="day-num">{{ day.dayNum }}</span>
                    <span v-if="day.checkedIn" class="checkin-dot"></span>
                  </div>
                </div>
              </div>

              <div class="calendar-footer">
                <div class="calendar-summary">
                  <span>本月学习 <strong>{{ totalCheckins }}</strong> 天</span>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <!-- Day detail drawer -->
        <el-drawer v-model="drawerVisible" :title="selectedDay?.date" size="320px">
          <template v-if="selectedDay">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="学习状态">{{ selectedDay.checkedIn ? '已学习' : '未学习' }}</el-descriptions-item>
              <el-descriptions-item label="学习单词">{{ selectedDay.wordsStudied }} 个</el-descriptions-item>
              <el-descriptions-item label="学习时长">{{ selectedDay.studyMinutes }} 分钟</el-descriptions-item>
              <el-descriptions-item label="答题次数">{{ selectedDay.totalRecords }} 次</el-descriptions-item>
              <el-descriptions-item label="平均正确率">{{ selectedDay.avgGrade > 0 ? ((selectedDay.avgGrade - 1) / 3 * 100).toFixed(1) + '%' : '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-drawer>
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
              <div class="report-hero">
                <div class="report-hero-text">{{ weeklyReport.summary }}</div>
                <div class="report-hero-meta">
                  <span v-if="weeklyReport.studyDays">📅 {{ weeklyReport.studyDays }} 天学习</span>
                  <span v-if="weeklyReport.longestStreak">🔥 连续学习 {{ weeklyReport.longestStreak }} 天</span>
                </div>
              </div>
              <div class="report-section">
                <div class="report-title">&#x1F4CA; 本周概览</div>
                <div class="report-stats">
                  <div class="report-stat">
                    <span class="report-stat-value">{{ weeklyReport.totalWords ?? weeklyReport.totalStudied }}</span>
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
                  <div class="report-stat" :class="weeklyReport.accuracyDelta >= 0 ? 'delta-up' : 'delta-down'">
                    <span class="report-stat-value">{{ weeklyReport.accuracyDelta >= 0 ? '+' : '' }}{{ weeklyReport.accuracyDelta }}%</span>
                    <span class="report-stat-label">较上周</span>
                  </div>
                </div>
              </div>
              <div class="report-section" v-if="weeklyReport.insights?.length">
                <div class="report-title">&#x1F50E; 数据洞察</div>
                <div class="report-insight-list">
                  <div v-for="(item, idx) in weeklyReport.insights" :key="idx" class="report-insight-item">
                    <span class="report-insight-icon">{{ idx === 0 ? '📝' : (item.includes('提升') ? '📈' : item.includes('下降') ? '📉' : '🎯') }}</span>
                    <span>{{ item }}</span>
                  </div>
                </div>
              </div>
              <div class="report-section" v-if="weeklyReport.suggestion">
                <div class="report-title">&#x1F4A1; TiMo 建议</div>
                <div class="report-text">{{ weeklyReport.suggestion }}</div>
              </div>
              <div class="report-section report-section-weakness" v-if="weeklyReport.weakness">
                <div class="report-title">&#x1F525; 薄弱环节</div>
                <div class="report-text">{{ weeklyReport.weakness }}</div>
              </div>
              <!-- Wave 6 Feature B — 学习节律（仅当时段分析有数据时展示） -->
              <div class="report-section report-section-rhythm" v-if="weeklyReport.timeAnalysis">
                <div class="report-title">&#x1F551; 学习节律</div>
                <div class="rhythm-content">
                  <div class="rhythm-hero">
                    <div class="rhythm-hero-hour">{{ weeklyReport.timeAnalysis.bestHourRange }}</div>
                    <div class="rhythm-hero-acc">{{ Math.round(weeklyReport.timeAnalysis.bestHourAccuracy * 100) }}%</div>
                    <div class="rhythm-hero-label">最佳正确率时段</div>
                  </div>
                  <div class="rhythm-meta">
                    <div class="rhythm-meta-item" v-if="weeklyReport.timeAnalysis.worstHourRange">
                      <span class="rhythm-meta-icon">&#x1F4C9;</span>
                      <span class="rhythm-meta-label">最差时段</span>
                      <span class="rhythm-meta-value">{{ weeklyReport.timeAnalysis.worstHourRange }}（{{ Math.round(weeklyReport.timeAnalysis.worstHourAccuracy * 100) }}%）</span>
                    </div>
                    <div class="rhythm-meta-item" v-if="weeklyReport.timeAnalysis.avgSessionLengthMinutes">
                      <span class="rhythm-meta-icon">&#x23F1;&#xFE0F;</span>
                      <span class="rhythm-meta-label">平均会话</span>
                      <span class="rhythm-meta-value">{{ weeklyReport.timeAnalysis.avgSessionLengthMinutes }} 分钟</span>
                    </div>
                  </div>
                  <div class="rhythm-recommend" v-if="weeklyReport.timeAnalysis.recommendation">
                    <span class="rhythm-recommend-icon">&#x1F4A1;</span>
                    {{ weeklyReport.timeAnalysis.recommendation }}
                  </div>
                </div>
              </div>
            </div>
          <div v-else class="report-empty">
            <div class="report-empty-icon">📊</div>
            <div class="report-empty-title">{{ isMonday ? '周报生成中...' : '周报尚未生成' }}</div>
            <div class="report-empty-hint">{{ isMonday ? '点击上方按钮获取本周学习分析' : '每周一可生成本周学习周报' }}</div>
            <el-button v-if="isMonday" type="primary" size="small" @click="generateWeeklyReport" :loading="reportLoading" style="margin-top: 8px;">
              生成周报
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import VChart from 'vue-echarts'
import TiMoFAB from '../components/agent/TiMoFAB.vue'
import { ElMessage } from 'element-plus'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import dayjs from 'dayjs'

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
import { getMonthly } from '../api/calendar'

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
const isMonday = dayjs().day() === 1

const retentionOption = ref({})
const dailyOption = ref({})
const heatmapOption = ref({})
const reactionOption = ref({})
const forgettingOption = ref({})
const wordCloudRef = ref(null)

// Monthly calendar state
const calendarCollapse = ref([])
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const currentYear = ref(dayjs().year())
const currentMonth = ref(dayjs().month() + 1)
const daysMap = ref({})
const totalCheckins = ref(0)
const drawerVisible = ref(false)
const selectedDay = ref(null)

const calendarDays = computed(() => {
  const first = dayjs(`${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}-01`)
  const startWeekday = (first.day() + 6) % 7 // Monday=0
  const daysInMonth = first.daysInMonth()
  const today = dayjs().format('YYYY-MM-DD')

  const cells = []
  for (let i = 0; i < startWeekday; i++) cells.push({ date: null })
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const info = daysMap.value[dateStr] || {}
    cells.push({
      date: dateStr,
      dayNum: d,
      isToday: dateStr === today,
      checkedIn: info.checkedIn || false,
      wordsStudied: info.wordsStudied || 0,
      studyMinutes: info.studyMinutes || 0,
      totalRecords: info.totalRecords || 0,
      avgGrade: info.avgGrade || 0,
      streakStart: false,
      streakEnd: false
    })
  }

  for (let i = 0; i < cells.length; i++) {
    if (!cells[i].date || !cells[i].checkedIn) continue
    const row = Math.floor(i / 7)
    const prev = i > 0 && Math.floor((i - 1) / 7) === row ? cells[i - 1] : null
    const next = i < cells.length - 1 && Math.floor((i + 1) / 7) === row ? cells[i + 1] : null
    const prevChecked = prev && prev.date && prev.checkedIn
    const nextChecked = next && next.date && next.checkedIn
    cells[i].streakStart = !prevChecked && nextChecked
    cells[i].streakEnd = prevChecked && !nextChecked
  }

  return cells
})

async function loadMonth(y, m) {
  try {
    const res = await getMonthly(y, m)
    const data = res.data
    totalCheckins.value = data.totalCheckinDays
    daysMap.value = {}
    data.days.forEach(d => { daysMap.value[d.date] = d })
  } catch {
    daysMap.value = {}
    totalCheckins.value = 0
  }
}

function changeMonth(delta) {
  const d = dayjs(`${currentYear.value}-${currentMonth.value}-01`).add(delta, 'month')
  currentYear.value = d.year()
  currentMonth.value = d.month() + 1
  loadMonth(currentYear.value, currentMonth.value)
}

function showDetail(day) {
  selectedDay.value = day
  drawerVisible.value = true
}

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
  const [overviewRes, retentionRes, dailyRes, heatmapRes, reactionRes, weakRes, forgettingRes, weeklyRes] =
    await Promise.allSettled([
      getOverview(), getRetention(days), getDailyStats(days),
      getHeatmap(), getReactionTime(days), getWeakWords(), getForgettingCurve(days),
      ...(isMonday ? [getWeeklyReport()] : [Promise.resolve(null)])
    ])

  if (weeklyRes?.status === 'fulfilled' && weeklyRes.value) {
    weeklyReport.value = weeklyRes.value.data
  }

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

  loadMonth(currentYear.value, currentMonth.value)

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
.report-hero {
  padding: 16px 20px;
  background: linear-gradient(135deg, var(--color-primary-bg) 0%, var(--color-bg-hover) 100%);
  border-radius: var(--radius-md);
  border-left: 4px solid var(--color-primary);
}
.report-hero-text {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-text-primary);
  line-height: 1.6;
  margin-bottom: 8px;
}
.report-hero-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.report-section {
  padding: 12px; background: var(--color-bg-hover);
  border-radius: var(--radius-md); border-left: 3px solid var(--color-primary);
}
.report-section-weakness {
  border-left-color: var(--color-warning, #E6A23C);
}
.report-title { font-size: 13px; font-weight: 800; color: var(--color-primary-dark); margin-bottom: 8px; }
.report-stats { display: flex; justify-content: space-around; gap: 12px; }
.report-stat { display: flex; flex-direction: column; align-items: center; }
.report-stat-value { font-size: 20px; font-weight: 900; font-family: var(--font-mono); color: var(--color-text-primary); }
.report-stat-label { font-size: 11px; font-weight: 700; color: var(--color-text-secondary); margin-top: 2px; }
.delta-up .report-stat-value { color: var(--color-success, #67C23A); }
.delta-down .report-stat-value { color: var(--color-danger, #F56C6C); }
.report-insight-list { display: flex; flex-direction: column; gap: 8px; }
.report-insight-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-regular);
  line-height: 1.6;
}
.report-insight-icon { flex-shrink: 0; font-size: 14px; }
.report-text { font-size: 13px; font-weight: 600; color: var(--color-text-regular); line-height: 1.6; }
.report-empty {
  text-align: center; padding: 32px 24px; color: var(--color-text-muted);
  font-size: 13px; font-weight: 600;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.report-empty-icon { font-size: 36px; }
.report-empty-title { font-size: 15px; font-weight: 800; color: var(--color-text-primary); }
.report-empty-hint { font-size: 12px; font-weight: 600; color: var(--color-text-secondary); }

/* Wave 6 Feature B — 学习节律 */
.report-section-rhythm {
  border-left-color: var(--color-purple, #CE82FF);
}
.rhythm-content { display: flex; flex-direction: column; gap: 12px; }
.rhythm-hero {
  display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap;
  padding: 12px 16px;
  background: linear-gradient(135deg, #F3E5F5 0%, #E1BEE7 100%);
  border-radius: var(--radius-md);
}
.rhythm-hero-hour {
  font-size: 24px; font-weight: 900; color: #6A1B9A;
  font-family: var(--font-mono);
}
.rhythm-hero-acc {
  font-size: 22px; font-weight: 900; color: var(--color-primary-dark, #2E7D32);
  font-family: var(--font-mono);
}
.rhythm-hero-label {
  font-size: 12px; font-weight: 700; color: var(--color-text-secondary);
  margin-left: auto;
}
.rhythm-meta { display: flex; gap: 16px; flex-wrap: wrap; }
.rhythm-meta-item {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--color-text-regular);
}
.rhythm-meta-icon { font-size: 14px; }
.rhythm-meta-label { color: var(--color-text-secondary); font-weight: 700; }
.rhythm-meta-value { color: var(--color-text-primary); }
.rhythm-recommend {
  display: flex; align-items: flex-start; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--color-text-regular);
  line-height: 1.6;
}
.rhythm-recommend-icon { flex-shrink: 0; font-size: 14px; }

/* Monthly calendar */
.calendar-collapse {
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 0 var(--color-border-lighter);
  background: #FFFFFF;
}
.calendar-collapse :deep(.el-collapse-item__header) {
  font-weight: 800;
  font-size: 15px;
  color: var(--color-text-primary);
  padding: 0 20px;
  border-bottom: none;
}
.calendar-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.calendar-collapse :deep(.el-collapse-item__content) {
  padding: 16px 20px 20px;
}

.monthly-calendar { max-width: 700px; margin: 0 auto; }

.calendar-header {
  display: flex; align-items: center; justify-content: center; gap: 24px;
  background: var(--color-primary-bg); border-radius: var(--radius-md); padding: 16px; margin-bottom: 20px;
}
.month-label { font-size: 22px; font-weight: 900; color: var(--color-text-primary); letter-spacing: 0.02em; min-width: 140px; text-align: center; }

.calendar-header :deep(.el-button) {
  width: 42px; height: 42px; border-radius: 50%; font-weight: 800; font-size: 16px;
  border: 2px solid var(--color-border-lighter); box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: all 0.2s ease; display: flex; align-items: center; justify-content: center;
}
.calendar-header :deep(.el-button:hover) {
  background: var(--color-primary); color: #fff; border-color: var(--color-primary);
  transform: scale(1.05);
}

.weekday-row { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; margin-bottom: 8px; }
.weekday-cell { color: var(--color-text-muted); font-size: 12px; font-weight: 800; padding: 6px 0; text-transform: uppercase; }
.days-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }

.day-cell {
  position: relative; text-align: center; padding: 12px 0; border-radius: var(--radius-sm);
  cursor: pointer; transition: all 0.2s ease, background 0.3s ease, border-color 0.3s ease; font-weight: 700;
}
.day-cell:hover:not(.empty) { background: var(--color-bg-hover); transform: scale(1.05); }
.day-cell.today { background: var(--color-blue-light); border: 2px solid var(--color-blue); border-radius: var(--radius-md); box-shadow: 0 2px 0 var(--color-blue-dark); }
.day-cell.checked { background: var(--color-primary-bg); border: 2px solid var(--color-primary); border-radius: var(--radius-md); box-shadow: 0 2px 0 var(--color-primary-dark); }

.day-cell.checked.streak-start {
  border-radius: var(--radius-md) 0 0 var(--radius-md);
  position: relative;
}
.day-cell.checked.streak-start::after {
  content: ''; position: absolute; top: 50%; right: -6px;
  width: 10px; height: 3px; background: var(--color-primary);
  transform: translateY(-50%); border-radius: 2px;
}
.day-cell.checked.streak-end {
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  position: relative;
}
.day-cell.checked.streak-end::before {
  content: ''; position: absolute; top: 50%; left: -6px;
  width: 10px; height: 3px; background: var(--color-primary);
  transform: translateY(-50%); border-radius: 2px;
}
.day-cell.checked:not(.streak-start):not(.streak-end) {
  border-radius: 0; position: relative;
}
.day-cell.checked:not(.streak-start):not(.streak-end)::before {
  content: ''; position: absolute; top: 50%; left: -6px;
  width: 10px; height: 3px; background: var(--color-primary);
  transform: translateY(-50%); border-radius: 2px;
}
.day-cell.checked:not(.streak-start):not(.streak-end)::after {
  content: ''; position: absolute; top: 50%; right: -6px;
  width: 10px; height: 3px; background: var(--color-primary);
  transform: translateY(-50%); border-radius: 2px;
}
.day-cell.empty { cursor: default; }
.day-num { font-size: 14px; }
.checkin-dot {
  display: block; width: 8px; height: 8px; border-radius: 50%;
  background: var(--color-primary); margin: 4px auto 0;
  animation: pulse-soft 2s ease-in-out infinite;
}
.calendar-footer {
  display: flex; align-items: center; gap: 16px; margin-top: 20px; flex-wrap: wrap;
}
.calendar-summary {
  text-align: center; padding: 14px 20px;
  background: var(--color-primary-bg); border-radius: var(--radius-full);
  color: var(--color-primary-dark); font-weight: 800; font-size: 14px; flex: 1;
  transition: transform 0.2s ease; cursor: default;
}
.calendar-summary:hover { transform: scale(1.02); }
</style>
