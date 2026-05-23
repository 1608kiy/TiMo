<template>
  <div class="admin-ai fade-in-up">
    <!-- Provider Section -->
    <div class="section-header">
      <span class="section-title">AI 厂商配置</span>
    </div>
    <div class="provider-grid">
      <div v-for="p in providers" :key="p.id" class="provider-card" :class="{ active: p.isActive }">
        <div class="provider-top">
          <div class="provider-name">{{ p.displayName }}</div>
          <el-tag :type="p.isActive ? 'success' : 'info'" size="small">{{ p.isActive ? '启用中' : '未启用' }}</el-tag>
        </div>
        <div class="provider-info">
          <div class="info-row"><span class="info-label">模型</span>{{ p.model }}</div>
          <div class="info-row"><span class="info-label">Base URL</span><span class="url-text">{{ p.baseUrl }}</span></div>
          <div class="info-row"><span class="info-label">Max Tokens</span>{{ p.maxTokens }}</div>
          <div class="info-row"><span class="info-label">Temperature</span>{{ p.temperature }}</div>
        </div>
        <div class="provider-actions">
          <el-button size="small" @click="showEditProvider(p)">编辑</el-button>
          <el-button size="small" type="success" @click="handleActivate(p)" :disabled="p.isActive">启用</el-button>
          <el-button size="small" type="danger" @click="handleDeleteProvider(p)">删除</el-button>
        </div>
      </div>
      <div class="provider-card add-card" @click="showAddProvider">
        <div class="add-icon">+</div>
        <div class="add-text">添加厂商</div>
      </div>
    </div>

    <!-- AI Stats -->
    <div class="section-header" style="margin-top: 24px;">
      <span class="section-title">AI 调用统计</span>
    </div>
    <div class="stats-grid">
      <div class="stat-card" v-for="card in statCards" :key="card.label">
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <!-- Logs Section -->
    <div class="section-header" style="margin-top: 24px;">
      <span class="section-title">AI 调用日志</span>
      <el-select v-model="logStatusFilter" placeholder="状态筛选" clearable style="width: 120px" @change="loadLogs">
        <el-option label="全部" value="" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAIL" />
      </el-select>
    </div>
    <div class="table-card">
      <el-table :data="logs" stripe v-loading="logsLoading" style="width: 100%" empty-text="暂无调用日志">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="provider" label="厂商" width="90" />
        <el-table-column prop="model" label="模型" width="140" />
        <el-table-column prop="totalTokens" label="Token" width="80" />
        <el-table-column prop="responseTimeMs" label="耗时(ms)" width="90" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination v-model:current-page="logPage" :page-size="15" :total="logTotal"
      layout="total, prev, pager, next" @current-change="loadLogs" style="margin-top: 12px; justify-content: flex-end;" />

    <!-- Provider dialog -->
    <el-dialog v-model="providerDialogVisible" :title="isEditProvider ? '编辑厂商' : '添加厂商'" width="480px" class="provider-dialog" :append-to-body="true">
      <el-form ref="providerFormRef" :model="providerForm" :rules="providerRules" label-width="100px">
        <el-form-item label="厂商标识" prop="providerName"><el-input v-model="providerForm.providerName" placeholder="deepseek" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="providerForm.displayName" placeholder="DeepSeek" /></el-form-item>
        <el-form-item label="Base URL" prop="baseUrl"><el-input v-model="providerForm.baseUrl" placeholder="https://api.deepseek.com" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="providerForm.apiKey" placeholder="留空则不修改" show-password /></el-form-item>
        <el-form-item label="模型" prop="model"><el-input v-model="providerForm.model" placeholder="deepseek-v4-flash" /></el-form-item>
        <el-form-item label="Max Tokens"><el-input-number v-model="providerForm.maxTokens" :min="1" :max="8192" /></el-form-item>
        <el-form-item label="Temperature"><el-input-number v-model="providerForm.temperature" :min="0" :max="2" :step="0.1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveProvider">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAIProviders, createAIProvider, updateAIProvider, activateAIProvider, deleteAIProvider, getAILogs, getAIStats } from '../../api/admin'

const providers = ref([])
const logs = ref([])
const logsLoading = ref(false)
const logPage = ref(1)
const logTotal = ref(0)
const logStatusFilter = ref('')
const aiStats = ref({})
const statCards = computed(() => [
  { label: '总调用次数', value: aiStats.value.totalCalls ?? '-' },
  { label: '成功次数', value: aiStats.value.successCalls ?? '-' },
  { label: '总 Token 消耗', value: aiStats.value.totalTokens ?? '-' },
  { label: '成功率', value: aiStats.value.successRate != null ? (aiStats.value.successRate * 100).toFixed(1) + '%' : '-' }
])

