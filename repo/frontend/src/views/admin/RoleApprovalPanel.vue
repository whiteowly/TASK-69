<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

interface PendingRoleRequest {
  id: number
  accountId: number
  roleType: string
  scopeId?: string
  status: string
  createdAt: string
}

interface ItemState {
  decision: string
  reviewNote: string
  state: 'idle' | 'loading' | 'success' | 'error'
  error: string
}

const requests = ref<PendingRoleRequest[]>([])
const itemStates = ref<Map<number, ItemState>>(new Map())
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

function getItemState(id: number): ItemState {
  if (!itemStates.value.has(id)) {
    itemStates.value.set(id, { decision: 'APPROVE', reviewNote: '', state: 'idle', error: '' })
  }
  return itemStates.value.get(id)!
}

async function fetchPending() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<PendingRoleRequest[]>('/admin/roles/pending')
    requests.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load pending roles.'
    loadState.value = 'error'
  }
}

async function submitDecision(request: PendingRoleRequest) {
  const s = getItemState(request.id)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/admin/roles/${request.id}/decision`, {
      decision: s.decision,
      reviewNote: s.reviewNote || undefined,
    })
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Decision failed.'
    s.state = 'error'
  }
}

onMounted(fetchPending)
</script>

<template>
  <div class="role-approval-panel">
    <h1>Role Approval Panel</h1>

    <p v-if="loadState === 'loading'">Loading pending requests...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="requests.length === 0">No pending role requests.</p>

      <div v-for="req in requests" :key="req.id" class="request-item">
        <div class="request-header">
          <span class="request-account">Account #{{ req.accountId }}</span>
          <span class="request-role">{{ req.roleType }}</span>
          <span v-if="req.scopeId" class="request-scope">Scope: {{ req.scopeId }}</span>
          <span class="request-status">{{ req.status }}</span>
        </div>
        <p class="request-date">Requested: {{ req.createdAt }}</p>

        <!-- Decision form -->
        <div v-if="getItemState(req.id).state !== 'success'" class="decision-form">
          <div class="form-row">
            <label>Decision</label>
            <select v-model="getItemState(req.id).decision">
              <option value="APPROVE">APPROVE</option>
              <option value="DENY">DENY</option>
            </select>
          </div>
          <div class="form-row">
            <label>Review Note</label>
            <textarea v-model="getItemState(req.id).reviewNote" placeholder="Optional note" rows="2" />
          </div>
          <button
            :disabled="getItemState(req.id).state === 'loading'"
            @click="submitDecision(req)"
          >
            {{ getItemState(req.id).state === 'loading' ? 'Submitting...' : 'Submit Decision' }}
          </button>
          <p v-if="getItemState(req.id).state === 'error'" class="error-message">
            {{ getItemState(req.id).error }}
          </p>
        </div>

        <p v-if="getItemState(req.id).state === 'success'" class="success-message">
          Decision submitted successfully.
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.role-approval-panel {
  max-width: 800px;
}

.request-item {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.request-header {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-bottom: 0.25rem;
}

.request-account {
  color: #555;
}

.request-role {
  font-weight: bold;
  padding: 0.15rem 0.5rem;
  background: #e2e3e5;
  border-radius: 3px;
}

.request-scope {
  font-size: 0.85rem;
  color: #333;
}

.request-status {
  font-size: 0.85rem;
  color: #856404;
  background: #fff3cd;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
}

.request-date {
  font-size: 0.85rem;
  color: #777;
  margin: 0.25rem 0 0.75rem;
}

.decision-form {
  border-top: 1px solid #eee;
  padding-top: 0.75rem;
}

.form-row {
  margin-bottom: 0.5rem;
}

.form-row label {
  display: block;
  font-weight: bold;
  font-size: 0.85rem;
  margin-bottom: 0.15rem;
}

.form-row select,
.form-row textarea {
  width: 100%;
  padding: 0.4rem;
  box-sizing: border-box;
}

button {
  padding: 0.5rem 1rem;
  cursor: pointer;
  margin-top: 0.25rem;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  color: red;
  margin-top: 0.5rem;
}

.success-message {
  color: green;
  margin-top: 0.5rem;
}
</style>
