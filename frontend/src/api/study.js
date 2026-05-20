import request from './request'

export function submitQuickMemory(data) {
  return request.post('/study/submit-quick-memory', data)
}

export function submitContextDeepGroup(data) {
  return request.post('/study/submit-context-deep-group', data)
}
