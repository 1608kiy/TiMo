<template>
  <div class="admin-users fade-in-up">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="keyword" placeholder="搜索昵称或邮箱" clearable style="width: 220px" @clear="loadUsers" @keyup.enter="loadUsers" />
        <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px" @change="loadUsers">
          <el-option label="全部" value="" />
          <el-option label="普通用户" value="USER" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="超级管理员" value="SUPER_ADMIN" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px" @change="loadUsers">
          <el-option label="全部" value="" />
          <el-option label="正常" value="ACTIVE" />
          <el-option label="封禁" value="BANNED" />
        </el-select>
        <el-button type="primary" @click="loadUsers">查询</el-button>
      </div>
      <div class="toolbar-meta">
        共 <strong>{{ total }}</strong> 位用户
      </div>
    </div>

    <!-- User List -->
    <div class="user-list" v-loading="loading">
      <div v-for="user in users" :key="user.id" class="user-card-wrap">
        <div class="user-card fade-in-up" :class="{ expanded: expandedId === user.id }">
          <div class="user-avatar">{{ (user.nickname || 'U')[0] }}</div>
          <div class="user-info">
            <div class="user-name-row">
              <span class="user-name">{{ user.nickname }}</span>
              <el-tag :type="roleTagType(user.role)" size="small" class="user-tag">{{ roleLabel(user.role) }}</el-tag>
              <el-tag v-if="user.status !== 'ACTIVE'" type="danger" size="small" class="user-tag">封禁</el-tag>
            </div>
            <div class="user-meta">
              <span>{{ user.email }}</span>
              <span class="meta-dot">·</span>
              <span>{{ formatTime(user.lastLoginAt) }}</span>
            </div>
          </div>
          <div class="user-actions">
            <button class="action-chip" :class="{ active: expandedId === user.id }" @click="toggleDetail(user.id)">详情</button>
            <button class="action-chip" @click="showRoleDialog(user)" :disabled="user.id === currentUserId">角色</button>
            <button class="action-chip warning" @click="handleImpersonate(user)" :disabled="user.role === 'SUPER_ADMIN'">模拟</button>
            <button class="action-chip" :class="user.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(user)" :disabled="user.id === currentUserId">
              {{ user.status === 'ACTIVE' ? '封禁' : '解封' }}
            </button>
            <button class="action-chip danger" @click="handleDeleteUser(user)" :disabled="user.id === currentUserId">删除</button>
          </div>
        </div>

        <!-- Inline Detail -->
        <transition name="expand">
          <div v-if="expandedId === user.id" class="user-detail">
            <div class="detail-grid">
              <div class="detail-cell"><span class="detail-label">ID</span><span class="detail-value">{{ user.id }}</span></div>
              <div class="detail-cell"><span class="detail-label">角色</span><span class="detail-value">{{ roleLabel(user.role) }}</span></div>
              <div class="detail-cell"><span class="detail-label">状态</span><span class="detail-value">{{ user.status === 'ACTIVE' ? '正常' : '封禁' }}</span></div>
              <div class="detail-cell"><span class="detail-label">考试类型</span><span class="detail-value">{{ user.examType || '-' }}</span></div>
              <div class="detail-cell"><span class="detail-label">目标词汇</span><span class="detail-value">{{ user.targetVocab }}</span></div>
              <div class="detail-cell"><span class="detail-label">每日新词</span><span class="detail-value">{{ user.dailyNewLimit }}</span></div>
              <div class="detail-cell"><span class="detail-label">注册时间</span><span class="detail-value">{{ formatTime(user.createdAt) }}</span></div>
              <div class="detail-cell"><span class="detail-label">最后登录</span><span class="detail-value">{{ formatTime(user.lastLoginAt) }}</span></div>
            </div>
          </div>
        </transition>
      </div>

      <div v-if="!loading && users.length === 0" class="empty-state">
        <span class="empty-text">暂无用户数据</span>
      </div>
    </div>

    <!-- Pagination -->
    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadUsers"
      style="margin-top: 16px; justify-content: flex-end;"
    />

    <!-- Role dialog -->
    <el-dialog v-model="roleDialogVisible" title="修改角色" width="380px" :append-to-body="true">
      <el-select v-model="newRole" style="width: 100%">
        <el-option label="普通用户" value="USER" />
        <el-option label="管理员" value="ADMIN" />
        <el-option v-if="isSuperAdmin" label="超级管理员" value="SUPER_ADMIN" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRoleChange">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateUserRole, updateUserStatus, deleteAdminUser, impersonateUser } from '../../api/admin'
