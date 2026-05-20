import request from './request'

export function getProfile() {
  return request.get('/user/profile')
}

export function updatePreferences(data) {
  return request.put('/user/preferences', data)
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteAccount() {
  return request.delete('/user/account')
}
