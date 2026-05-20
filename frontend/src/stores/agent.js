import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAgentStore = defineStore('agent', () => {
  const isOpen = ref(false)
  const tiMoState = ref('idle')
  const apiStatus = ref('online')
  const messages = ref([])
  const conversationType = ref('general')
  const fabRect = ref({ x: 0, y: 0, width: 0, height: 0 })
  const sessionId = ref(null)
  const unreadCount = ref(0)
  const currentPage = ref('dashboard')

  let stateTimer = null

  function toggleDialog() {
    isOpen.value = !isOpen.value
    if (isOpen.value) {
      unreadCount.value = 0
    }
  }

  function setTiMoState(state, duration) {
    tiMoState.value = state
    if (stateTimer) clearTimeout(stateTimer)
    if (duration) {
      stateTimer = setTimeout(() => {
        tiMoState.value = 'idle'
      }, duration)
    }
  }

  function setApiStatus(status) {
    apiStatus.value = status
    if (status === 'offline') {
      setTiMoState('offline')
    }
  }

  function addMessage(msg) {
    messages.value.push(msg)
    if (messages.value.length > 100) {
      messages.value = messages.value.slice(-100)
    }
    if (msg.role === 'assistant' && !isOpen.value) {
      unreadCount.value++
    }
  }

  function setSessionId(id) {
    sessionId.value = id
  }

  function setConversationType(type) {
    conversationType.value = type
  }

  function setFabRect(rect) {
    fabRect.value = rect
  }

  function clearMessages() {
    messages.value = []
  }

  function setCurrentPage(page) {
    currentPage.value = page
  }

  return {
    isOpen, tiMoState, apiStatus, messages, conversationType, fabRect,
    sessionId, unreadCount, currentPage,
    toggleDialog, setTiMoState, setApiStatus, addMessage, setSessionId,
    setConversationType, setFabRect, clearMessages, setCurrentPage
  }
})
