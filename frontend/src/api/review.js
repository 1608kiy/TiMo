import request from './request'

export function getReviewQueue() {
  return request.get('/review/queue')
}

export function submitReviewResult(data) {
  return request.post('/review/result', data)
}

export function getNearForgotten() {
  return request.get('/review/near-forgotten')
}
