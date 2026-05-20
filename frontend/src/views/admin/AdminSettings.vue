<template>
  <div class="admin-settings fade-in-up">
    <div class="section-header">
      <span class="section-title">系统参数配置</span>
      <el-button type="primary" @click="handleSaveAll" :loading="saving">保存全部</el-button>
    </div>

    <div class="config-groups">
      <div v-for="group in configGroups" :key="group.title" class="config-group fade-in-up">
        <div class="group-header">
          <div class="group-title">{{ group.title }}</div>
          <div class="group-badge">{{ group.badge }}</div>
        </div>
        <div class="group-desc">{{ group.description }}</div>
        <div v-if="group.formulas" class="group-formula">
          <span class="formula-label">核心公式</span>
          <div class="formula-list">
            <div v-for="(f, i) in group.formulas" :key="i" class="formula-line">
              <MathFormula v-if="f.latex" :expr="f.latex" :display="true" />
              <span v-if="f.note" class="formula-note">{{ f.note }}</span>
            </div>
          </div>
        </div>

        <div class="config-list">
          <div v-for="item in group.items" :key="item.key" class="config-item">
            <div class="config-info">
              <div class="config-row">
                <span class="config-key">{{ item.key }}</span>
                <span class="config-range" v-if="item.range">{{ item.range }}</span>
              </div>
              <div class="config-desc">{{ item.description }}</div>
              <div v-if="item.detail" class="config-detail">{{ item.detail }}</div>
            </div>
            <el-input-number v-if="item.type === 'number'" v-model.number="configs[item.key]" style="width: 180px" size="small" :min="item.min" :max="item.max" :step="item.step || 0.1" :precision="item.precision ?? 1" />
            <el-input v-else v-model="configs[item.key]" style="width: 180px" size="small" :type="item.key === 'admin_secret' ? 'password' : 'text'" :show-password="item.key === 'admin_secret'" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemConfigs, batchUpdateConfigs } from '../../api/admin'
import MathFormula from '../../components/common/MathFormula.vue'

const configs = reactive({})
const saving = ref(false)

