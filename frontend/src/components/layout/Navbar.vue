<template>
  <nav class="navbar">
    <div class="navbar-inner">
      <div class="navbar-left">
        <router-link to="/" class="navbar-logo">
          <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
            <path d="M16 2 L16 30" stroke="#7a9e7e" stroke-width="1.5" stroke-linecap="round"/>
            <path d="M8 8 L16 14 L24 8" stroke="#7a9e7e" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M8 16 L16 22 L24 16" stroke="#a3c4a6" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            <path d="M8 24 L16 30 L24 24" stroke="#d4e8d5" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          </svg>
          <span class="logo-text">TiMo</span>
        </router-link>
      </div>
      <div class="navbar-center">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path"
          class="nav-item" :class="{ active: isActive(item.path) }">
          {{ item.label }}
        </router-link>
      </div>
      <div class="navbar-right">
        <el-dropdown trigger="click">
          <span class="user-btn">
            <div class="user-avatar">{{ userInitial }}</div>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="isAdmin" @click="$router.push('/admin')">管理后台</el-dropdown-item>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { logout } from '../../api/auth'

const router = useRouter()
const route = useRoute()

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
const userStore = useUserStore()

const userInitial = computed(() => {
  const n = userStore.userInfo?.nickname
  return n ? n.charAt(0).toUpperCase() : 'U'
})

const isAdmin = computed(() => {
  const role = userStore.userInfo?.role
  return role === 'SUPER_ADMIN' || role === 'ADMIN'
})

const navItems = [
  { path: '/', label: '首页' },
  { path: '/word-select', label: '学习' },
  { path: '/review', label: '复习' },
  { path: '/calendar', label: '日历' },
  { path: '/wordbank', label: '词库' },
  { path: '/stats', label: '统计' },
  { path: '/profile', label: '我的' },
]

function handleLogout() {
  logout().catch(() => {})
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(250, 249, 246, 0.88);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--color-border-lighter);
}

.navbar-inner {
  max-width: var(--content-max-width);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--navbar-height);
  padding: 0 32px;
}

.navbar-left {
  flex-shrink: 0;
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.logo-text {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: 0.05em;
}

.navbar-center {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-item {
  padding: 6px 14px;
  border-radius: 6px;
  text-decoration: none;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 600;
  transition: color 0.2s ease, background 0.2s ease;
  letter-spacing: 0.01em;
}

.nav-item:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-hover);
}

.nav-item.active {
  color: #fff;
  background: var(--color-primary);
  font-weight: 700;
}

.navbar-right {
  flex-shrink: 0;
}

.user-btn {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 2px;
  border-radius: 50%;
  transition: opacity 0.2s ease;
}

.user-btn:hover {
  opacity: 0.8;
}

.user-avatar {
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
}
</style>
