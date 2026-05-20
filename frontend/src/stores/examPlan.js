import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useExamPlanStore = defineStore('examPlan', () => {
  const stage = ref('init')
  const isActive = ref(false)
  const dialogMessages = ref([])
  const generatedPlan = ref(null)

  function setStage(newStage) {
    stage.value = newStage
  }

  function startPlanning() {
    isActive.value = true
    dialogMessages.value = []
    stage.value = 'init'
    generatedPlan.value = null
  }

  function finishPlanning(plan) {
    generatedPlan.value = plan
    isActive.value = false
  }

  function cancelPlanning() {
    isActive.value = false
  }

  function addDialogMessage(msg) {
    dialogMessages.value.push(msg)
  }

  function setGeneratedPlan(plan) {
    generatedPlan.value = plan
  }

  function reset() {
    stage.value = 'init'
    isActive.value = false
    dialogMessages.value = []
    generatedPlan.value = null
  }

  return {
    stage, isActive, dialogMessages, generatedPlan,
    setStage, startPlanning, finishPlanning, cancelPlanning,
    addDialogMessage, setGeneratedPlan, reset
  }
})
