<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { WorkOrderResponse } from '@/types'

const workOrders = ref<WorkOrderResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const statusFilter = ref('')

const STATUS_TRANSITIONS: Record<string, string[]> = {
  NEW_ALERT: ['ACKNOWLEDGED'],
  ACKNOWLEDGED: ['DISPATCHED'],
  DISPATCHED: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED'],
}

interface ItemState {
  state: 'idle' | 'loading' | 'success' | 'error'
  note: string
  reviewSummary: string
  assigneeId: string
  error: string
  photoFile: File | null
  photoState: 'idle' | 'loading' | 'success' | 'error'
  photoError: string
}

const itemStates = ref<Map<number, ItemState>>(new Map())

function getItemState(id: number): ItemState {
  if (!itemStates.value.has(id)) {
    itemStates.value.set(id, { state: 'idle', note: '', reviewSummary: '', assigneeId: '', error: '', photoFile: null, photoState: 'idle', photoError: '' })
  }
  return itemStates.value.get(id)!
}

function nextStates(status: string): string[] {
  return STATUS_TRANSITIONS[status] || []
}

function formatSeconds(seconds?: number): string {
  if (seconds == null) return 'N/A'
  const mins = Math.floor(seconds / 60)
  const hrs = Math.floor(mins / 60)
  if (hrs > 0) return `${hrs}h ${mins % 60}m`
  return `${mins}m`
}

async function fetchWorkOrders() {
  loadState.value = 'loading'
  try {
    const params: any = {}
    if (statusFilter.value) params.status = statusFilter.value
    const response = await apiClient.get<WorkOrderResponse[]>('/work-orders', { params })
    workOrders.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load work orders.'
    loadState.value = 'error'
  }
}

async function transitionWorkOrder(woId: number, newStatus: string) {
  const s = getItemState(woId)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/work-orders/${woId}/transition`, { toStatus: newStatus })
    s.state = 'success'
    await fetchWorkOrders()
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Transition failed.'
    s.state = 'error'
  }
}

async function assignWorkOrder(woId: number) {
  const s = getItemState(woId)
  const assigneeId = parseInt(s.assigneeId, 10)
  if (isNaN(assigneeId) || assigneeId <= 0) {
    s.error = 'Please enter a valid account ID for the assignee.'
    s.state = 'error'
    return
  }
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/work-orders/${woId}/assign`, { assigneeId })
    s.assigneeId = ''
    s.state = 'success'
    await fetchWorkOrders()
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Assignment failed.'
    s.state = 'error'
  }
}

async function addNote(woId: number) {
  const s = getItemState(woId)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/work-orders/${woId}/notes`, { content: s.note })
    s.note = ''
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Failed to add note.'
    s.state = 'error'
  }
}

function onPhotoSelect(woId: number, event: Event) {
  const input = event.target as HTMLInputElement
  const s = getItemState(woId)
  s.photoFile = input.files?.[0] || null
}

async function uploadPhoto(woId: number) {
  const s = getItemState(woId)
  if (!s.photoFile) {
    s.photoError = 'Please select a photo file.'
    s.photoState = 'error'
    return
  }
  s.photoState = 'loading'
  s.photoError = ''
  try {
    const formData = new FormData()
    formData.append('file', s.photoFile)
    await apiClient.post(`/work-orders/${woId}/photos`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    s.photoFile = null
    s.photoState = 'success'
  } catch (err: any) {
    s.photoError = err.response?.data?.message || 'Photo upload failed.'
    s.photoState = 'error'
  }
}

async function submitReview(woId: number) {
  const s = getItemState(woId)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/work-orders/${woId}/post-incident-review`, { summary: s.reviewSummary, lessons: '', actions: '' })
    s.reviewSummary = ''
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Failed to submit review.'
    s.state = 'error'
  }
}

onMounted(fetchWorkOrders)
</script>

