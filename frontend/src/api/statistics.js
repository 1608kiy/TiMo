import request from './request'

export function getOverview() {
  return request.get('/statistics/overview')
}

export function getRetention(days = 7) {
  return request.get('/statistics/retention', { params: { days } })
}

export function getForgettingCurve(days = 14) {
  return request.get('/statistics/forgetting-curve', { params: { days } })
}

export function getHeatmap() {
  return request.get('/statistics/heatmap')
}

export function getDailyStats(days = 30) {
  return request.get('/statistics/daily-stats', { params: { days } })
}

export function getReactionTime(days = 30) {
  return request.get('/statistics/reaction-time', { params: { days } })
}

export function getWeakWords() {
  return request.get('/statistics/weak-words')
}