const configGroups = [
  {
    title: 'FSRS 核心参数',
    badge: 'Free Spaced Repetition Scheduler',
    description: 'FSRS 算法用于计算每个单词的最佳复习间隔。稳定性（Stability）决定遗忘曲线的半衰期，难度（Difficulty）反映单词本身的记忆难度。新词默认稳定性为 1 天，难度为中等 5 分。',
    formulas: [
      { latex: 'R(t) = e^{\\ln(0.9) \\cdot t / S}', note: '遗忘曲线：当 t = S 时，R = 0.9' },
      { latex: 'S\' = S \\cdot \\left(1 + e^{0.1}(11 - D) \\cdot S^{-0.2}(e^{0.05(1-R)} - 1)\\right)', note: '稳定性更新（成功，grade ≥ 3）' },
      { latex: 'D\' = D - 0.1 + (4 - g)(0.1 + (4 - g) \\cdot 0.02)', note: '难度更新' }
    ],
    items: [
      {
        key: 'fsrs_default_stability',
        description: '新词初始稳定性 S₀（单位：天）',
        detail: '即首次复习间隔。S₀ = 1 表示新词学完 1 天后达到 90% 遗忘阈值，触发首次复习。稳定性会随复习表现动态调整。',
        range: '[0.5, 1.5]',
        type: 'number', min: 0.5, max: 1.5, step: 0.1, precision: 1
      },
      {
        key: 'fsrs_default_difficulty',
        description: '新词初始难度 D₀（1-10 量表）',
        detail: '难度越高，复习后稳定性增长越慢。D₀ = 5 为中等难度。完美回忆（grade=4）会降低难度，遗忘（grade=1）会增加难度。',
        range: '[1.0, 10.0]',
        type: 'number', min: 1.0, max: 10.0, step: 0.5, precision: 1
      }
    ]
  },
  {
    title: '动态遗忘因子 (DF)',
    badge: 'Dynamic Forgetting Factor · 自研扩展',
    description: 'DF 是本系统对标准 FSRS 的核心创新扩展。它根据用户实时行为（反应时间、准确率、提示使用）计算一个乘性调节因子，用于修正标准 FSRS 的稳定性预测。DF ∈ [0.5, 1.5]，值越大表示用户掌握越好。',
    formulas: [
      { latex: 'DF = \\lambda_{rt} \\cdot \\lambda_{acc}', note: 'quick_memory / unified_review 模式' },
      { latex: '\\lambda_{rt} = 1 + \\theta_{rt} \\cdot \\left(1 - \\frac{t - \\mu}{2\\sigma}\\right)', note: '反应时间因子，t 为实际答题时间' },
      { latex: '\\lambda_{acc} = 1 + \\theta_{acc} \\cdot (p_{correct} - 0.8)', note: '准确率因子，基于历史正确率' },
      { latex: "S_{final} = \\mathrm{clamp}(S' \\cdot DF,\\ 0.5,\\ 1.5)", note: '最终稳定性 = FSRS 输出 × DF' }
    ],
    items: [
      {
        key: 'df_theta1',
        description: 'θ_rt — 反应时间灵敏度系数',
        detail: '控制反应时间对 DF 的影响强度。用户答题越快于其个人均值 μ，λ_rt 越大，稳定性增益越高。',
        range: 'λ_rt ∈ [0.7, 1.3]',
        type: 'number', min: 0.7, max: 1.3, step: 0.05, precision: 2
      },
      {
        key: 'df_theta2',
        description: 'θ_acc — 历史准确率灵敏度系数',
        detail: '控制准确率对 DF 的影响强度。正确率超过 80% 时 λ_acc > 1（增益），低于 80% 时 < 1（惩罚）。',
        range: 'λ_acc ∈ [0.7, 1.3]',
        type: 'number', min: 0.7, max: 1.3, step: 0.05, precision: 2
      }
    ]
  },
  {
    title: '冷启动参数',
    badge: 'Cold Start',
    description: '当用户累计正确答题数不足 30 次时，系统缺乏足够的个人行为数据，使用预设的冷启动均值 μ 和标准差 σ 作为反应时间的估计参数，避免早期数据波动导致的不稳定调度。',
    formulas: [
      { latex: '\\lambda_{rt} = 1 + \\theta_{rt} \\cdot \\left(1 - \\frac{t - \\mu}{2\\sigma}\\right)', note: 'correctCount < 30 时，μ 和 σ 使用冷启动默认值' }
    ],
    items: [
      {
        key: 'cold_start_mu',
        description: 'μ — 冷启动平均反应时间（秒）',
        detail: '新用户的默认平均反应时间。μ = 8 表示系统假设新用户平均每道题用时 8 秒。当用户积累 30 次正确答题后，自动切换为用户个人历史均值。',
        range: '单位：秒',
        type: 'number', min: 1, max: 30, step: 1, precision: 0
      },
      {
        key: 'cold_start_sigma',
        description: 'σ — 冷启动反应时间标准差（秒）',
        detail: '新用户的默认反应时间波动范围。σ = 3 表示大部分答题时间落在 5-11 秒区间（μ ± σ）。用于标准化反应时间偏差。',
        range: '单位：秒',
        type: 'number', min: 1, max: 15, step: 1, precision: 0
      }
    ]
  },
  {
    title: '疲劳检测与熔断保护',
    badge: 'Fatigue & Circuit Breaker',
    description: '当用户连续学习时间过长或连续答错时，系统触发保护机制：疲劳检测在连续学习超过阈值后降低复习优先级；熔断机制在连续失败后暂停该词的复习调度，防止无效重复。',
    formulas: null,
    items: [
      {
        key: 'fatigue_threshold_minutes',
        description: '疲劳检测时间阈值（分钟）',
        detail: '用户连续学习超过此时间后，系统标记为疲劳状态，降低新词推送频率并优先复习已掌握的词。默认 20 分钟。',
        range: '单位：分钟',
        type: 'number', min: 5, max: 60, step: 5, precision: 0
      },
      {
        key: 'circuit_breaker_threshold',
        description: '熔断触发失败次数',
        detail: '同一单词连续答错达到此次数后，触发熔断保护，暂时停止该词的复习调度，避免用户在疲劳状态下反复失败导致负面情绪。',
        range: '次数',
        type: 'number', min: 2, max: 10, step: 1, precision: 0
      },
      {
        key: 'circuit_breaker_reset_ms',
        description: '熔断恢复冷却时间（毫秒）',
        detail: '熔断触发后，等待此时间后自动恢复该词的复习调度。默认 60000ms（1 分钟），给予用户短暂休息时间。',
        range: '单位：毫秒',
        type: 'number', min: 10000, max: 300000, step: 10000, precision: 0
      }
    ]
  },
  {
    title: '安全配置',
    badge: 'Security',
    description: null,
    formulas: null,
    items: [
      {
        key: 'admin_secret',
        description: '管理员身份验证密钥',
        detail: null,
        range: null
      }
    ]
  }
]

onMounted(async () => {
  try {
    const res = await getSystemConfigs()
    res.data.forEach(c => { configs[c.configKey] = c.configValue })
  } catch (e) { console.warn('Settings load failed:', e) }
})

async function handleSaveAll() {
  saving.value = true
  try {
    await batchUpdateConfigs({ ...configs })
    ElMessage.success('保存成功')
  } catch (e) { ElMessage.error('保存失败') }
  finally { saving.value = false }
}
</script>

<style scoped>
.admin-settings { display: flex; flex-direction: column; gap: 20px; }

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.config-group {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
}

.group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.group-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-primary-dark);
}

.group-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-bg-secondary, #f5f5f5);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.group-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  margin-bottom: 12px;
}

.group-formula {
  background: var(--color-primary-bg, #f0f7f0);
  border: 1px solid var(--color-primary-lighter, #d4e4d6);
  border-radius: var(--radius-sm, 6px);
  padding: 14px 18px;
  margin-bottom: 16px;
  display: flex;
  gap: 14px;
}

.formula-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-primary-dark);
  white-space: nowrap;
  padding-top: 2px;
}

.formula-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.formula-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.formula-line :deep(.katex) {
  font-size: 1.1em;
  color: var(--color-text-primary);
}

.formula-note {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-lighter);
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.config-info { flex: 1; min-width: 0; }

.config-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.config-key {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

.config-range {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-bg-secondary, #f5f5f5);
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

.config-desc {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 1.5;
}

.config-detail {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
</style>
