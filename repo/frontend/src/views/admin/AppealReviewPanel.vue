<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { AppealResponse } from '@/types'

const appeals = ref<AppealResponse[]>([])
const loadError = ref('')
const loadStatus = ref<'idle' | 'loading' | 'error'>('idle')

const decisionForms = ref<Record<number, {
  decision: string
  decisionNote: string
  status: 'idle' | 'loading' | 'success' | 'error'
  errorMessage: string
  successMessage: string
}>>({})

async function fetchAppeals() {
  loadStatus.value = 'loading'
  loadError.value = ''
  try {
    const response = await apiClient.get<AppealResponse[]>('/admin/appeals')
    appeals.value = response.data
    for (const appeal of appeals.value) {
      if (!decisionForms.value[appeal.appealId]) {
        decisionForms.value[appeal.appealId] = {
          decision: 'APPROVE_UNBLOCK',
          decisionNote: '',
          status: 'idle',
          errorMessage: '',
          successMessage: '',
        }
      }
    }
  } catch (err: any) {
    loadStatus.value = 'error'
    loadError.value = err.response?.data?.message || 'Failed to load appeals.'
    return
  }
  loadStatus.value = 'idle'
}

async function handleDecision(appealId: number) {
  const form = decisionForms.value[appealId]
  form.errorMessage = ''
  form.successMessage = ''
  form.status = 'loading'

  try {
    await apiClient.post(`/admin/appeals/${appealId}/decision`, {
      decision: form.decision,
      decisionNote: form.decisionNote || undefined,
    })
    form.status = 'success'
    const label = form.decision === 'APPROVE_UNBLOCK' ? 'approved (unblocked)' : 'denied'
    form.successMessage = `Appeal #${appealId} ${label}.`
  } catch (err: any) {
    form.status = 'error'
    form.errorMessage =
      err.response?.data?.message || 'Failed to submit decision.'
  }
}

onMounted(fetchAppeals)
</script>

<template>
  <div class="appeal-review-panel">
    <h2>Appeal Review</h2>

    <div v-if="loadError" class="error-message">{{ loadError }}</div>
    <div v-if="loadStatus === 'loading'">Loading appeals...</div>

    <div v-if="appeals.length === 0 && loadStatus === 'idle'" class="empty-state">
      No pending appeals.
    </div>

    <div v-for="appeal in appeals" :key="appeal.appealId" class="appeal-card">
      <div class="appeal-info">
        <p><strong>Appeal #{{ appeal.appealId }}</strong></p>
        <p><strong>Account ID:</strong> {{ appeal.accountId }}</p>
        <p><strong>Appeal text:</strong></p>
        <p class="appeal-text">{{ appeal.appealText }}</p>
        <p v-if="appeal.contactNote"><strong>Contact note:</strong> {{ appeal.contactNote }}</p>
        <p class="due-date">Due: {{ appeal.dueDate }}</p>
      </div>

      <div v-if="decisionForms[appeal.appealId]?.status === 'success'" class="success-message">
        {{ decisionForms[appeal.appealId].successMessage }}
      </div>

      <form v-else @submit.prevent="handleDecision(appeal.appealId)" class="decision-form">
        <div class="form-group">
          <label :for="'decision-' + appeal.appealId">Decision</label>
          <select
            :id="'decision-' + appeal.appealId"
            v-model="decisionForms[appeal.appealId].decision"
          >
            <option value="APPROVE_UNBLOCK">Approve &amp; Unblock</option>
            <option value="DENY">Deny</option>
          </select>
        </div>
        <div class="form-group">
          <label :for="'note-' + appeal.appealId">Decision Note</label>
          <textarea
            :id="'note-' + appeal.appealId"
            v-model="decisionForms[appeal.appealId].decisionNote"
            rows="2"
          ></textarea>
        </div>
        <div v-if="decisionForms[appeal.appealId]?.errorMessage" class="error-message">
          {{ decisionForms[appeal.appealId].errorMessage }}
        </div>
        <button
          type="submit"
          :disabled="decisionForms[appeal.appealId]?.status === 'loading'"
        >
          {{ decisionForms[appeal.appealId]?.status === 'loading' ? 'Submitting...' : 'Submit Decision' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.appeal-review-panel {
  max-width: 700px;
}

.appeal-card {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.appeal-info {
  margin-bottom: 0.75rem;
}

.appeal-text {
  background: #f9f9f9;
  padding: 0.5rem;
  border-radius: 4px;
  white-space: pre-wrap;
}

.due-date {
  font-size: 0.85rem;
  color: #888;
  margin-top: 0.5rem;
}

.decision-form {
  border-top: 1px solid #eee;
  padding-top: 0.75rem;
}

.form-group {
  margin-bottom: 0.75rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
}

.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.error-message {
  color: red;
  margin-bottom: 0.75rem;
}

.success-message {
  color: green;
}

.empty-state {
  color: #888;
  margin-top: 1rem;
}

button {
  padding: 0.5rem 1rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
