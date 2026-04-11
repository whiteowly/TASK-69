<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')

async function handleLogin() {
  errorMessage.value = ''
  status.value = 'loading'

  try {
    await auth.login(username.value, password.value)
    status.value = 'success'
    router.push(`/workspace/${auth.activeRole}`)
  } catch (err: any) {
    if (auth.accountStatus === 'BLACKLISTED') {
      // Session was created with constrained blacklist access — go to appeal
      router.push('/appeal')
      return
    }
    if (auth.accountStatus === 'LOCKED') {
      router.push('/locked')
      return
    }
    status.value = 'error'
    errorMessage.value =
      err.response?.data?.message || 'Login failed. Please try again.'
  }
}
</script>

<template>
  <div class="login-container">
    <h1>Community Resilience Operations Hub</h1>
    <form @submit.prevent="handleLogin">
      <div class="form-group">
        <label for="username">Username</label>
        <input
          id="username"
          v-model="username"
          type="text"
          required
          autocomplete="username"
        />
      </div>
      <div class="form-group">
        <label for="password">Password</label>
        <input
          id="password"
          v-model="password"
          type="password"
          required
          autocomplete="current-password"
        />
      </div>
      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
      <button type="submit" :disabled="status === 'loading'">
        {{ status === 'loading' ? 'Signing in...' : 'Sign In' }}
      </button>
      <p class="register-link">
        Don't have an account? <router-link to="/register">Register</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.login-container {
  max-width: 400px;
  margin: 100px auto;
  padding: 2rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
}

.form-group input {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.error-message {
  color: red;
  margin-bottom: 1rem;
}

button {
  width: 100%;
  padding: 0.75rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.register-link {
  margin-top: 1rem;
  text-align: center;
}
</style>
