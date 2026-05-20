import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const useStudyStore = defineStore('study', () => {
  const sessionQueue = ref([])
  const progress = reactive({
    completed: 0,
    total: 0,
    correct: 0,
    accuracy: 0,
    elapsedMs: 0
  })

  function setSessionQueue(queue) {
    sessionQueue.value = queue
    progress.total = queue.length
    progress.completed = 0
    progress.correct = 0
  }

  function updateProgress(completed, correct) {
    progress.completed = completed
    progress.correct = correct
    progress.accuracy = completed > 0 ? Math.round((correct / completed) * 100) : 0
  }

  function resetSession() {
    sessionQueue.value = []
    progress.completed = 0
    progress.total = 0
    progress.correct = 0
    progress.accuracy = 0
    progress.elapsedMs = 0
  }

  return { sessionQueue, progress, setSessionQueue, updateProgress, resetSession }
})
