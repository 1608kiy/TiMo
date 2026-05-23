import request from './request'

export function startExamPlanDialog() {
  return request.post('/exam-plan/start-dialog')
}

export function continueExamPlanDialog(answer) {
  return request.post('/exam-plan/continue-dialog', { answer })
}

export function getExamPlanStatus() {
  return request.get('/exam-plan/status')
}

// 今日新词/复习 配额（ExamPlan 闭环）
export function getDailyQuota() {
  return request.get('/exam-plan/daily-quota')
}
