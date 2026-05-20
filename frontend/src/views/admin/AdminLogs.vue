<template>
  <div class="admin-logs fade-in-up">
    <div class="toolbar">
      <el-select v-model="filterType" placeholder="操作类型" clearable style="width: 160px" @change="loadLogs">
        <el-option label="全部" value="" />
        <el-option label="登录" value="ADMIN_SECRET_VERIFY" />
        <el-option label="角色修改" value="ROLE_CHANGE" />
        <el-option label="封禁" value="BAN" />
        <el-option label="解封" value="UNBAN" />
        <el-option label="AI配置" value="AI_CONFIG_UPDATE" />
        <el-option label="系统配置" value="SYSTEM_CONFIG_UPDATE" />
        <el-option label="批量导入" value="WORD_IMPORT" />
        <el-option label="模拟登录" value="IMPERSONATE" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" @change="loadLogs" />
      <el-button type="primary" @click="loadLogs">查询</el-button>
    </div>

    <div class="table-card">
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="adminEmail" label="操作人" width="160" />
        <el-table-column label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="opTagType(row.operationType)" size="small">{{ opLabel(row.operationType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标类型" width="90" />
        <el-table-column prop="targetId" label="目标ID" width="70" />
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="120" />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-pagination v-model:current-page="page" :page-size="20" :total="total"
      layout="total, prev, pager, next" @current-change="loadLogs" style="margin-top: 16px; justify-content: flex-end;" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOperationLogs } from '../../api/admin'

const logs = ref([])
const loading = ref(false)
const filterType = ref('')
const dateRange = ref(null)
const page = ref(1)
const total = ref(0)

onMounted(() => loadLogs())

async function loadLogs() {
  loading.value = true
  try {
    const params = { type: filterType.value || undefined, page: page.value - 1, size: 20 }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getOperationLogs(params)
    logs.value = res.data.content
    total.value = res.data.totalElements
  } catch (e) { console.warn('Logs load failed:', e) } finally { loading.value = false }
}

function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 19) : '-' }

function opLabel(type) {
  const map = {
    ADMIN_SECRET_VERIFY: '密钥验证', ROLE_CHANGE: '角色修改', BAN: '封禁', UNBAN: '解封',
    AI_CONFIG_CREATE: '新增AI厂商', AI_CONFIG_UPDATE: '修改AI配置', AI_CONFIG_ACTIVATE: '启用AI厂商',
    AI_CONFIG_DELETE: '删除AI厂商', SYSTEM_CONFIG_UPDATE: '系统配置', WORD_CREATE: '新增单词',
    WORD_UPDATE: '编辑单词', WORD_DELETE: '删除单词', WORD_IMPORT: '批量导入',
    IMPERSONATE: '模拟登录', EXIT_IMPERSONATE: '退出模拟', DELETE_USER: '删除用户'
  }
  return map[type] || type
}

function opTagType(type) {
  if (['BAN', 'DELETE_USER', 'WORD_DELETE', 'AI_CONFIG_DELETE'].includes(type)) return 'danger'
  if (['ROLE_CHANGE', 'SYSTEM_CONFIG_UPDATE', 'AI_CONFIG_UPDATE'].includes(type)) return 'warning'
  if (['IMPERSONATE'].includes(type)) return 'info'
  return 'success'
}
</script>

<style scoped>
.admin-logs { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 10px; align-items: center; }

.table-card {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: 4px;
  box-shadow: 0 3px 0 var(--color-border-lighter);
  overflow: hidden;
}
</style>
