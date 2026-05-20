<template>
  <div class="admin-layout">
    <!-- Impersonate banner -->
    <transition name="fade-slide">
      <div v-if="adminStore.isImpersonating" class="impersonate-banner">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        <span>正在模拟用户 <strong>{{ adminStore.impersonateUser?.nickname || '' }}</strong></span>
        <button class="banner-exit" @click="handleExitImpersonate">返回后台</button>
      </div>
    </transition>

    <!-- Sidebar -->
    <aside class="admin-sidebar">
      <div class="sidebar-header">
        <router-link to="/admin" class="logo-link">
          <div class="logo-mark">
            <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
              <path d="M16 2 L16 30" stroke="var(--color-primary)" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M8 8 L16 14 L24 8" stroke="var(--color-primary)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
              <path d="M8 16 L16 22 L24 16" stroke="var(--color-primary-light)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
              <path d="M8 24 L16 30 L24 24" stroke="var(--color-primary-lighter)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
          </div>
          <div class="logo-text">
            <span class="logo-name">TiMo</span>
            <span class="logo-sub">管理后台</span>
          </div>
        </router-link>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-group">
          <div class="nav-group-label">概览</div>
          <router-link to="/admin" class="nav-item" :class="{ 'nav-item--active': route.path === '/admin' }">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
            <span class="nav-label">系统总览</span>
          </router-link>
          <router-link to="/admin/stats" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
            <span class="nav-label">全局统计</span>
          </router-link>
        </div>

        <div class="nav-group">
          <div class="nav-group-label">管理</div>
          <router-link to="/admin/users" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            <span class="nav-label">用户管理</span>
          </router-link>
          <router-link to="/admin/words" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="13" y2="11"/></svg>
            <span class="nav-label">词库管理</span>
          </router-link>
          <router-link to="/admin/ai" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>
            <span class="nav-label">AI 配置</span>
          </router-link>
        </div>

        <div class="nav-group">
          <div class="nav-group-label">系统</div>
          <router-link to="/admin/settings" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
            <span class="nav-label">系统配置</span>
          </router-link>
          <router-link to="/admin/logs" class="nav-item" active-class="nav-item--active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            <span class="nav-label">操作日志</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/" class="back-link">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
          <span>返回用户端</span>
        </router-link>
        <div class="admin-info">
          <div class="admin-avatar">{{ (userStore.userInfo?.nickname || '管')[0] }}</div>
          <div class="admin-detail">
            <span class="admin-name">{{ userStore.userInfo?.nickname || '管理员' }}</span>
            <span class="admin-role">{{ roleLabel }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main content -->
    <main class="admin-main">
      <header class="admin-header">
        <h1 class="page-title">{{ currentPageTitle }}</h1>
        <div class="header-actions">
          <span class="admin-badge">{{ roleLabel }}</span>
        </div>
      </header>
      <div class="admin-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { useAdminStore } from '../../stores/admin'
import { exitImpersonate } from '../../api/admin'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const adminStore = useAdminStore()

const roleLabel = computed(() => {
  const role = userStore.userInfo?.role
  if (role === 'SUPER_ADMIN') return '超级管理员'
  if (role === 'ADMIN') return '管理员'
  return '用户'
})

const currentPageTitle = computed(() => {
  const map = {
    '/admin': '系统总览',
    '/admin/users': '用户管理',
    '/admin/words': '词库管理',
    '/admin/ai': 'AI 配置',
    '/admin/stats': '全局统计',
    '/admin/settings': '系统配置',
    '/admin/logs': '操作日志'
  }
  return map[route.path] || '管理后台'
})

async function handleExitImpersonate() {
  try {
    await exitImpersonate()
  } catch (e) { console.warn('Exit impersonate failed:', e) }
  adminStore.stopImpersonate()
  const token = localStorage.getItem('admin_token')
  if (token) {
    localStorage.setItem('token', token)
    localStorage.removeItem('admin_token')
  }
  router.push('/admin')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg-page);
}

/* === Impersonate Banner === */
.impersonate-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  background: var(--color-orange);
  color: #fff;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.banner-exit {
  background: rgba(255,255,255,0.2);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.3);
  padding: 3px 14px;
  border-radius: var(--radius-full);
  font-weight: 700;
  cursor: pointer;
  font-size: 12px;
  font-family: var(--font-family);
  transition: background 0.2s ease;
  margin-left: 4px;
}

.banner-exit:hover { background: rgba(255,255,255,0.35); }

/* === Sidebar === */
.admin-sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid var(--color-border-lighter);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar-header {
  padding: 20px 18px;
  border-bottom: 1px solid var(--color-border-lighter);
}

.logo-link {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.logo-name {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: 0.02em;
  line-height: 1.2;
}

.logo-sub {
  font-size: 11px;
  color: var(--color-text-muted);
  font-weight: 500;
  letter-spacing: 0.03em;
}

/* === Nav === */
.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-group {
  margin-bottom: 4px;
}

.nav-group-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 8px 12px 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s ease;
  position: relative;
}

.nav-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.nav-item--active {
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  font-weight: 700;
}

.nav-item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--color-primary);
}

.nav-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  opacity: 0.6;
  transition: opacity 0.2s ease;
}

.nav-item:hover .nav-icon,
.nav-item--active .nav-icon {
  opacity: 1;
}

/* === Footer === */
.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid var(--color-border-lighter);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  text-decoration: none;
  font-size: 12px;
  font-weight: 600;
  transition: color 0.2s ease;
}

.back-link:hover { color: var(--color-primary-dark); }

.admin-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.admin-detail {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.admin-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.admin-role {
  font-size: 11px;
  color: var(--color-text-muted);
  font-weight: 500;
}

/* === Main === */
.admin-main {
  flex: 1;
  margin-left: 220px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.admin-header {
  background: rgba(250, 249, 246, 0.88);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  padding: 0 28px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border-lighter);
  position: sticky;
  top: 0;
  z-index: 50;
}

.page-title {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
  letter-spacing: 0.01em;
}

.admin-badge {
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 700;
  border: 1px solid var(--color-primary-lighter);
}

.admin-content {
  flex: 1;
  padding: 24px 28px;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
}

/* === Transitions === */
.fade-slide-enter-active { transition: all 0.3s ease; }
.fade-slide-leave-active { transition: all 0.2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(-8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-8px); }

@media (max-width: 768px) {
  .admin-sidebar { width: 56px; }
  .logo-text, .nav-label, .nav-group-label, .sidebar-footer { display: none; }
  .sidebar-header { padding: 16px 12px; }
  .logo-link { justify-content: center; }
  .nav-item { justify-content: center; padding: 10px; }
  .nav-item--active::before { display: none; }
  .admin-main { margin-left: 56px; }
  .admin-content { padding: 16px; }
}
</style>

<!-- Global dialog styles for admin panel -->
<style>
.admin-layout ~ .el-overlay {
  background-color: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(2px);
}

.admin-layout ~ .el-overlay .el-dialog {
  border-radius: var(--radius-md);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
}
</style>
