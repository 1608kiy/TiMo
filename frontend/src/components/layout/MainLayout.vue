<template>
  <div class="main-layout" :class="{ 'has-banner': adminStore.isImpersonating }">
    <!-- Impersonate banner -->
    <div v-if="adminStore.isImpersonating" class="impersonate-banner">
      正在以管理员身份模拟查看用户 {{ adminStore.impersonateUser?.nickname || '' }} 的数据
      <button class="exit-btn" @click="handleExitImpersonate">返回后台</button>
    </div>

    <Navbar data-onboard="navbar" />
    <main class="main-content" data-onboard="content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in" appear>
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <TiMoFAB v-if="showGlobalFab" class="global-fab" />
    <TiMoDialog v-if="agentStore.isOpen" />
    <OnboardingOverlay />
    <OfflineSnackbar />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAgentStore } from '../../stores/agent'
import { useAdminStore } from '../../stores/admin'
import { exitImpersonate } from '../../api/admin'
import Navbar from './Navbar.vue'
import TiMoFAB from '../agent/TiMoFAB.vue'
import TiMoDialog from '../agent/TiMoDialog.vue'
import OnboardingOverlay from '../common/OnboardingOverlay.vue'
import OfflineSnackbar from '../OfflineSnackbar.vue'
import emitter from '../../events'

const router = useRouter()
const route = useRoute()
const agentStore = useAgentStore()
const adminStore = useAdminStore()

const showGlobalFab = computed(() => {
  const p = route.path
  if (p === '/' || p === '/login' || p === '/forgot-password') return false
  if (p.startsWith('/admin')) return false
  return true
})

async function handleExitImpersonate() {
  try {
    await exitImpersonate()
  } catch {}
  adminStore.stopImpersonate()
  const token = localStorage.getItem('admin_token')
  if (token) {
    localStorage.setItem('token', token)
    localStorage.removeItem('admin_token')
  }
  router.push('/admin')
}
let offStart, offSuccess, offMeltdown

onMounted(() => {
  offStart = emitter.on('api:call-start', () => agentStore.setTiMoState('thinking'))
  offSuccess = emitter.on('api:call-success', () => {
    agentStore.setTiMoState('idle')
    agentStore.setApiStatus('online')
  })
  offMeltdown = emitter.on('api:meltdown', () => {
    agentStore.setTiMoState('offline')
    agentStore.setApiStatus('offline')
  })
})

onBeforeUnmount(() => {
  offStart?.()
  offSuccess?.()
  offMeltdown?.()
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: var(--color-bg-page);
}

.impersonate-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  background: #ff8c00;
  color: #fff;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.exit-btn {
  background: #fff;
  color: #ff8c00;
  border: none;
  padding: 4px 16px;
  border-radius: 4px;
  font-weight: 700;
  cursor: pointer;
  font-size: 13px;
}

.exit-btn:hover { background: #fff3e0; }

.main-content {
  max-width: var(--content-max-width);
  margin: 0 auto;
  padding: var(--content-padding);
  padding-top: calc(var(--navbar-height) + var(--content-padding));
}

.has-banner .main-content {
  padding-top: calc(var(--navbar-height) + var(--content-padding) + 40px);
}

.global-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 50;
  background: #fff;
  padding: 8px 14px;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-full);
  box-shadow: 0 4px 0 var(--color-border-lighter);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.global-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 0 var(--color-border-lighter);
}

@media (max-width: 768px) {
  .global-fab {
    right: 16px;
    bottom: 16px;
  }
}
</style>
