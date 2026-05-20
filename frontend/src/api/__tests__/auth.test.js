import { describe, it, expect } from 'vitest'
// Import API functions to verify they exist and have correct signatures
import { login, register, sendResetCode, resetPassword, logout } from '../auth'

describe('Auth API', () => {
  it('login is a function', () => {
    expect(typeof login).toBe('function')
  })

  it('register is a function', () => {
    expect(typeof register).toBe('function')
  })

  it('sendResetCode is a function', () => {
    expect(typeof sendResetCode).toBe('function')
  })

  it('resetPassword is a function', () => {
    expect(typeof resetPassword).toBe('function')
  })

  it('logout is a function', () => {
    expect(typeof logout).toBe('function')
  })
})

describe('Study API exports', () => {
  it('has all study functions', async () => {
    const study = await import('../study')
    expect(typeof study.submitQuickMemory).toBe('function')
    expect(typeof study.submitContextDeepGroup).toBe('function')
  })
})

describe('Words API exports', () => {
  it('has all word functions', async () => {
    const words = await import('../words')
    expect(typeof words.getWordList).toBe('function')
    expect(typeof words.getWordDetail).toBe('function')
    expect(typeof words.getWordFsrsState).toBe('function')
    expect(typeof words.getWordBatch).toBe('function')
    expect(typeof words.searchWords).toBe('function')
    expect(typeof words.getWordCount).toBe('function')
  })
})

describe('Review API exports', () => {
  it('has all review functions', async () => {
    const review = await import('../review')
    expect(typeof review.getReviewQueue).toBe('function')
    expect(typeof review.submitReviewResult).toBe('function')
    expect(typeof review.getNearForgotten).toBe('function')
  })
})

describe('Statistics API exports', () => {
  it('has all statistics functions', async () => {
    const stats = await import('../statistics')
    expect(typeof stats.getOverview).toBe('function')
    expect(typeof stats.getRetention).toBe('function')
    expect(typeof stats.getForgettingCurve).toBe('function')
    expect(typeof stats.getHeatmap).toBe('function')
    expect(typeof stats.getDailyStats).toBe('function')
    expect(typeof stats.getReactionTime).toBe('function')
    expect(typeof stats.getWeakWords).toBe('function')
  })
})

describe('Calendar API exports', () => {
  it('has all calendar functions', async () => {
    const cal = await import('../calendar')
    expect(typeof cal.getMonthly).toBe('function')
    expect(typeof cal.checkin).toBe('function')
  })
})

describe('User API exports', () => {
  it('has all user functions', async () => {
    const user = await import('../user')
    expect(typeof user.getProfile).toBe('function')
    expect(typeof user.updatePreferences).toBe('function')
    expect(typeof user.uploadAvatar).toBe('function')
    expect(typeof user.deleteAccount).toBe('function')
  })
})

describe('Agent API exports', () => {
  it('has all agent functions', async () => {
    const agent = await import('../agent')
    expect(typeof agent.sendChatMessage).toBe('function')
    expect(typeof agent.loadChatHistory).toBe('function')
    expect(typeof agent.getRecommend).toBe('function')
    expect(typeof agent.getWeeklyReport).toBe('function')
    expect(typeof agent.generatePassage).toBe('function')
    expect(typeof agent.getStubbornWords).toBe('function')
    expect(typeof agent.getProgressAlert).toBe('function')
    expect(typeof agent.analyzeWord).toBe('function')
  })
})

describe('ExamPlan API exports', () => {
  it('has all examPlan functions', async () => {
    const ep = await import('../examPlan')
    expect(typeof ep.startExamPlanDialog).toBe('function')
    expect(typeof ep.continueExamPlanDialog).toBe('function')
    expect(typeof ep.getExamPlanStatus).toBe('function')
  })
})
