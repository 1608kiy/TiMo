import request from './request'

// === Auth ===
export const verifyAdminSecret = (secret) => request.post('/admin/auth/verify-secret', { secret })
export const impersonateUser = (targetUserId) => request.post(`/admin/auth/impersonate/${targetUserId}`)
export const exitImpersonate = () => request.post('/admin/auth/exit-impersonate')

// === Dashboard ===
export const getDashboardOverview = () => request.get('/admin/dashboard/overview')
export const getDashboardTrend = (days = 7) => request.get('/admin/dashboard/trend', { params: { days } })

// === Users ===
export const getAdminUsers = (params) => request.get('/admin/users', { params })
export const getAdminUserDetail = (id) => request.get(`/admin/users/${id}`)
export const updateUserRole = (id, role) => request.put(`/admin/users/${id}/role`, { role })
export const updateUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, { status })
export const deleteAdminUser = (id) => request.delete(`/admin/users/${id}`)

// === Words ===
export const getAdminWords = (params) => request.get('/admin/words', { params })
export const createAdminWord = (data) => request.post('/admin/words', data)
export const updateAdminWord = (id, data) => request.put(`/admin/words/${id}`, data)
export const deleteAdminWord = (id) => request.delete(`/admin/words/${id}`)
export const importWords = (formData) => request.post('/admin/words/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const deleteWordsByExamType = (examTypes) => request.delete('/admin/words/by-exam-type', { data: examTypes })

// === AI ===
export const getAIProviders = () => request.get('/admin/ai/providers')
export const createAIProvider = (data) => request.post('/admin/ai/providers', data)
export const updateAIProvider = (id, data) => request.put(`/admin/ai/providers/${id}`, data)
export const activateAIProvider = (id) => request.put(`/admin/ai/providers/${id}/activate`)
export const deleteAIProvider = (id) => request.delete(`/admin/ai/providers/${id}`)
export const getAILogs = (params) => request.get('/admin/ai/logs', { params })
export const getAIStats = (days = 7) => request.get('/admin/ai/stats', { params: { days } })

// === System Config ===
export const getSystemConfigs = () => request.get('/admin/system/config')
export const updateSystemConfig = (key, value) => request.put(`/admin/system/config/${key}`, { value })
export const batchUpdateConfigs = (configs) => request.put('/admin/system/config', configs)

// === Logs ===
export const getOperationLogs = (params) => request.get('/admin/logs', { params })
