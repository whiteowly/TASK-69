<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { VerificationQueueItem, VerificationDecisionRequest } from '@/types'

interface QueueItemState {
  decision: string
  reasonCode: string
  reviewNote: string
  state: 'idle' | 'loading' | 'success' | 'error'
  error: string
}

const items = ref<VerificationQueueItem[]>([])
const itemStates = ref<Map<number, QueueItemState>>(new Map())
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

function getItemState(id: number): QueueItemState {
  if (!itemStates.value.has(id)) {
    itemStates.value.set(id, { decision: 'APPROVE', reasonCode: '', reviewNote: '', state: 'idle', error: '' })
  }
  return itemStates.value.get(id)!
}

async function fetchQueue() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<VerificationQueueItem[]>('/admin/verification/queue')
    items.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load queue.'
    loadState.value = 'error'
  }
}

async function submitDecision(item: VerificationQueueItem) {
  const s = getItemState(item.id)
  s.state = 'loading'
  s.error = ''
  const endpoint = item.type === 'PERSON'
    ? `/admin/verification/person/${item.id}/decision`
    : `/admin/verification/org-document/${item.id}/decision`
  try {
    await apiClient.post(endpoint, {
      decision: s.decision,
      reasonCode: s.reasonCode || undefined,
      reviewNote: s.reviewNote || undefined,
    } as VerificationDecisionRequest)
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Decision failed.'
    s.state = 'error'
  }
}

onMounted(fetchQueue)
</script>

<template>
  <div class="verification-queue">
    <h1>Verification Review</h1>

    <p v-if="loadState === 'loading'">Loading queue...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="items.length === 0">No pending verifications.</p>

      <div v-for="item in items" :key="item.id" class="queue-item">
        <div class="item-header">
          <span class="item-type">{{ item.type }}</span>
          <span class="item-account">Account #{{ item.accountId }}</span>
          <span class="item-status">{{ item.status }}</span>
        </div>

        <div v-if="item.type === 'PERSON'" class="item-details">
          <p><strong>Legal Name:</strong> {{ item.legalName }}</p>
          <p><strong>Date of Birth:</strong> {{ item.dobMasked || '****-**-**' }}</p>
        </div>

        <div v-if="item.type === 'ORG_DOCUMENT'" class="item-details">
          <p><strong>File:</strong> {{ item.fileName }}</p>
          <p><strong>Size:</strong> {{ item.fileSize }} bytes</p>
          <p><strong>Type:</strong> {{ item.contentType }}</p>
          <p v-if="item.duplicateFlag" class="warning-message">Duplicate flag detected</p>
        </div>

        <div v-if="getItemState(item.id).state !== 'success'" class="decision-form">
          <div class="form-row">
            <label>Decision</label>
            <select v-model="getItemState(item.id).decision">
              <option value="APPROVE">APPROVE</option>
              <option value="DENY">DENY</option>
            </select>
          </div>
          <div class="form-row">
            <label>Review Note</label>
            <textarea v-model="getItemState(item.id).reviewNote" placeholder="Optional note" rows="2" />
          </div>
          <button
            :disabled="getItemState(item.id).state === 'loading'"
            @click="submitDecision(item)"
          >
            {{ getItemState(item.id).state === 'loading' ? 'Submitting...' : 'Submit Decision' }}
          </button>
          <p v-if="getItemState(item.id).state === 'error'" class="error-message">
            {{ getItemState(item.id).error }}
          </p>
        </div>

        <p v-if="getItemState(item.id).state === 'success'" class="success-message">
          Decision submitted successfully.
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.verification-queue { max-width: 800px; }
.queue-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 1rem; }
.item-header { display: flex; gap: 1rem; align-items: center; margin-bottom: 0.5rem; }
.item-type { font-weight: bold; padding: 0.15rem 0.5rem; background: #e2e3e5; border-radius: 3px; }
.item-account { color: #555; }
.item-status { font-size: 0.85rem; color: #856404; background: #fff3cd; padding: 0.15rem 0.5rem; border-radius: 3px; }
.item-details { margin-bottom: 0.75rem; }
.item-details p { margin: 0.25rem 0; }
.decision-form { border-top: 1px solid #eee; padding-top: 0.75rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row select, .form-row textarea { width: 100%; padding: 0.4rem; box-sizing: border-box; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
.warning-message { color: orange; font-weight: bold; }
</style>
