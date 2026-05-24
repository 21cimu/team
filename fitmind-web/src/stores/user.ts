import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const PROFILE_SETUP_PENDING_KEY = 'profile-setup-pending'
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<any>(null)
  const profilePromptRequired = ref(false)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: any) {
    userInfo.value = info
    profilePromptRequired.value = !!info?.profilePromptRequired
  }

  function setProfilePromptRequired(required: boolean) {
    profilePromptRequired.value = required
    if (userInfo.value) {
      userInfo.value.profilePromptRequired = required
    }
  }

  function setProfileSetupPending(required: boolean) {
    if (required) {
      sessionStorage.setItem(PROFILE_SETUP_PENDING_KEY, '1')
    } else {
      sessionStorage.removeItem(PROFILE_SETUP_PENDING_KEY)
    }
  }

  function consumeProfileSetupPending() {
    const pending = sessionStorage.getItem(PROFILE_SETUP_PENDING_KEY) === '1'
    sessionStorage.removeItem(PROFILE_SETUP_PENDING_KEY)
    return pending
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    profilePromptRequired.value = false
    localStorage.removeItem('token')
    sessionStorage.removeItem(PROFILE_SETUP_PENDING_KEY)
  }

  return {
    token,
    userInfo,
    profilePromptRequired,
    setToken,
    setUserInfo,
    setProfilePromptRequired,
    setProfileSetupPending,
    consumeProfileSetupPending,
    logout
  }
})
