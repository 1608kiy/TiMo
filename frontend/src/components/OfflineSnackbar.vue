<template>
  <transition name="snackbar-slide">
    <div v-if="visible" class="offline-snackbar" :class="variant">
      <span class="snackbar-icon">{{ variant === 'offline' ? '&#x1F4F5;' : '&#x2705;' }}</span>
      <span class="snackbar-text">{{ variant === 'offline' ? '网络已断开，学习进度将暂存本地' : '网络已恢复' }}</span>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useNetwork } from '../composables/useNetwork'

const { isOnline } = useNetwork()

const visible = ref(false)
const variant = ref('offline') // 'offline' | 'online'
let dismissTimer = null

watch(isOnline, (online) => {
  clearTimeout(dismissTimer)
  if (!online) {
    variant.value = 'offline'
    visible.value = true
    // Offline message stays visible until reconnected
  } else if (visible.value) {
    // Was offline, now recovered
    variant.value = 'online'
    visible.value = true
    dismissTimer = setTimeout(() => {
      visible.value = false
    }, 3000)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  clearTimeout(dismissTimer)
})
</script>

<style scoped>
.offline-snackbar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 700;
  box-shadow: var(--shadow-lg);
  pointer-events: none;
  white-space: nowrap;
}

.offline-snackbar.offline {
  background: var(--color-red-light);
  color: var(--color-red-dark);
  border: 2px solid var(--color-red);
}

.offline-snackbar.online {
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  border: 2px solid var(--color-primary);
}

.snackbar-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.snackbar-text {
  letter-spacing: 0.01em;
}

/* Slide-up transition */
.snackbar-slide-enter-active,
.snackbar-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.snackbar-slide-enter-from,
.snackbar-slide-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(16px);
}
</style>
