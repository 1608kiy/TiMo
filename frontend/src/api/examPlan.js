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
