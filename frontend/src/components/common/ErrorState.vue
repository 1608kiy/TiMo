<template>
  <div class="error-state">
    <img src="/agent/offline.png" alt="TiMo" class="error-icon" />
    <h3 class="error-title">{{ title }}</h3>
    <p class="error-desc">{{ description }}</p>
    <el-button type="primary" @click="$emit('retry')">
      重试
    </el-button>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '加载失败' },
  description: { type: String, default: '网络异常，请检查后重试' }
})

defineEmits(['retry'])
</script>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 24px;
  text-align: center;
}

.error-icon {
  width: 72px;
  height: 72px;
  object-fit: contain;
  opacity: 0.6;
}

.error-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.error-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 280px;
}
</style>
