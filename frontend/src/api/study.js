import request from './request'

export function submitQuickMemory(data) {
  return request.post('/study/submit-quick-memory', data)
}

export function submitContextDeepGroup(data) {
  return request.post('/study/submit-context-deep-group', data)
}

export function submitReverseRecall(data) {
  return request.post('/study/submit-reverse-recall', data)
}

export function getReverseRecallCandidates(limit = 10) {
  return request.get('/study/reverse-recall/candidates', { params: { limit } })
}
