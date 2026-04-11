<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

interface AuditEntry {
  id: number
  actionType: string
  objectType: string
  objectId: string
  accountId: number
  detail: string
  createdAt: string
}

const entries = ref<AuditEntry[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

const filterActionType = ref('')
const filterObjectType = ref('')
const filterFrom = ref('')
const filterTo = ref('')
const page = ref(0)
const pageSize = 25
const hasMore = ref(false)

async function fetchEntries(resetPage = true) {
  if (resetPage) page.value = 0
  loadState.value = 'loading'
  loadError.value = ''
  try {
    const params: any = { page: page.value, size: pageSize }
    if (filterActionType.value) params.actionType = filterActionType.value
    if (filterObjectType.value) params.objectType = filterObjectType.value
    if (filterFrom.value) params.from = filterFrom.value
    if (filterTo.value) params.to = filterTo.value
    const response = await apiClient.get<AuditEntry[]>('/admin/audit-logs', { params })
    entries.value = response.data
    hasMore.value = response.data.length === pageSize
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load audit logs.'
    loadState.value = 'error'
  }
}

function nextPage() {
  page.value++
  fetchEntries(false)
}

function prevPage() {
  if (page.value > 0) {
    page.value--
    fetchEntries(false)
  }
}

onMounted(() => fetchEntries())
</script>

<template>
  <div class="audit-log-viewer">
    <h1>Audit Log Viewer</h1>

    <div class="filter-bar">
      <div class="form-row">
        <label>Action Type</label>
        <input v-model="filterActionType" placeholder="e.g. LOGIN" />
      </div>
      <div class="form-row">
        <label>Object Type</label>
        <input v-model="filterObjectType" placeholder="e.g. ACCOUNT" />
      </div>
      <div class="form-row">
        <label>From</label>
        <input v-model="filterFrom" type="date" />
      </div>
      <div class="form-row">
        <label>To</label>
        <input v-model="filterTo" type="date" />
      </div>
      <button :disabled="loadState === 'loading'" @click="fetchEntries(true)">
        {{ loadState === 'loading' ? 'Loading...' : 'Search' }}
      </button>
    </div>

    <p v-if="loadState === 'loading'">Loading audit logs...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="entries.length === 0">No audit entries found.</p>
      <table v-if="entries.length > 0" class="audit-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Action</th>
            <th>Object</th>
            <th>Object ID</th>
            <th>Account</th>
            <th>Detail</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in entries" :key="entry.id">
            <td>{{ entry.id }}</td>
            <td>{{ entry.actionType }}</td>
            <td>{{ entry.objectType }}</td>
            <td>{{ entry.objectId }}</td>
            <td>#{{ entry.accountId }}</td>
            <td>{{ entry.detail }}</td>
            <td>{{ entry.createdAt }}</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <button :disabled="page === 0" @click="prevPage">Previous</button>
        <span>Page {{ page + 1 }}</span>
        <button :disabled="!hasMore" @click="nextPage">Next</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.audit-log-viewer { max-width: 1000px; }
.filter-bar { display: flex; gap: 1rem; align-items: flex-end; margin-bottom: 1rem; flex-wrap: wrap; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input { padding: 0.4rem; }
.audit-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.audit-table th, .audit-table td { border: 1px solid #ddd; padding: 0.5rem; text-align: left; }
.audit-table th { background: #f0f0f0; font-weight: bold; }
.pagination { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
button { padding: 0.5rem 1rem; cursor: pointer; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
</style>
