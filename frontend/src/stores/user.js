import { defineStore } from 'pinia'
import { ref } from 'vue'

function parseTokenInfo(token) {
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return { userId: payload.sub ? Number(payload.sub) : null, email: payload.email, role: payload.role }
  } catch { return null }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(parseTokenInfo(token.value))

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})
