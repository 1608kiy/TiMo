import { ref, onMounted, onBeforeUnmount } from 'vue'
import emitter from '../events'

/**
 * Composable for monitoring network connectivity.
 * Listens to browser online/offline events and request.js network error events.
 * Returns reactive isOnline ref.
 */
export function useNetwork() {
  const isOnline = ref(navigator.onLine)

  // Browser online/offline events
  function onBrowserOnline() {
    isOnline.value = true
  }

  function onBrowserOffline() {
    isOnline.value = false
  }

  // request.js meltdown (3 consecutive failures)
  let offMeltdown = null
  function onMeltdown() {
    isOnline.value = false
  }

  // request.js network error (no response from server)
  let offNetworkOffline = null
  function onNetworkOffline() {
    isOnline.value = false
  }

  // request.js successful call — if we were offline, we're back
  let offSuccess = null
  function onApiSuccess() {
    if (!isOnline.value) {
      isOnline.value = true
    }
  }

  onMounted(() => {
    window.addEventListener('online', onBrowserOnline)
    window.addEventListener('offline', onBrowserOffline)
    offMeltdown = emitter.on('api:meltdown', onMeltdown)
    offNetworkOffline = emitter.on('network:offline', onNetworkOffline)
    offSuccess = emitter.on('api:call-success', onApiSuccess)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('online', onBrowserOnline)
    window.removeEventListener('offline', onBrowserOffline)
    offMeltdown?.()
    offNetworkOffline?.()
    offSuccess?.()
  })

  return { isOnline }
}
