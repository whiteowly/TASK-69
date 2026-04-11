<script setup lang="ts">
import { ref } from 'vue'
import apiClient from '@/api/client'
import type { PasswordResetResponse } from '@/types'

const targetAccountId = ref<number | null>(null)
const identityReviewNote = ref('')
const errorMessage = ref('')
const temporarySecret = ref('')
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')

async function handleSubmit() {
  errorMessage.value = ''
  temporarySecret.value = ''
  status.value = 'loading'

  try {
    const response = await apiClient.post<PasswordResetResponse>('/admin/password-resets', {
      targetAccountId: targetAccountId.value,
      identityReviewNote: identityReviewNote.value,
    })
    status.value = 'success'
    temporarySecret.value = response.data.temporarySecret
  } catch (err: any) {
    status.value = 'error'
    errorMessage.value =
      err.response?.data?.message || 'Failed to reset password.'
  }
}
</script>

<template>
  <div class="password-reset-panel">
    <h2>Password Reset</h2>

    <div v-if="status === 'success'" class="success-message">
      <p>Password reset successful.</p>
      <p>Temporary password: <code class="temp-secret">{{ temporarySecret }}</code></p>
      <button @click="status = 'idle'; targetAccountId = null; identityReviewNote = ''; temporarySecret = ''">
        Reset Another
      </button>
    </div>

    <form v-else @submit.prevent="handleSubmit">
      <div class="form-group">
        <label for="targetAccountId">Target Account ID</label>
        <input
          id="targetAccountId"
          v-model.number="targetAccountId"
          type="number"
          required
        />
      </div>
      <div class="form-group">
        <label for="identityReviewNote">Identity Review Note</label>
        <textarea
          id="identityReviewNote"
          v-model="identityReviewNote"
          required
          rows="3"
        ></textarea>
      </div>
      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
      <button type="submit" :disabled="status === 'loading'">
        {{ status === 'loading' ? 'Resetting...' : 'Reset Password' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.password-reset-panel {
  max-width: 500px;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.error-message {
  color: red;
  margin-bottom: 1rem;
}

.success-message {
  color: green;
  margin-bottom: 1rem;
}

.temp-secret {
  background: #f0f0f0;
  padding: 0.25rem 0.5rem;
  border-radius: 3px;
  font-size: 1.1rem;
  user-select: all;
}

button {
  padding: 0.75rem 1.5rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
