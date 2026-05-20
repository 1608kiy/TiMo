import { ref, onBeforeUnmount } from 'vue'

export function useFatigueCheck(sessionDone, sessionStartTime) {
  const fatigueMinutes = ref(0)
  const showFatigueReminder = ref(false)
  let fatigueTimer = null
  let fatigueDismissed = false

  function startFatigueCheck() {
    const check = () => {
      if (fatigueDismissed || sessionDone.value) return
      const elapsed = Date.now() - sessionStartTime.value
      const mins = Math.floor(elapsed / 60000)
      fatigueMinutes.value = mins
      if (mins >= 20) showFatigueReminder.value = true
    }
    fatigueTimer = setInterval(check, 30000)
    check()
  }

  function dismissFatigue() {
    fatigueDismissed = true
    showFatigueReminder.value = false
  }

  onBeforeUnmount(() => {
    if (fatigueTimer) clearInterval(fatigueTimer)
  })

  return { fatigueMinutes, showFatigueReminder, startFatigueCheck, dismissFatigue }
}
