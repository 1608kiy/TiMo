import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminStore = defineStore('admin', () => {
  const isImpersonating = ref(false)
  const impersonateUser = ref(null)

  function startImpersonate(user) {
    isImpersonating.value = true
    impersonateUser.value = user
  }

  function stopImpersonate() {
    isImpersonating.value = false
    impersonateUser.value = null
  }

  return { isImpersonating, impersonateUser, startImpersonate, stopImpersonate }
})
