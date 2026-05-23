import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDailyQuota } from '../api/examPlan'

export const useExamPlanStore = defineStore('examPlan', () => {
  const stage = ref('init')
  const isActive = ref(false)
  const dialogMessages = ref([])
  const generatedPlan = ref(null)
  // 今日配额：null 表示尚未拉取
  const quota = ref(null)
  const quotaLoading = ref(false)

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

  async function fetchQuota() {
    quotaLoading.value = true
    try {
      const res = await getDailyQuota()
      quota.value = res.data || null
      return quota.value
    } catch {
      quota.value = null
      return null
    } finally {
      quotaLoading.value = false
    }
  }

  function reset() {
    stage.value = 'init'
    isActive.value = false
    dialogMessages.value = []
    generatedPlan.value = null
    quota.value = null
    quotaLoading.value = false
  }

  return {
    stage, isActive, dialogMessages, generatedPlan,
    quota, quotaLoading,
    setStage, startPlanning, finishPlanning, cancelPlanning,
    addDialogMessage, setGeneratedPlan, fetchQuota, reset
  }
})
