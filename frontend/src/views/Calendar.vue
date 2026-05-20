<template>
  <div class="calendar-page fade-in-up">
    <h2 class="page-title">学习日历</h2>

    <el-card shadow="hover" class="calendar-card">
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
          <span>本月打卡 <strong>{{ totalCheckins }}</strong> 天</span>
        </div>
        <el-button type="primary" class="checkin-btn" :loading="checkinLoading" @click="handleCheckin">
          今日打卡
        </el-button>
        <div class="timo-mini-inline">
          <TiMoFAB compact />
          <span class="timo-mini-hint">帮你分析学习规律</span>
        </div>
      </div>
    </el-card>

    <!-- Day detail drawer -->
    <el-drawer v-model="drawerVisible" :title="selectedDay?.date" size="320px">
      <template v-if="selectedDay">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="打卡状态">{{ selectedDay.checkedIn ? '已打卡' : '未打卡' }}</el-descriptions-item>
          <el-descriptions-item label="学习单词">{{ selectedDay.wordsStudied }} 个</el-descriptions-item>
          <el-descriptions-item label="学习时长">{{ selectedDay.studyMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="答题次数">{{ selectedDay.totalRecords }} 次</el-descriptions-item>
          <el-descriptions-item label="平均正确率">{{ selectedDay.avgGrade > 0 ? ((selectedDay.avgGrade - 1) / 3 * 100).toFixed(1) + '%' : '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getMonthly, checkin } from '../api/calendar'
import { useAgentStore } from '../stores/agent'
import { useUserStore } from '../stores/user'
import TiMoFAB from '../components/agent/TiMoFAB.vue'

const agentStore = useAgentStore()
const userStore = useUserStore()

const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const currentYear = ref(dayjs().year())
const currentMonth = ref(dayjs().month() + 1)
const daysMap = ref({})
const totalCheckins = ref(0)
const drawerVisible = ref(false)
const selectedDay = ref(null)
const checkinLoading = ref(false)

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

  // Calculate consecutive check-in streaks and mark start/end
  // Only connect cells within the same grid row (7 cells per row)
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

async function loadData() {
  try {
    const res = await getMonthly(currentYear.value, currentMonth.value)
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
  loadData()
}

function showDetail(day) {
  selectedDay.value = day
  drawerVisible.value = true
}

async function handleCheckin() {
  checkinLoading.value = true
  try {
    await checkin()
    ElMessage.success('打卡成功')
    await loadData()
  } catch {
    ElMessage.error('打卡失败，请稍后重试')
  } finally {
    checkinLoading.value = false
  }
}

onMounted(() => {
  agentStore.setCurrentPage('calendar')
  if (userStore.token) {
    loadData()
  }
})
</script>

<style scoped>
.calendar-page { max-width: 700px; margin: 0 auto; }
.page-title { font-size: 22px; font-weight: 900; color: var(--color-text-primary); margin: 0 0 20px; }

.calendar-card {
  border: 2px solid var(--color-border-lighter);
  box-shadow: 0 4px 0 var(--color-border-lighter);
}

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

/* Streak connection lines — only show between cells in the same row */
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

.checkin-btn {
  flex-shrink: 0;
  font-weight: 700;
  height: 42px;
  padding: 0 24px;
}

.timo-mini-inline {
  flex-shrink: 0;
  margin-left: auto;
}

.timo-mini-section {
  display: flex; align-items: center; gap: 8px; padding: 8px 0;
}

.timo-mini-hint {
  font-size: 12px; font-weight: 700; color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .calendar-page { padding: 0 8px; }
  .calendar-card { margin: 0; }
  .calendar-footer { flex-direction: column; align-items: stretch; }
  .checkin-btn { width: 100%; }
  .timo-mini-inline { display: none; }
}
</style>
