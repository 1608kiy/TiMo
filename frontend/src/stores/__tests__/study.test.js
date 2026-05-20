import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useStudyStore } from '../study'

describe('useStudyStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with empty state', () => {
    const store = useStudyStore()
    expect(store.sessionQueue).toEqual([])
    expect(store.progress.completed).toBe(0)
    expect(store.progress.total).toBe(0)
    expect(store.progress.correct).toBe(0)
    expect(store.progress.accuracy).toBe(0)
    expect(store.progress.elapsedMs).toBe(0)
  })

  it('setSessionQueue sets queue and total', () => {
    const store = useStudyStore()
    store.setSessionQueue([1, 2, 3, 4, 5])
    expect(store.sessionQueue).toEqual([1, 2, 3, 4, 5])
    expect(store.progress.total).toBe(5)
    expect(store.progress.completed).toBe(0)
  })

  it('updateProgress calculates accuracy', () => {
    const store = useStudyStore()
    store.setSessionQueue([1, 2, 3, 4])
    store.updateProgress(4, 3)
    expect(store.progress.completed).toBe(4)
    expect(store.progress.correct).toBe(3)
    expect(store.progress.accuracy).toBe(75)
  })

  it('updateProgress with 0 completed gives 0 accuracy', () => {
    const store = useStudyStore()
    store.updateProgress(0, 0)
    expect(store.progress.accuracy).toBe(0)
  })

  it('resetSession clears everything', () => {
    const store = useStudyStore()
    store.setSessionQueue([1, 2, 3])
    store.updateProgress(3, 2)
    store.resetSession()
    expect(store.sessionQueue).toEqual([])
    expect(store.progress.completed).toBe(0)
    expect(store.progress.total).toBe(0)
    expect(store.progress.accuracy).toBe(0)
    expect(store.progress.elapsedMs).toBe(0)
  })
})
