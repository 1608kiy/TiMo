import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAgentStore } from '../agent'

describe('useAgentStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  it('initializes with default state', () => {
    const store = useAgentStore()
    expect(store.isOpen).toBe(false)
    expect(store.tiMoState).toBe('idle')
    expect(store.apiStatus).toBe('online')
    expect(store.messages).toEqual([])
    expect(store.conversationType).toBe('general')
    expect(store.unreadCount).toBe(0)
  })

  it('toggleDialog toggles open state', () => {
    const store = useAgentStore()
    store.toggleDialog()
    expect(store.isOpen).toBe(true)
    store.toggleDialog()
    expect(store.isOpen).toBe(false)
  })

  it('toggleDialog resets unread count when opened', () => {
    const store = useAgentStore()
    store.unreadCount = 5
    store.toggleDialog()
    expect(store.unreadCount).toBe(0)
  })

  it('addMessage adds to messages', () => {
    const store = useAgentStore()
    store.addMessage({ role: 'user', content: 'hello' })
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].content).toBe('hello')
  })

  it('addMessage limits to 100 messages', () => {
    const store = useAgentStore()
    for (let i = 0; i < 105; i++) {
      store.addMessage({ role: 'user', content: `msg${i}` })
    }
    expect(store.messages).toHaveLength(100)
    expect(store.messages[0].content).toBe('msg5')
  })

  it('addMessage increments unread for assistant when closed', () => {
    const store = useAgentStore()
    store.isOpen = false
    store.addMessage({ role: 'assistant', content: 'hi' })
    expect(store.unreadCount).toBe(1)
  })

  it('addMessage does not increment unread when open', () => {
    const store = useAgentStore()
    store.isOpen = true
    store.addMessage({ role: 'assistant', content: 'hi' })
    expect(store.unreadCount).toBe(0)
  })

  it('setTiMoState resets to idle after duration', () => {
    const store = useAgentStore()
    store.setTiMoState('thinking', 1000)
    expect(store.tiMoState).toBe('thinking')
    vi.advanceTimersByTime(1000)
    expect(store.tiMoState).toBe('idle')
  })

  it('setApiStatus offline sets tiMoState', () => {
    const store = useAgentStore()
    store.setApiStatus('offline')
    expect(store.apiStatus).toBe('offline')
    expect(store.tiMoState).toBe('offline')
  })

  it('clearMessages empties messages', () => {
    const store = useAgentStore()
    store.addMessage({ role: 'user', content: 'a' })
    store.clearMessages()
    expect(store.messages).toEqual([])
  })
})
