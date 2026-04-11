<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

interface PendingRegistration {
  id: number
  eventId: number
  eventTitle: string
  accountId: number
  status: string
  createdAt: string
}

interface ItemState {
  state: 'idle' | 'loading' | 'success' | 'error'
  error: string
}

const registrations = ref<PendingRegistration[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const itemStates = ref<Map<number, ItemState>>(new Map())

function getItemState(id: number): ItemState {
  if (!itemStates.value.has(id)) {
    itemStates.value.set(id, { state: 'idle', error: '' })
  }
  return itemStates.value.get(id)!
}

async function fetchPending() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<PendingRegistration[]>('/registrations/pending')
    registrations.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load registrations.'
    loadState.value = 'error'
  }
}

async function decide(reg: PendingRegistration, decision: 'APPROVE' | 'DENY') {
  const s = getItemState(reg.id)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/registrations/${reg.id}/decision`, { decision })
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Decision failed.'
    s.state = 'error'
  }
}

onMounted(fetchPending)
</script>

<template>
  <div class="registration-review">
    <h1>Registration Review</h1>

    <p v-if="loadState === 'loading'">Loading pending registrations...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="registrations.length === 0">No pending registrations.</p>
      <div v-for="reg in registrations" :key="reg.id" class="reg-item">
        <div class="reg-header">
          <span>Event: <strong>{{ reg.eventTitle }}</strong></span>
          <span>Account #{{ reg.accountId }}</span>
          <span class="badge">{{ reg.status }}</span>
        </div>
        <p class="reg-date">Registered: {{ reg.createdAt }}</p>

        <div v-if="getItemState(reg.id).state !== 'success'" class="decision-actions">
          <button
            :disabled="getItemState(reg.id).state === 'loading'"
            @click="decide(reg, 'APPROVE')"
          >
            {{ getItemState(reg.id).state === 'loading' ? 'Processing...' : 'Approve' }}
          </button>
          <button
            :disabled="getItemState(reg.id).state === 'loading'"
            @click="decide(reg, 'DENY')"
          >
            Deny
          </button>
          <p v-if="getItemState(reg.id).state === 'error'" class="error-message">{{ getItemState(reg.id).error }}</p>
        </div>

        <p v-if="getItemState(reg.id).state === 'success'" class="success-message">Decision submitted.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.registration-review { max-width: 800px; }
.reg-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.reg-header { display: flex; gap: 1rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #fff3cd; color: #856404; border-radius: 3px; }
.reg-date { font-size: 0.85rem; color: #777; margin: 0.25rem 0 0.5rem; }
.decision-actions { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
button { padding: 0.5rem 1rem; cursor: pointer; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
