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
        <keep-alive :include="cachedPages">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </keep-alive>
      </router-view>
    </main>
    <TiMoDialog v-if="agentStore.isOpen" />
    <OnboardingOverlay />
    <OfflineSnackbar />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '../../stores/agent'
import { useAdminStore } from '../../stores/admin'
import { exitImpersonate } from '../../api/admin'
import Navbar from './Navbar.vue'
import TiMoDialog from '../agent/TiMoDialog.vue'
import OnboardingOverlay from '../common/OnboardingOverlay.vue'
import OfflineSnackbar from '../OfflineSnackbar.vue'
import emitter from '../../events'

const router = useRouter()
const agentStore = useAgentStore()
const adminStore = useAdminStore()

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
const cachedPages = ['Wordbank', 'Stats', 'Profile', 'Calendar']

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
</style>
