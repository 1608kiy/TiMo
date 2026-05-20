<template>
  <div class="timo-avatar" :class="[`state-${state}`, `size-${size}`]">
    <div class="timo-glow" v-if="state === 'success' || state === 'alert'"></div>
    <img :key="animKey" :src="imageSrc" :alt="state" class="timo-img" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  state: { type: String, default: 'idle' },
  size: { type: String, default: 'normal' }
})

const animKey = ref(0)

watch(() => props.state, (newState) => {
  if (newState === 'success' || newState === 'alert') {
    animKey.value++
  }
})

const imageMap = {
  idle: '/agent/idle.png',
  thinking: '/agent/thinking.png',
  alert: '/agent/alert.png',
  success: '/agent/success.png',
  offline: '/agent/offline.png'
}

const imageSrc = computed(() => imageMap[props.state] || imageMap.idle)
</script>

<style scoped>
.timo-avatar {
  position: relative; display: inline-flex; align-items: center; justify-content: center;
}
.timo-avatar.size-small { width: 36px; height: 36px; }
.timo-avatar.size-normal { width: 52px; height: 52px; }
.timo-avatar.size-large { width: 88px; height: 88px; }

.timo-img { width: 100%; height: 100%; object-fit: contain; position: relative; z-index: 1; }

/* Glow backdrop */
.timo-glow {
  position: absolute; inset: -6px; border-radius: 50%; z-index: 0;
  opacity: 0; animation: glow-pulse 1.5s ease-in-out infinite;
}
.state-success .timo-glow { background: radial-gradient(circle, rgba(103,194,58,0.35) 0%, transparent 70%); }
.state-alert .timo-glow { background: radial-gradient(circle, rgba(230,162,60,0.35) 0%, transparent 70%); }

/* Per-state animations */
.state-idle .timo-img {
  animation: idle-breathe 3s ease-in-out infinite;
  will-change: transform;
}

.state-thinking .timo-img {
  animation: thinking-wobble 0.8s ease-in-out infinite;
  will-change: transform;
}

.state-success .timo-img {
  animation: success-bounce 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.state-alert .timo-img {
  animation: alert-shake 0.4s ease-in-out;
}

.state-offline .timo-img {
  opacity: 0.5;
  filter: grayscale(0.6);
}

@keyframes idle-breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.03); }
}

@keyframes thinking-wobble {
  0%, 100% { transform: rotate(0deg) scale(1); }
  25% { transform: rotate(-3deg) scale(1.02); }
  75% { transform: rotate(3deg) scale(1.02); }
}

@keyframes success-bounce {
  0% { transform: scale(0.8); }
  50% { transform: scale(1.15); }
  100% { transform: scale(1); }
}

@keyframes alert-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-3px); }
  40% { transform: translateX(3px); }
  60% { transform: translateX(-2px); }
  80% { transform: translateX(2px); }
}

@keyframes glow-pulse {
  0%, 100% { opacity: 0; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.1); }
}
</style>
