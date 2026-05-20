<template>
  <span ref="el" class="math-formula"></span>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import katex from 'katex'
import 'katex/dist/katex.min.css'

const props = defineProps({
  expr: { type: String, required: true },
  display: { type: Boolean, default: false }
})

const el = ref(null)

function render() {
  if (!el.value || !props.expr) return
  try {
    katex.render(props.expr, el.value, {
      throwOnError: false,
      displayMode: props.display,
      output: 'html'
    })
  } catch {}
}

onMounted(render)
watch(() => props.expr, render)
</script>

<style scoped>
.math-formula {
  font-size: inherit;
  line-height: 1;
}
</style>
