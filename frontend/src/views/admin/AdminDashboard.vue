<template>
  <div class="admin-dashboard fade-in-up">
    <!-- Stats Grid -->
    <div class="stats-grid">
      <div class="stat-card fade-in-up" v-for="(card, i) in statCards" :key="card.label" :style="{ animationDelay: (i * 0.06) + 's' }">
        <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="card.svg"></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- Charts -->
    <div class="charts-row">
      <div class="chart-card fade-in-up fade-in-up-delay-2">
        <div class="chart-header">
          <span class="chart-title">近 7 天 AI 调用趋势</span>
        </div>
        <div ref="trendChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card fade-in-up fade-in-up-delay-3">
        <div class="chart-header">
          <span class="chart-title">学习模式使用分布</span>
        </div>
        <div ref="modeChartRef" class="chart-box"></div>
      </div>
    </div>
    <div class="charts-row">
      <div class="chart-card fade-in-up fade-in-up-delay-4">
        <div class="chart-header">
          <span class="chart-title">考试类型分布</span>
        </div>
        <div ref="examChartRef" class="chart-box"></div>
      </div>
      <div></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardOverview, getDashboardTrend } from '../../api/admin'

const overview = ref({})
const trendChartRef = ref(null)
const modeChartRef = ref(null)
const examChartRef = ref(null)
let trendChart = null
let modeChart = null
let examChart = null

const statCards = computed(() => [
  { label: '总用户数', value: overview.value.totalUsers ?? '-', svg: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>', color: 'var(--color-primary-dark)', bg: 'var(--color-primary-bg)' },
  { label: '总单词数', value: overview.value.totalWords ?? '-', svg: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>', color: 'var(--color-blue-dark)', bg: 'var(--color-blue-light)' },
  { label: '今日 AI 调用', value: overview.value.todayAiCalls ?? '-', svg: '<circle cx="12" cy="12" r="3"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>', color: 'var(--color-orange-dark)', bg: 'var(--color-orange-light)' },
  { label: '今日 Token', value: overview.value.todayTokens ?? '-', svg: '<path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>', color: 'var(--color-red-dark)', bg: 'var(--color-red-light)' },
  { label: '本周 AI 调用', value: overview.value.weekAiCalls ?? '-', svg: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>', color: 'var(--color-purple-dark)', bg: 'var(--color-purple-light)' },
  { label: 'AI 成功率', value: overview.value.aiSuccessRate != null ? (overview.value.aiSuccessRate * 100).toFixed(1) + '%' : '-', svg: '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>', color: 'var(--color-primary-darker)', bg: 'var(--color-primary-bg)' }
])

const modeNameMap = {
  'QUICK_MEMORY': '快速记忆',
  'quick_memory': '快速记忆',
  'DEEP_LEARNING': '语境深度',
  'context_deep': '语境深度',
  'UNIFIED_REVIEW': '统一复习',
  'unified_review': '统一复习'
}
const modeColors = ['#7a9e7e', '#a3c4a6', '#c49a6c']

const examColors = ['#7a9e7e', '#a3c4a6', '#c49a6c', '#7a9baa', '#9a8aad', '#c47a7a']

function handleResize() {
  trendChart?.resize()
  modeChart?.resize()
  examChart?.resize()
}

onMounted(async () => {
  try {
    const [overviewRes, trendRes] = await Promise.all([getDashboardOverview(), getDashboardTrend(7)])
    overview.value = { ...overviewRes.data, aiDailyStats: trendRes.data.aiDailyStats }
  } catch (e) { console.warn('Dashboard load failed:', e) }

  await nextTick()
  initTrendChart()
  initModeChart()
  initExamChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  modeChart?.dispose()
  examChart?.dispose()
})

function initTrendChart() {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  const days = []
  const calls = []
  const tokens = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    days.push((d.getMonth() + 1) + '/' + d.getDate())
    calls.push(0)
    tokens.push(0)
  }
  if (overview.value.aiDailyStats) {
    overview.value.aiDailyStats.forEach(s => {
      const raw = String(s[0])
      const key = raw.includes('-') ? (() => { const p = raw.split('-'); return Number(p[1]) + '/' + Number(p[2]) })() : raw
      const idx = days.indexOf(key)
      if (idx >= 0) { calls[idx] = s[1]; tokens[idx] = s[2] }
    })
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['调用次数', 'Token 消耗'], bottom: 0, textStyle: { fontSize: 11 } },
    grid: { top: 16, right: 16, bottom: 36, left: 44 },
    xAxis: { type: 'category', data: days, axisLabel: { fontSize: 11, color: '#b0b0b0' }, axisLine: { lineStyle: { color: '#f0efeb' } } },
    yAxis: [
      { type: 'value', name: '次数', nameTextStyle: { fontSize: 11, color: '#b0b0b0' }, axisLabel: { fontSize: 11, color: '#b0b0b0' }, splitLine: { lineStyle: { color: '#f0efeb' } } },
      { type: 'value', name: 'Token', position: 'right', nameTextStyle: { fontSize: 11, color: '#b0b0b0' }, axisLabel: { fontSize: 11, color: '#b0b0b0' }, splitLine: { show: false } }
    ],
    series: [
      { name: '调用次数', type: 'bar', data: calls, itemStyle: { color: '#7a9e7e', borderRadius: [4, 4, 0, 0] }, barWidth: '40%' },
      { name: 'Token 消耗', type: 'line', yAxisIndex: 1, data: tokens, itemStyle: { color: '#c49a6c' }, smooth: true, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 6 }
    ]
  })
}

function initModeChart() {
  if (!modeChartRef.value) return
  modeChart = echarts.init(modeChartRef.value)
  const rawData = overview.value.modeDistribution || []
  const data = rawData.map((item, i) => ({
    value: item[1],
    name: modeNameMap[item[0]] || item[0],
    itemStyle: { color: modeColors[i % modeColors.length] }
  }))
  modeChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: { show: true, formatter: '{b}: {d}%', fontSize: 11 },
      data: data.length ? data : [{ value: 0, name: '暂无数据' }]
    }]
  })
}

function initExamChart() {
  if (!examChartRef.value) return
  examChart = echarts.init(examChartRef.value)
  const rawData = overview.value.examTypeDistribution || []
  const data = rawData.map((item, i) => ({
    value: item[1],
    name: item[0] || '未分类',
    itemStyle: { color: examColors[i % examColors.length] }
  }))
  examChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data: data.length ? data : [{ value: 0, name: '暂无数据' }]
    }]
  })
}
</script>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.stat-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 0 var(--color-border-lighter);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 900;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 600;
  margin-top: 2px;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.chart-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
}

.chart-header {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-lighter);
}

.chart-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.chart-box { height: 280px; }

@media (max-width: 768px) {
  .charts-row { grid-template-columns: 1fr; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
