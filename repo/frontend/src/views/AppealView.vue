<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import apiClient from '@/api/client'
import type { AppealResponse } from '@/types'

const router = useRouter()
const auth = useAuthStore()

const blacklistId = ref<number | null>(null)
const reasonCode = ref('')
const appealText = ref('')
const contactNote = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadStatus = ref<'loading' | 'loaded' | 'error'>('loading')
const loadError = ref('')

async function loadBlacklistInfo() {
  loadStatus.value = 'loading'
  try {
    const response = await apiClient.get('/appeals/my-blacklist')
    blacklistId.value = response.data.blacklistId
    reasonCode.value = response.data.reasonCode
    loadStatus.value = 'loaded'
  } catch (err: any) {
    loadStatus.value = 'error'
    loadError.value = 'Could not load blacklist information.'
  }
}

async function handleSubmit() {
  if (!blacklistId.value) return
  errorMessage.value = ''
  successMessage.value = ''
  status.value = 'loading'

  try {
    const response = await apiClient.post<AppealResponse>('/appeals', {
      blacklistId: blacklistId.value,
      appealText: appealText.value,
      contactNote: contactNote.value || undefined,
    })
    status.value = 'success'
    successMessage.value = `Appeal submitted successfully. Review due by: ${response.data.dueDate}`
  } catch (err: any) {
    status.value = 'error'
    errorMessage.value =
      err.response?.data?.message || 'Failed to submit appeal. Please try again.'
  }
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

onMounted(loadBlacklistInfo)
</script>

<template>
  <div class="appeal-container">
    <h1>Account Blacklisted</h1>
    <p class="blacklist-message">
      Your account has been blacklisted. If you believe this is an error, you may
      submit an appeal for admin review below.
    </p>

    <div v-if="loadStatus === 'loading'" class="loading">Loading blacklist information...</div>

    <div v-if="loadStatus === 'error'" class="error-message">
      {{ loadError }}
    </div>

    <div v-if="loadStatus === 'loaded' && reasonCode" class="blacklist-info">
      <p><strong>Reason:</strong> {{ reasonCode }}</p>
    </div>

    <div v-if="status === 'success'" class="success-message">
      <p>{{ successMessage }}</p>
    </div>

    <form v-else-if="loadStatus === 'loaded'" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label for="appealText">Your Appeal</label>
        <textarea
          id="appealText"
          v-model="appealText"
          required
          rows="4"
          placeholder="Explain why you believe the blacklist decision should be reconsidered..."
        ></textarea>
      </div>
      <div class="form-group">
        <label for="contactNote">Contact Note (optional)</label>
        <textarea
          id="contactNote"
          v-model="contactNote"
          rows="2"
          placeholder="How can an admin reach you? e.g. station desk hours"
        ></textarea>
      </div>
      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
      <button type="submit" :disabled="status === 'loading'">
        {{ status === 'loading' ? 'Submitting...' : 'Submit Appeal' }}
      </button>
    </form>

    <button class="logout-btn" @click="handleLogout">Logout</button>
  </div>
</template>

<style scoped>
.appeal-container {
  max-width: 500px;
  margin: 60px auto;
  padding: 2rem;
}

.blacklist-message {
  margin-bottom: 1.5rem;
  color: #666;
}

.blacklist-info {
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  padding: 0.75rem;
  margin-bottom: 1rem;
}

.loading {
  color: #888;
  margin-bottom: 1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
}

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
  text-align: center;
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

.logout-btn {
  margin-top: 1rem;
  background: #ccc;
  border: 1px solid #999;
}
</style>
