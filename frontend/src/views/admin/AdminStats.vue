<template>
  <div class="admin-stats fade-in-up">
    <!-- Stats Grid -->
    <div class="stats-grid">
      <div class="stat-card fade-in-up" v-for="(card, i) in statCards" :key="card.label" :style="{ animationDelay: (i * 0.06) + 's' }">
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <!-- Charts -->
    <div class="charts-row">
      <div class="chart-card fade-in-up fade-in-up-delay-2">
        <div class="chart-header">
          <span class="chart-title">考试类型分布</span>
        </div>
        <div ref="examChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card fade-in-up fade-in-up-delay-3">
        <div class="chart-header">
          <span class="chart-title">近 30 天 AI 调用趋势</span>
        </div>
        <div ref="trendChartRef" class="chart-box"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getDashboardOverview, getDashboardTrend } from '../../api/admin'

const statsData = ref({})
const trendData = ref([])
const examChartRef = ref(null)
const trendChartRef = ref(null)
let examChart = null
let trendChart = null

const statCards = computed(() => [
  { label: '总用户数', value: statsData.value.totalUsers ?? '-' },
  { label: '总单词数', value: statsData.value.totalWords ?? '-' },
  { label: '今日 AI 调用', value: statsData.value.todayAiCalls ?? '-' },
  { label: '本周 AI 调用', value: statsData.value.weekAiCalls ?? '-' }
])

const colors = ['#7a9e7e', '#a3c4a6', '#c49a6c', '#7a9baa', '#9a8aad', '#c47a7a']

function handleResize() {
  examChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  try {
    const [overviewRes, trendRes] = await Promise.all([getDashboardOverview(), getDashboardTrend(30)])
    statsData.value = overviewRes.data
    trendData.value = trendRes.data.aiDailyStats || []
  } catch (e) { console.warn('Stats load failed:', e); ElMessage.warning('统计数据加载失败') }
  await nextTick()
  initExamChart()
  initTrendChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  examChart?.dispose()
  trendChart?.dispose()
})

function initExamChart() {
  if (!examChartRef.value) return
  examChart = echarts.init(examChartRef.value)
  const rawData = statsData.value.examTypeDistribution || []
  const data = rawData.map((item, i) => ({
    value: item[1],
    name: item[0] || '未分类',
    itemStyle: { color: colors[i % colors.length] }
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

function initTrendChart() {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  const days = []
  const calls = []
  for (let i = 29; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    days.push((d.getMonth() + 1) + '/' + d.getDate())
    calls.push(0)
  }
  if (trendData.value.length) {
    trendData.value.forEach(s => {
      const raw = String(s[0])
      const key = raw.includes('-') ? (() => { const p = raw.split('-'); return Number(p[1]) + '/' + Number(p[2]) })() : raw
      const idx = days.indexOf(key)
      if (idx >= 0) calls[idx] = s[1]
    })
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 16, right: 16, bottom: 28, left: 44 },
    xAxis: { type: 'category', data: days, axisLabel: { interval: 4, fontSize: 11, color: '#b0b0b0' }, axisLine: { lineStyle: { color: '#f0efeb' } } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11, color: '#b0b0b0' }, splitLine: { lineStyle: { color: '#f0efeb' } } },
    series: [{ type: 'line', data: calls, smooth: true, areaStyle: { opacity: 0.12, color: '#7a9e7e' }, itemStyle: { color: '#7a9e7e' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 5 }]
  })
}
</script>

<style scoped>
.admin-stats { display: flex; flex-direction: column; gap: 24px; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 24px;
  text-align: center;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 0 var(--color-border-lighter);
}

.stat-value {
  font-size: 28px;
  font-weight: 900;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 600;
  margin-top: 6px;
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

@media (max-width: 768px) { .charts-row { grid-template-columns: 1fr; } }
</style>
