<template>
  <div class="timo-fab" :class="{ compact }" ref="fabRef" @click="handleClick" data-onboard="fab">
    <TiMoAvatar :state="agentStore.tiMoState" :size="compact ? 'small' : 'normal'" />
    <span v-if="!compact" class="fab-label">TiMo</span>
    <span v-if="agentStore.unreadCount > 0" class="timo-badge">{{ agentStore.unreadCount > 99 ? '99+' : agentStore.unreadCount }}</span>
  </div>
</template>

<script setup>
import { ref, onMounted, onUpdated } from 'vue'
import { useAgentStore } from '../../stores/agent'
import TiMoAvatar from './TiMoAvatar.vue'

defineProps({ compact: { type: Boolean, default: false } })

const agentStore = useAgentStore()
const fabRef = ref(null)

function reportPosition() {
  if (fabRef.value) {
    const rect = fabRef.value.getBoundingClientRect()
    agentStore.setFabRect({ x: rect.x, y: rect.y, width: rect.width, height: rect.height })
  }
}

function handleClick() {
  reportPosition()
  agentStore.toggleDialog()
}

onMounted(reportPosition)
onUpdated(reportPosition)
</script>

<style scoped>
.timo-fab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  background: transparent;
  padding: 0;
  border-radius: var(--radius-full);
  border: none;
  box-shadow: none;
  transition: box-shadow 0.2s ease, transform 0.15s ease;
  flex-shrink: 0;
  position: relative;
}

.timo-fab:hover {
  transform: translateY(-1px);
}

.timo-fab:active {
  transform: translateY(1px);
}

.fab-label {
  font-weight: 800;
  font-size: 14px;
  color: var(--color-primary-dark);
}

.timo-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: var(--color-red);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: var(--radius-full);
  padding: 0 6px;
  border: 3px solid #fff;
  animation: badge-pop 0.4s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

@keyframes badge-pop { 0%{transform:scale(0)} 60%{transform:scale(1.2)} 100%{transform:scale(1)} }
</style>
