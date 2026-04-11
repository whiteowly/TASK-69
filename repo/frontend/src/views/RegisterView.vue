<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const username = ref('')
const password = ref('')
const accountType = ref<'PERSON' | 'ORGANIZATION'>('PERSON')
const errorMessage = ref('')
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')

async function handleRegister() {
  errorMessage.value = ''

  if (!username.value || !password.value) {
    status.value = 'error'
    errorMessage.value = 'Username and password are required.'
    return
  }

  status.value = 'loading'

  try {
    await auth.register(username.value, password.value, accountType.value)
    status.value = 'success'
  } catch (err: any) {
    status.value = 'error'
    errorMessage.value =
      err.response?.data?.message || 'Registration failed. Please try again.'
  }
}
</script>

<template>
  <div class="register-container">
    <h1>Create Account</h1>

    <div v-if="status === 'success'" class="success-message">
      <p>Registration successful!</p>
      <router-link to="/login">Go to Login</router-link>
    </div>

    <form v-else @submit.prevent="handleRegister">
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
          autocomplete="new-password"
        />
      </div>
      <div class="form-group">
        <label for="accountType">Account Type</label>
        <select id="accountType" v-model="accountType">
          <option value="PERSON">Person</option>
          <option value="ORGANIZATION">Organization</option>
        </select>
      </div>
      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
      <button type="submit" :disabled="status === 'loading'">
        {{ status === 'loading' ? 'Registering...' : 'Register' }}
      </button>
      <p class="login-link">
        Already have an account? <router-link to="/login">Sign in</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.register-container {
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

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.error-message {
  color: red;
  margin-bottom: 1rem;
}

.success-message {
  text-align: center;
  color: green;
}

.success-message a {
  display: inline-block;
  margin-top: 1rem;
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

.login-link {
  margin-top: 1rem;
  text-align: center;
}
</style>
