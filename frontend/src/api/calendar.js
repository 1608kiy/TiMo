import request from './request'

export function getMonthly(year, month) {
  return request.get('/calendar/monthly', { params: { year, month } })
}
