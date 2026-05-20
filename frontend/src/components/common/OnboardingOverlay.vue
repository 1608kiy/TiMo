<template>
  <Teleport to="body">
    <Transition name="overlay-fade">
      <div v-if="visible" class="onboarding-overlay" @click.self="skip">
        <!-- Cutout highlight -->
        <div
          class="highlight-cutout"
          :style="cutoutStyle"
        ></div>

        <!-- Bubble -->
        <div class="bubble-wrapper" :style="bubbleStyle">
          <div class="bubble">
            <div class="bubble-step">第 {{ currentStep + 1 }} 步 / 共 {{ steps.length }} 步</div>
            <div class="bubble-title">{{ steps[currentStep].title }}</div>
            <div class="bubble-desc">{{ steps[currentStep].desc }}</div>
            <div class="bubble-actions">
              <button class="bubble-btn skip" @click="skip">跳过</button>
              <button class="bubble-btn next" @click="next">
                {{ currentStep === steps.length - 1 ? '开始使用' : '下一步' }}
              </button>
            </div>
            <!-- Arrow pointer -->
            <div class="bubble-arrow" :class="steps[currentStep].arrowDir"></div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const STORAGE_KEY = computed(() => `timo_onboarding_done_${userStore.userInfo?.userId || 'guest'}`)

const steps = [
  {
    target: 'navbar',
    title: '导航栏',
    desc: '在这里快速切换学习模式、查看词库和统计，右上角可以进入个人中心。',
    arrowDir: 'arrow-down'
  },
  {
    target: 'content',
    title: '学习区域',
    desc: '这是你的学习主阵地，选词、练习、复习都在这里完成。',
    arrowDir: 'arrow-up'
  },
  {
    target: 'fab',
    title: 'TiMo 助手',
    desc: '随时点击这个按钮和 TiMo 对话，获取学习建议和备考规划。',
    arrowDir: 'arrow-left'
  }
]

const visible = ref(false)
const currentStep = ref(0)

const targetRect = ref({ top: 0, left: 0, width: 0, height: 0 })

const cutoutStyle = computed(() => {
  const r = targetRect.value
  const pad = 8
  return {
    top: (r.top - pad) + 'px',
    left: (r.left - pad) + 'px',
    width: (r.width + pad * 2) + 'px',
    height: (r.height + pad * 2) + 'px',
    borderRadius: '12px'
  }
})

const bubbleStyle = computed(() => {
  const r = targetRect.value
  const step = steps[currentStep.value]
  const gap = 16

  if (step.arrowDir === 'arrow-down') {
    // Bubble below target
    return {
      position: 'fixed',
      top: (r.top + r.height + gap + 8) + 'px',
      left: Math.min(r.left, window.innerWidth - 320) + 'px'
    }
  }
  if (step.arrowDir === 'arrow-up') {
    // Bubble above target
    return {
      position: 'fixed',
      top: Math.max(r.top - 180 - gap, 16) + 'px',
      left: Math.min(r.left, window.innerWidth - 320) + 'px'
    }
  }
  // arrow-left: bubble to the left
  return {
    position: 'fixed',
    top: Math.max(r.top - 40, 16) + 'px',
    left: Math.max(r.left - 310 - gap, 16) + 'px'
  }
})

function locateTarget() {
  const selector = steps[currentStep.value].target
  const el = document.querySelector(`[data-onboard="${selector}"]`)
  if (el) {
    targetRect.value = el.getBoundingClientRect()
  } else {
    // Fallback positions
    const fallbacks = {
      navbar: { top: 0, left: 0, width: window.innerWidth, height: 64 },
      content: { top: 80, left: window.innerWidth * 0.15, width: window.innerWidth * 0.7, height: 300 },
      fab: { top: window.innerHeight - 80, left: window.innerWidth - 80, width: 64, height: 64 }
    }
    targetRect.value = fallbacks[selector]
  }
}

function next() {
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
    nextTick(locateTarget)
  } else {
    finish()
  }
}

function skip() {
  finish()
}

function finish() {
  localStorage.setItem(STORAGE_KEY.value, '1')
  visible.value = false
}

function handleResize() {
  locateTarget()
}

onMounted(() => {
  if (localStorage.getItem(STORAGE_KEY.value)) return
  visible.value = true
  nextTick(locateTarget)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.onboarding-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.55);
}

.highlight-cutout {
  position: fixed;
  background: transparent;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  z-index: 1;
  pointer-events: none;
}

.bubble-wrapper {
  z-index: 2;
  pointer-events: auto;
}

.bubble {
  width: 300px;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  animation: bubble-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
}

@keyframes bubble-in {
  from { opacity: 0; transform: scale(0.9) translateY(6px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.bubble-step {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-primary, #7a9e7e);
  margin-bottom: 6px;
}

.bubble-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--color-text-primary, #2c2c2c);
  margin-bottom: 6px;
}

.bubble-desc {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.6;
  margin-bottom: 16px;
}

.bubble-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.bubble-btn {
  padding: 8px 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
  font-family: var(--font-family, 'Nunito', sans-serif);
}

.bubble-btn.skip {
  background: #f0f0f0;
  color: #666;
}

.bubble-btn.skip:hover {
  background: #e4e4e4;
}

.bubble-btn.next {
  background: var(--color-primary, #7a9e7e);
  color: #fff;
  box-shadow: 0 2px 8px rgba(122, 158, 126, 0.3);
}

.bubble-btn.next:hover {
  background: var(--color-primary-dark, #6a8e6e);
  transform: translateY(-1px);
}

/* Arrow pointers */
.bubble-arrow {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #fff;
  transform: rotate(45deg);
}

.bubble-arrow.arrow-down {
  top: -6px;
  left: 24px;
}

.bubble-arrow.arrow-up {
  bottom: -6px;
  left: 24px;
}

.bubble-arrow.arrow-left {
  right: -6px;
  top: 40px;
}

/* Transition */
.overlay-fade-enter-active { transition: opacity 0.3s ease; }
.overlay-fade-leave-active { transition: opacity 0.25s ease; }
.overlay-fade-enter-from,
.overlay-fade-leave-to { opacity: 0; }
</style>
