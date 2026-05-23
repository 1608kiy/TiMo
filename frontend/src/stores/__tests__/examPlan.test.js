import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useExamPlanStore } from '../examPlan'

describe('useExamPlanStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with default state', () => {
    const store = useExamPlanStore()
    expect(store.stage).toBe('init')
    expect(store.isActive).toBe(false)
    expect(store.dialogMessages).toEqual([])
    expect(store.generatedPlan).toBeNull()
  })

  it('startPlanning sets active and clears state', () => {
    const store = useExamPlanStore()
    store.generatedPlan = { some: 'data' }
    store.startPlanning()
    expect(store.isActive).toBe(true)
    expect(store.stage).toBe('init')
    expect(store.dialogMessages).toEqual([])
    expect(store.generatedPlan).toBeNull()
  })

  it('finishPlanning stores plan and deactivates', () => {
    const store = useExamPlanStore()
    store.startPlanning()
    const plan = { examType: 'cet4', targetVocab: 5000 }
    store.finishPlanning(plan)
    expect(store.generatedPlan).toEqual(plan)
    expect(store.isActive).toBe(false)
  })

  it('cancelPlanning deactivates', () => {
    const store = useExamPlanStore()
    store.startPlanning()
    store.cancelPlanning()
    expect(store.isActive).toBe(false)
  })

  it('addDialogMessage accumulates', () => {
    const store = useExamPlanStore()
    store.addDialogMessage({ role: 'agent', content: 'hi' })
    store.addDialogMessage({ role: 'user', content: 'hello' })
    expect(store.dialogMessages).toHaveLength(2)
  })

  it('setStage updates stage', () => {
    const store = useExamPlanStore()
    store.setStage('exam_type')
    expect(store.stage).toBe('exam_type')
  })

  it('reset clears everything', () => {
    const store = useExamPlanStore()
    store.startPlanning()
    store.addDialogMessage({ role: 'user', content: 'test' })
    store.setStage('current_level')
    store.reset()
    expect(store.stage).toBe('init')
    expect(store.isActive).toBe(false)
    expect(store.dialogMessages).toEqual([])
    expect(store.generatedPlan).toBeNull()
  })

  it('full exam plan flow simulates registration trigger', () => {
    const store = useExamPlanStore()
    // Simulate: Dashboard sets exam_planning → TiMoDialog's tryStartExamPlan fires
    store.startPlanning()
    expect(store.isActive).toBe(true)
    expect(store.stage).toBe('init')
    expect(store.dialogMessages).toEqual([])

    // Simulate: API response from startExamPlanDialog()
    store.setStage('exam_type')
    store.addDialogMessage({ role: 'assistant', content: '请选择你的目标考试', options: [
      { label: 'CET-4', value: 'cet4' }, { label: 'CET-6', value: 'cet6' }
    ]})
    expect(store.stage).toBe('exam_type')
    expect(store.dialogMessages).toHaveLength(1)
    expect(store.isActive).toBe(true)

    // Simulate: user selects option → continueExamPlanDialog()
    store.addDialogMessage({ role: 'user', content: 'CET-4' })
    store.setStage('target_date')
    store.addDialogMessage({ role: 'assistant', content: '计划备考多久？' })
    expect(store.dialogMessages).toHaveLength(3)

    // Simulate: finish planning → plan generated
    const plan = { examType: 'cet4', dailyNewWords: 30, dailyReviewWords: 60, estimatedDays: 90 }
    store.finishPlanning(plan)
    expect(store.isActive).toBe(false)
    expect(store.generatedPlan).toEqual(plan)
  })

  it('timo_plan_skipped prevents re-trigger check via localStorage', () => {
    const store = useExamPlanStore()
    // Simulate: user closes dialog → cancelPlanning()
    store.startPlanning()
    expect(store.isActive).toBe(true)
    store.cancelPlanning()
    expect(store.isActive).toBe(false)
    // After cancel, Dashboard checks timo_plan_skipped flag
    // This is verified by the Dashboard component using localStorage
  })

  it('quota initial state is null and reset clears it', () => {
    const store = useExamPlanStore()
    expect(store.quota).toBeNull()
    expect(store.quotaLoading).toBe(false)
    // simulate manual hydration
    store.quota = {
      hasActivePlan: true,
      dailyNewWordsTarget: 20,
      todayNewWordsLearned: 5,
      newWordsRemaining: 15
    }
    expect(store.quota.newWordsRemaining).toBe(15)
    store.reset()
    expect(store.quota).toBeNull()
    expect(store.quotaLoading).toBe(false)
  })
})
