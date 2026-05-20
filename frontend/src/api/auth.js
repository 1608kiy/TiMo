import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function sendResetCode(email) {
  return request.post('/auth/forgot-password/send-code', { email })
}

export function resetPassword(email, code, newPassword) {
  return request.post('/auth/forgot-password/reset', { email, code, newPassword })
}

export function logout() {
  return request.post('/auth/logout')
}