const providerDialogVisible = ref(false)
const isEditProvider = ref(false)
const editProviderId = ref(null)
const providerFormRef = ref(null)
const providerForm = ref({ providerName: '', displayName: '', baseUrl: '', apiKey: '', model: '', maxTokens: 2048, temperature: 0.7 })
const providerRules = {
  providerName: [{ required: true, message: '请输入厂商标识', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

onMounted(() => { loadProviders(); loadStats(); loadLogs() })

async function loadProviders() {
  try { const res = await getAIProviders(); providers.value = res.data } catch (e) { console.warn('AI providers load failed:', e); ElMessage.warning('AI 厂商列表加载失败') }
}

async function loadStats() {
  try { const res = await getAIStats(30); aiStats.value = res.data } catch (e) { console.warn('AI stats load failed:', e); ElMessage.warning('AI 统计数据加载失败') }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const params = { page: logPage.value - 1, size: 15 }
    if (logStatusFilter.value) params.status = logStatusFilter.value
    const res = await getAILogs(params)
    logs.value = res.data.content
    logTotal.value = res.data.totalElements
  } catch (e) { console.warn('AI logs load failed:', e); ElMessage.warning('AI 调用日志加载失败') } finally { logsLoading.value = false }
}

function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 19) : '-' }

function showAddProvider() {
  isEditProvider.value = false
  providerForm.value = { providerName: '', displayName: '', baseUrl: '', apiKey: '', model: '', maxTokens: 2048, temperature: 0.7 }
  providerDialogVisible.value = true
}

function showEditProvider(p) {
  isEditProvider.value = true
  editProviderId.value = p.id
  providerForm.value = { ...p }
  providerDialogVisible.value = true
}

async function handleSaveProvider() {
  if (!providerFormRef.value) return
  await providerFormRef.value.validate()
  try {
    if (isEditProvider.value) {
      await updateAIProvider(editProviderId.value, providerForm.value)
    } else {
      await createAIProvider(providerForm.value)
    }
    ElMessage.success('保存成功')
    providerDialogVisible.value = false
    loadProviders()
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
}

async function handleActivate(p) {
  try {
    await activateAIProvider(p.id)
    ElMessage.success(`${p.displayName} 已启用`)
    loadProviders()
  } catch (e) { ElMessage.error('操作失败') }
}

async function handleDeleteProvider(p) {
  try {
    await ElMessageBox.confirm(`确定删除 ${p.displayName} 吗？`, '确认')
    await deleteAIProvider(p.id)
    ElMessage.success('已删除')
    loadProviders()
  } catch { /* user cancelled confirm */ }
}
</script>

<style scoped>
.admin-ai { display: flex; flex-direction: column; gap: 16px; }

.section-header { margin-bottom: 4px; }
.section-title { font-size: 15px; font-weight: 800; color: var(--color-text-primary); }

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.provider-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.provider-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 3px 0 var(--color-primary-dark);
}

.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  cursor: pointer;
  border-style: dashed;
}

.add-card:hover {
  border-color: var(--color-primary-lighter);
  background: var(--color-primary-bg);
}

.add-icon { font-size: 28px; color: var(--color-text-muted); margin-bottom: 4px; }
.add-text { font-size: 13px; color: var(--color-text-secondary); font-weight: 600; }

.provider-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-lighter);
}

.provider-name { font-size: 15px; font-weight: 800; color: var(--color-text-primary); }

.provider-info { display: flex; flex-direction: column; gap: 6px; }
.info-row { font-size: 13px; color: var(--color-text-regular); }
.info-label { color: var(--color-text-secondary); margin-right: 8px; font-weight: 500; }
.url-text { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-muted); word-break: break-all; }

.provider-actions { margin-top: 14px; display: flex; gap: 8px; }

/* === Stats Grid === */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.stat-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 18px;
  text-align: center;
  box-shadow: 0 3px 0 var(--color-border-lighter);
}

.stat-value {
  font-size: 24px;
  font-weight: 900;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 600;
  margin-top: 4px;
}

.table-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 4px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  overflow: hidden;
}

/* === Dialog Override === */
.provider-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--color-border-lighter);
  margin-right: 0;
}

.provider-dialog :deep(.el-dialog__title) {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.provider-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.provider-dialog :deep(.el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--color-border-lighter);
}
</style>
