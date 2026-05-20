import request from './request'

export function sendChatMessage(data) {
  return request.post('/agent/chat/send', data)
}

export function loadChatHistory(sessionId) {
  return request.get('/agent/chat/history', { params: { sessionId } })
}

export function getRecommend() {
  return request.get('/agent/recommend')
}

export function getWeeklyReport() {
  return request.get('/agent/weekly-report')
}

export function generatePassage(words) {
  return request.post('/agent/generate-passage', { words })
}

export function getStubbornWords() {
  return request.get('/agent/stubborn-words')
}

export function getProgressAlert() {
  return request.get('/agent/progress-alert')
}

export function analyzeWord(word) {
  return request.post('/agent/analyze-word', { word })
}
