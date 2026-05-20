<template>
  <transition name="fade">
    <div v-if="visible" class="loading-overlay">
      <div class="loading-content">
        <div class="timo-bounce">
          <img src="/agent/idle.png" alt="TiMo" class="timo-loading-img" />
        </div>
        <p class="loading-text">{{ text }}</p>
      </div>
    </div>
  </transition>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  text: { type: String, default: '加载中...' }
})
</script>

<style scoped>
.loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(250, 249, 246, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.timo-bounce {
  animation: timoBounce 1.2s ease-in-out infinite;
}

.timo-loading-img {
  width: 64px;
  height: 64px;
  object-fit: contain;
}

.loading-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

@keyframes timoBounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
