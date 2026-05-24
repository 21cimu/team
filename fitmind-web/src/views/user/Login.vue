<template>
  <div class="auth-screen">
    <section class="auth-story">
      <div class="text-label">[ Account Access ]</div>
      <div class="auth-number">02</div>
      <h1 class="text-display-lg">Return to the training cycle you already started.</h1>
      <p class="auth-copy">
        Plans, logs, body profile and AI dialogue stay in one archive. Sign in and continue the same rhythm,
        not a fresh setup every time.
      </p>

      <div class="auth-ledger">
        <article class="auth-ledger-card">
          <span>Coach</span>
          <strong>Conversation remembers context</strong>
          <p>Recent training, current goal and profile constraints stay visible to the system.</p>
        </article>
        <article class="auth-ledger-card">
          <span>Flow</span>
          <strong>Plan and execution stay connected</strong>
          <p>The app reads training, nutrition and recovery as one continuous operating surface.</p>
        </article>
      </div>
    </section>

    <section class="auth-form-panel nd-card">
      <div class="form-header">
        <div class="text-label">[ Login ]</div>
        <h2 class="text-display-md">Enter System</h2>
        <p class="text-secondary mt-sm">Continue the editorial workflow for training and nutrition.</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent.native="handleLogin" label-position="top">
        <el-form-item label="Username" prop="username">
          <input v-model="form.username" class="nd-input" type="text" autocomplete="off" />
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <input v-model="form.password" class="nd-input" type="password" autocomplete="off" />
        </el-form-item>

        <div v-if="errorMessage" class="feedback feedback-error">{{ errorMessage }}</div>

        <div class="form-actions">
          <button type="submit" class="nd-btn primary submit-btn" :disabled="loading">
            {{ loading ? 'Checking...' : 'Login And Continue' }}
          </button>
          <button type="button" class="switch-link" @click="$router.push('/register')">
            Need an account? Create one.
          </button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo, login } from '../../api/user'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()

const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter password', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    loading.value = true
    errorMessage.value = ''

    try {
      const loginResult: any = await login(form)
      const token = typeof loginResult === 'string' ? loginResult : loginResult?.token
      userStore.setToken(token as string)
      userStore.setProfilePromptRequired(!!loginResult?.profilePromptRequired)
      userStore.setProfileSetupPending(!!loginResult?.profilePromptRequired)
      try {
        const userInfo = await getUserInfo()
        userStore.setUserInfo(userInfo)
      } catch (error) {
        // user info fetch failed, still proceed
      }
      router.push('/app/dashboard')
    } catch (error) {
      errorMessage.value = 'Invalid username or password.'
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth-screen {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.08fr minmax(380px, 0.92fr);
  gap: 24px;
  padding: 28px;
}

.auth-story {
  position: relative;
  overflow: hidden;
  padding: 36px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 34px;
  background: rgba(14, 16, 22, 0.76);
  backdrop-filter: blur(18px);
}

.auth-number {
  position: absolute;
  top: 28px;
  right: 28px;
  color: rgba(244, 238, 232, 0.1);
  font-family: var(--font-heading);
  font-size: clamp(5rem, 9vw, 7.4rem);
  line-height: 0.86;
}

.auth-story h1 {
  max-width: 9ch;
  margin-top: 18px;
  color: var(--text-inverse);
}

.auth-copy {
  max-width: 46ch;
  margin: 18px 0 0;
  color: var(--text-inverse-muted);
  line-height: 1.84;
}

.auth-ledger {
  display: grid;
  gap: 14px;
  margin-top: 34px;
  max-width: 520px;
}

.auth-ledger-card {
  padding: 20px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 24px;
  background: rgba(244, 238, 232, 0.05);
}

.auth-ledger-card span,
.auth-ledger-card strong {
  display: block;
}

.auth-ledger-card span {
  color: rgba(244, 238, 232, 0.52);
  font-size: 0.72rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.auth-ledger-card strong {
  margin-top: 10px;
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 1.18rem;
}

.auth-ledger-card p {
  margin: 10px 0 0;
  color: var(--text-inverse-muted);
  line-height: 1.72;
}

.auth-form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px;
}

.form-header {
  margin-bottom: 28px;
  color: var(--text-main);
}

.feedback {
  margin-top: 8px;
  padding: 12px 14px;
  border-radius: 16px;
  font-size: 0.86rem;
}

.feedback-error {
  background: rgba(185, 116, 105, 0.12);
  border: 1px solid rgba(185, 116, 105, 0.22);
  color: var(--error);
}

.form-actions {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 22px;
}

.submit-btn {
  width: 100%;
}

.switch-link {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  text-align: center;
  padding: 0;
}

.switch-link:hover {
  color: var(--text-main);
}

@media (max-width: 980px) {
  .auth-screen {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .auth-screen {
    padding: 14px;
  }

  .auth-story,
  .auth-form-panel {
    padding: 22px;
  }
}

.auth-screen {
  gap: 0;
  background:
    linear-gradient(90deg, transparent 0, transparent calc(50% - 1px), rgba(244, 238, 232, 0.05) calc(50% - 1px), rgba(244, 238, 232, 0.05) calc(50% + 1px), transparent calc(50% + 1px));
}

.auth-story {
  border-radius: 0;
  border-right: none;
  background: #0f1117;
  backdrop-filter: none;
}

.auth-ledger {
  gap: 0;
}

.auth-ledger-card {
  border-radius: 0;
  background: transparent;
  border-left: 3px solid rgba(244, 238, 232, 0.18);
}

.auth-ledger-card + .auth-ledger-card {
  border-top: none;
}

.auth-form-panel {
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 980px) {
  .auth-screen {
    gap: 18px;
    background: none;
  }

  .auth-story {
    border-right: 1px solid rgba(244, 238, 232, 0.1);
  }
}
</style>
