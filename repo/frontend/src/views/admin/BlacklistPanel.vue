<script setup lang="ts">
import { ref } from 'vue'
import apiClient from '@/api/client'
import type { BlacklistResponse } from '@/types'

const targetAccountId = ref<number | null>(null)
const reasonCode = ref('SAFETY_POLICY_BREACH')
const note = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')

const reasonCodes = [
  { value: 'SAFETY_POLICY_BREACH', label: 'Safety Policy Breach' },
  { value: 'FRAUD', label: 'Fraud' },
  { value: 'REPEATED_MISUSE', label: 'Repeated Misuse' },
  { value: 'OTHER', label: 'Other' },
]

async function handleSubmit() {
  errorMessage.value = ''
  successMessage.value = ''
  status.value = 'loading'

  try {
    const response = await apiClient.post<BlacklistResponse>('/admin/blacklist', {
      targetAccountId: targetAccountId.value,
      reasonCode: reasonCode.value,
      note: note.value || undefined,
    })
    status.value = 'success'
    successMessage.value = `Account ${response.data.accountId} blacklisted successfully (ID: ${response.data.blacklistId}).`
  } catch (err: any) {
    status.value = 'error'
    errorMessage.value =
      err.response?.data?.message || 'Failed to blacklist account.'
  }
}
</script>

<template>
  <div class="blacklist-panel">
    <h2>Blacklist Management</h2>

    <div v-if="status === 'success'" class="success-message">
      <p>{{ successMessage }}</p>
      <button @click="status = 'idle'; targetAccountId = null; note = ''">Blacklist Another</button>
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
        <label for="reasonCode">Reason Code</label>
        <select id="reasonCode" v-model="reasonCode">
          <option v-for="rc in reasonCodes" :key="rc.value" :value="rc.value">
            {{ rc.label }}
          </option>
        </select>
      </div>
      <div class="form-group">
        <label for="note">Note (optional)</label>
        <textarea id="note" v-model="note" rows="3"></textarea>
      </div>
      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>
      <button type="submit" :disabled="status === 'loading'">
        {{ status === 'loading' ? 'Submitting...' : 'Blacklist Account' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.blacklist-panel {
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
.form-group select,
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

button {
  padding: 0.75rem 1.5rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