<template>
  <div class="work-order-panel">
    <h1>Work Order Panel</h1>

    <div class="filter-bar">
      <label>Status Filter</label>
      <select v-model="statusFilter" @change="fetchWorkOrders">
        <option value="">All</option>
        <option value="NEW_ALERT">NEW_ALERT</option>
        <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
        <option value="DISPATCHED">DISPATCHED</option>
        <option value="IN_PROGRESS">IN_PROGRESS</option>
        <option value="RESOLVED">RESOLVED</option>
        <option value="CLOSED">CLOSED</option>
      </select>
    </div>

    <p v-if="loadState === 'loading'">Loading work orders...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="workOrders.length === 0">No work orders found.</p>
      <div v-for="wo in workOrders" :key="wo.id" class="wo-item">
        <div class="wo-header">
          <strong>{{ wo.title }}</strong>
          <span class="badge" :class="'severity-' + wo.severity.toLowerCase()">{{ wo.severity }}</span>
          <span class="badge">{{ wo.status }}</span>
          <span v-if="wo.assignedTo">Assigned: #{{ wo.assignedTo }}</span>
        </div>
        <p class="wo-date">Created: {{ wo.createdAt }}</p>

        <div class="sla-info">
          <span>First Response: {{ formatSeconds(wo.firstResponseSeconds) }}</span>
          <span>Time to Close: {{ formatSeconds(wo.timeToCloseSeconds) }}</span>
        </div>

        <div v-if="nextStates(wo.status).length > 0" class="transition-actions">
          <button
            v-for="ns in nextStates(wo.status)"
            :key="ns"
            :disabled="getItemState(wo.id).state === 'loading'"
            @click="transitionWorkOrder(wo.id, ns)"
          >
            {{ ns }}
          </button>
        </div>

        <div v-if="wo.status === 'DISPATCHED'" class="assign-form">
          <label>Assign to Account ID</label>
          <div class="assign-row">
            <input v-model="getItemState(wo.id).assigneeId" type="number" min="1" placeholder="Account ID" />
            <button :disabled="getItemState(wo.id).state === 'loading' || !getItemState(wo.id).assigneeId" @click="assignWorkOrder(wo.id)">
              Assign
            </button>
          </div>
        </div>

        <div class="note-form">
          <input v-model="getItemState(wo.id).note" placeholder="Add a note..." />
          <button :disabled="getItemState(wo.id).state === 'loading' || !getItemState(wo.id).note" @click="addNote(wo.id)">
            Add Note
          </button>
        </div>

        <div class="photo-form">
          <label>Upload Photo</label>
          <input type="file" accept="image/*" @change="onPhotoSelect(wo.id, $event)" />
          <button :disabled="getItemState(wo.id).photoState === 'loading' || !getItemState(wo.id).photoFile" @click="uploadPhoto(wo.id)">
            {{ getItemState(wo.id).photoState === 'loading' ? 'Uploading...' : 'Upload Photo' }}
          </button>
          <p v-if="getItemState(wo.id).photoState === 'success'" class="success-message">Photo uploaded.</p>
          <p v-if="getItemState(wo.id).photoState === 'error'" class="error-message">{{ getItemState(wo.id).photoError }}</p>
        </div>

        <div v-if="wo.status === 'CLOSED'" class="review-form">
          <label>Post-Incident Review</label>
          <textarea v-model="getItemState(wo.id).reviewSummary" placeholder="Review summary..." rows="2" />
          <button :disabled="getItemState(wo.id).state === 'loading' || !getItemState(wo.id).reviewSummary" @click="submitReview(wo.id)">
            Submit Review
          </button>
        </div>

        <p v-if="getItemState(wo.id).state === 'error'" class="error-message">{{ getItemState(wo.id).error }}</p>
        <p v-if="getItemState(wo.id).state === 'success'" class="success-message">Updated.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.work-order-panel { max-width: 900px; }
.filter-bar { margin-bottom: 1rem; display: flex; gap: 0.5rem; align-items: center; }
.filter-bar select { padding: 0.4rem; }
.wo-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.wo-header { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; border-radius: 3px; background: #e2e3e5; }
.severity-critical { background: #f8d7da; color: #721c24; }
.severity-high { background: #fff3cd; color: #856404; }
.severity-medium { background: #d1ecf1; color: #0c5460; }
.severity-low { background: #e2e3e5; color: #383d41; }
.wo-date { font-size: 0.85rem; color: #777; margin: 0.25rem 0 0.5rem; }
.sla-info { display: flex; gap: 1.5rem; font-size: 0.85rem; color: #555; margin-bottom: 0.5rem; }
.transition-actions { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; }
.assign-form { margin-top: 0.5rem; margin-bottom: 0.5rem; }
.assign-form label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.assign-row { display: flex; gap: 0.5rem; }
.assign-row input { width: 150px; padding: 0.4rem; }
.note-form { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
.photo-form { margin-top: 0.5rem; display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.photo-form label { font-weight: bold; font-size: 0.85rem; }
.note-form input { flex: 1; padding: 0.4rem; }
.review-form { margin-top: 0.5rem; }
.review-form label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.review-form textarea { width: 100%; padding: 0.4rem; box-sizing: border-box; }
button { padding: 0.5rem 1rem; cursor: pointer; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
