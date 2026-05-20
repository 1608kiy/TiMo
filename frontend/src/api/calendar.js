import request from './request'

export function getMonthly(year, month) {
  return request.get('/calendar/monthly', { params: { year, month } })
}

export function checkin(data) {
  return request.post('/calendar/checkin', data || {})
}
