import request from './request'

export function getWordList(params) {
  return request.get('/words', { params })
}

export function getWordDetail(id) {
  return request.get(`/words/${id}`)
}

export function getWordFsrsState(wordId) {
  return request.get(`/words/${wordId}/fsrs-state`)
}

export function getWordBatch(ids) {
  return request.get('/words/batch', {
    params: { ids },
    paramsSerializer: params => {
      const parts = []
      for (const [key, val] of Object.entries(params)) {
        if (Array.isArray(val)) {
          val.forEach(v => parts.push(`${key}=${encodeURIComponent(v)}`))
        } else if (val !== undefined) {
          parts.push(`${key}=${encodeURIComponent(val)}`)
        }
      }
      return parts.join('&')
    }
  })
}

export function searchWords(keyword) {
  return request.get('/words/search', { params: { keyword } })
}

export function getWordCount(examType) {
  return request.get('/words/count', { params: examType ? { examType } : {} })
}
