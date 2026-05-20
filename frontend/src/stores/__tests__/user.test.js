import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'

// Mock localStorage for happy-dom compatibility
const store = {}
const mockLocalStorage = {
  getItem: (key) => store[key] || null,
  setItem: (key, val) => { store[key] = String(val) },
  removeItem: (key) => { delete store[key] },
  clear: () => { Object.keys(store).forEach(k => delete store[k]) }
}
vi.stubGlobal('localStorage', mockLocalStorage)

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockLocalStorage.clear()
  })

  it('initializes with empty token from localStorage', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
  })

  it('setToken updates token and localStorage', () => {
    const store = useUserStore()
    store.setToken('abc123')
    expect(store.token).toBe('abc123')
    expect(mockLocalStorage.getItem('token')).toBe('abc123')
  })

  it('setUserInfo updates userInfo', () => {
    const store = useUserStore()
    const info = { id: 1, email: 'test@test.com', nickname: 'Test' }
    store.setUserInfo(info)
    expect(store.userInfo).toEqual(info)
  })

  it('logout clears everything', () => {
    const store = useUserStore()
    store.setToken('tok')
    store.setUserInfo({ id: 1 })
    store.logout()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(mockLocalStorage.getItem('token')).toBeNull()
  })

  it('initializes token from localStorage', () => {
    mockLocalStorage.setItem('token', 'saved-token')
    const store = useUserStore()
    expect(store.token).toBe('saved-token')
  })
})
