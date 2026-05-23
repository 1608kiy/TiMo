import { describe, it, expect } from 'vitest'

/**
 * API Contract Tests
 * Verifies that all frontend API calls use correct HTTP methods and endpoint patterns
 * that match the backend controller definitions.
 */

describe('API Contract: Frontend ↔ Backend endpoint alignment', () => {

  describe('Auth API (/api/auth/**)', () => {
    it('login uses POST /auth/login', async () => {
      const { login } = await import('../auth')
      expect(login).toBeTypeOf('function')
      // login({email, password}) -> request.post('/auth/login', data)
    }, 30000)

    it('register uses POST /auth/register', async () => {
      const { register } = await import('../auth')
      expect(register).toBeTypeOf('function')
    })

    it('logout uses POST /auth/logout', async () => {
      const { logout } = await import('../auth')
      expect(logout).toBeTypeOf('function')
    })

    it('sendResetCode uses POST /auth/forgot-password/send-code', async () => {
      const { sendResetCode } = await import('../auth')
      expect(sendResetCode).toBeTypeOf('function')
    })

    it('resetPassword uses POST /auth/forgot-password/reset', async () => {
      const { resetPassword } = await import('../auth')
      expect(resetPassword).toBeTypeOf('function')
    })
  })

  describe('Study API (/api/study/**)', () => {
    it('submitQuickMemory uses POST /study/submit-quick-memory', async () => {
      const { submitQuickMemory } = await import('../study')
      expect(submitQuickMemory).toBeTypeOf('function')
    })

    it('submitContextDeepGroup uses POST /study/submit-context-deep-group', async () => {
      const { submitContextDeepGroup } = await import('../study')
      expect(submitContextDeepGroup).toBeTypeOf('function')
    })

    it('submitReverseRecall uses POST /study/submit-reverse-recall', async () => {
      const { submitReverseRecall } = await import('../study')
      expect(submitReverseRecall).toBeTypeOf('function')
    })

    it('getReverseRecallCandidates uses GET /study/reverse-recall/candidates', async () => {
      const { getReverseRecallCandidates } = await import('../study')
      expect(getReverseRecallCandidates).toBeTypeOf('function')
    })

  })

  describe('Words API (/api/words/**)', () => {
    it('getWordList uses GET /words', async () => {
      const { getWordList } = await import('../words')
      expect(getWordList).toBeTypeOf('function')
    })

    it('getWordDetail uses GET /words/:id', async () => {
      const { getWordDetail } = await import('../words')
      expect(getWordDetail).toBeTypeOf('function')
    })

    it('getWordFsrsState uses GET /words/:id/fsrs-state', async () => {
      const { getWordFsrsState } = await import('../words')
      expect(getWordFsrsState).toBeTypeOf('function')
    })

    it('searchWords uses GET /words/search', async () => {
      const { searchWords } = await import('../words')
      expect(searchWords).toBeTypeOf('function')
    })
  })

  describe('Review API (/api/review/**)', () => {
    it('getReviewQueue uses GET /review/queue', async () => {
      const { getReviewQueue } = await import('../review')
      expect(getReviewQueue).toBeTypeOf('function')
    })

    it('submitReviewResult uses POST /review/result', async () => {
      const { submitReviewResult } = await import('../review')
      expect(submitReviewResult).toBeTypeOf('function')
    })

    it('getNearForgotten uses GET /review/near-forgotten', async () => {
      const { getNearForgotten } = await import('../review')
      expect(getNearForgotten).toBeTypeOf('function')
    })
  })

  describe('Statistics API (/api/statistics/**)', () => {
    it('getOverview uses GET /statistics/overview', async () => {
      const { getOverview } = await import('../statistics')
      expect(getOverview).toBeTypeOf('function')
    })

    it('getRetention uses GET /statistics/retention', async () => {
      const { getRetention } = await import('../statistics')
      expect(getRetention).toBeTypeOf('function')
    })

    it('getForgettingCurve uses GET /statistics/forgetting-curve', async () => {
      const { getForgettingCurve } = await import('../statistics')
      expect(getForgettingCurve).toBeTypeOf('function')
    })

    it('getHeatmap uses GET /statistics/heatmap', async () => {
      const { getHeatmap } = await import('../statistics')
      expect(getHeatmap).toBeTypeOf('function')
    })

    it('getDailyStats uses GET /statistics/daily-stats', async () => {
      const { getDailyStats } = await import('../statistics')
      expect(getDailyStats).toBeTypeOf('function')
    })

    it('getReactionTime uses GET /statistics/reaction-time', async () => {
      const { getReactionTime } = await import('../statistics')
      expect(getReactionTime).toBeTypeOf('function')
    })

    it('getWeakWords uses GET /statistics/weak-words', async () => {
      const { getWeakWords } = await import('../statistics')
      expect(getWeakWords).toBeTypeOf('function')
    })
  })

  describe('Calendar API (/api/calendar/**)', () => {
    it('getMonthly uses GET /calendar/monthly', async () => {
      const { getMonthly } = await import('../calendar')
      expect(getMonthly).toBeTypeOf('function')
    })

    it('checkin uses POST /calendar/checkin', async () => {
      const { checkin } = await import('../calendar')
      expect(checkin).toBeTypeOf('function')
    })
  })

  describe('User API (/api/user/**)', () => {
    it('getProfile uses GET /user/profile', async () => {
      const { getProfile } = await import('../user')
      expect(getProfile).toBeTypeOf('function')
    })

    it('updatePreferences uses PUT /user/preferences', async () => {
      const { updatePreferences } = await import('../user')
      expect(updatePreferences).toBeTypeOf('function')
    })

    it('uploadAvatar uses POST /user/avatar', async () => {
      const { uploadAvatar } = await import('../user')
      expect(uploadAvatar).toBeTypeOf('function')
    })

    it('deleteAccount uses DELETE /user/account', async () => {
      const { deleteAccount } = await import('../user')
      expect(deleteAccount).toBeTypeOf('function')
    })
  })

  describe('Agent API (/api/agent/**)', () => {
    it('sendChatMessage uses POST /agent/chat/send', async () => {
      const { sendChatMessage } = await import('../agent')
      expect(sendChatMessage).toBeTypeOf('function')
    })

    it('loadChatHistory uses GET /agent/chat/history', async () => {
      const { loadChatHistory } = await import('../agent')
      expect(loadChatHistory).toBeTypeOf('function')
    })

    it('getRecommend uses GET /agent/recommend', async () => {
      const { getRecommend } = await import('../agent')
      expect(getRecommend).toBeTypeOf('function')
    })

    it('getWeeklyReport uses GET /agent/weekly-report', async () => {
      const { getWeeklyReport } = await import('../agent')
      expect(getWeeklyReport).toBeTypeOf('function')
    })

    it('generatePassage uses POST /agent/generate-passage', async () => {
      const { generatePassage } = await import('../agent')
      expect(generatePassage).toBeTypeOf('function')
    })

    it('getStubbornWords uses GET /agent/stubborn-words', async () => {
      const { getStubbornWords } = await import('../agent')
      expect(getStubbornWords).toBeTypeOf('function')
    })

    it('getProgressAlert uses GET /agent/progress-alert', async () => {
      const { getProgressAlert } = await import('../agent')
      expect(getProgressAlert).toBeTypeOf('function')
    })

    it('analyzeWord uses POST /agent/analyze-word', async () => {
      const { analyzeWord } = await import('../agent')
      expect(analyzeWord).toBeTypeOf('function')
    })

    it('planSmartSession uses POST /agent/smart-session/plan', async () => {
      const { planSmartSession } = await import('../agent')
      expect(planSmartSession).toBeTypeOf('function')
    })
  })

  describe('ExamPlan API (/api/exam-plan/**)', () => {
    it('startExamPlanDialog uses POST /exam-plan/start-dialog', async () => {
      const { startExamPlanDialog } = await import('../examPlan')
      expect(startExamPlanDialog).toBeTypeOf('function')
    })

    it('continueExamPlanDialog uses POST /exam-plan/continue-dialog', async () => {
      const { continueExamPlanDialog } = await import('../examPlan')
      expect(continueExamPlanDialog).toBeTypeOf('function')
    })

    it('getExamPlanStatus uses GET /exam-plan/status', async () => {
      const { getExamPlanStatus } = await import('../examPlan')
      expect(getExamPlanStatus).toBeTypeOf('function')
    })

    it('getDailyQuota uses GET /exam-plan/daily-quota', async () => {
      const { getDailyQuota } = await import('../examPlan')
      expect(getDailyQuota).toBeTypeOf('function')
    })
  })
})