import { useUserStore } from '../../stores/user'
import { useAdminStore } from '../../stores/admin'

const router = useRouter()
const userStore = useUserStore()
const adminStore = useAdminStore()

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref('')
const page = ref(1)
const pageSize = 15
const total = ref(0)

const roleDialogVisible = ref(false)
const selectedUserId = ref(null)
const newRole = ref('USER')

const expandedId = ref(null)
const currentUserId = computed(() => userStore.userInfo?.userId)
const isSuperAdmin = computed(() => userStore.userInfo?.role === 'SUPER_ADMIN')

onMounted(() => loadUsers())

async function loadUsers() {
  loading.value = true
  try {
    const res = await getAdminUsers({
      keyword: keyword.value || undefined,
      role: filterRole.value || undefined,
      status: filterStatus.value || undefined,
      page: page.value - 1,
      size: pageSize
    })
    users.value = res.data.content
    total.value = res.data.totalElements
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

function roleLabel(role) {
  if (role === 'SUPER_ADMIN') return '超级管理员'
  if (role === 'ADMIN') return '管理员'
  return '普通用户'
}

function roleTagType(role) {
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'warning'
  return 'info'
}

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

function toggleDetail(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function showRoleDialog(row) {
  selectedUserId.value = row.id
  newRole.value = row.role || 'USER'
  roleDialogVisible.value = true
}

async function confirmRoleChange() {
  try {
    await updateUserRole(selectedUserId.value, newRole.value)
    ElMessage.success('角色已更新')
    roleDialogVisible.value = false
    loadUsers()
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
}

async function toggleStatus(row) {
  const newStatus = row.status === 'ACTIVE' ? 'BANNED' : 'ACTIVE'
  const action = newStatus === 'BANNED' ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 ${row.nickname} 吗？`, '确认')
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(`已${action}`)
    loadUsers()
  } catch { /* user cancelled confirm */ }
}

async function handleDeleteUser(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${row.nickname}（${row.email}）吗？此操作不可恢复。`, '确认删除', { type: 'warning' })
    await deleteAdminUser(row.id)
    ElMessage.success('已删除')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function handleImpersonate(row) {
  try {
    await ElMessageBox.confirm(`模拟登录用户 ${row.nickname}？5分钟后自动失效。`, '确认')
    localStorage.setItem('admin_token', localStorage.getItem('token'))
    const res = await impersonateUser(row.id)
    localStorage.setItem('token', res.data.token)
    adminStore.startImpersonate(res.data.targetUser)
    router.push('/')
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
}
</script>

<style scoped>
.admin-users {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* === Toolbar === */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-meta {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.toolbar-meta strong {
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

/* === User List === */
.user-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: all 0.2s ease;
}

.user-card:hover {
  border-color: var(--color-primary-lighter);
  box-shadow: 0 3px 0 var(--color-primary-lighter);
}

/* === Avatar === */
.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 15px;
  flex-shrink: 0;
}

/* === User Info === */
.user-info {
  flex: 1;
  min-width: 0;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 3px;
}

.user-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.user-tag {
  flex-shrink: 0;
}

.user-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-dot {
  color: var(--color-text-muted);
}

/* === Actions === */
.user-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-chip {
  padding: 5px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--color-border-light);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-family);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.action-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
  background: var(--color-primary-bg);
}

.action-chip:active {
  transform: scale(0.96);
}

.action-chip:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-chip.danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

.action-chip.success:hover {
  border-color: var(--color-success);
  color: var(--color-success);
  background: var(--color-success-bg);
}

.action-chip.warning:hover {
  border-color: var(--color-warning);
  color: var(--color-warning-dark);
  background: var(--color-warning-bg);
}

/* === Empty === */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.empty-text {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 600;
}

/* === Card Wrap + Inline Detail === */
.user-card-wrap {
  display: flex;
  flex-direction: column;
}

.user-card.expanded {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  border-bottom-color: transparent;
}

.action-chip.active {
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
  background: var(--color-primary-bg);
}

/* === Inline Detail === */
.user-detail {
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-top: none;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  padding: 16px 20px;
  overflow: hidden;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px 24px;
}

.detail-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.03em;
}

.detail-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
}

/* === Expand Transition === */
.expand-enter-active {
  transition: all 0.25s ease;
}

.expand-leave-active {
  transition: all 0.2s ease;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

/* === Responsive === */
@media (max-width: 768px) {
  .user-card {
    flex-wrap: wrap;
    gap: 10px;
  }

  .user-actions {
    width: 100%;
    justify-content: flex-end;
    padding-top: 8px;
    border-top: 1px solid var(--color-border-lighter);
  }
}
</style>
