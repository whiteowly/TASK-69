<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { OperationsSummary } from '@/types'

const summary = ref<OperationsSummary | null>(null)
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const dateFrom = ref('')
const dateTo = ref('')

async function fetchSummary() {
  loadState.value = 'loading'
  loadError.value = ''
  try {
    const params: any = {}
    // Issue 8 fix: send ISO date-time values, not bare dates
    if (dateFrom.value) params.from = dateFrom.value + 'T00:00:00'
    else params.from = new Date(Date.now() - 30 * 86400000).toISOString().substring(0, 19)
    if (dateTo.value) params.to = dateTo.value + 'T23:59:59'
    else params.to = new Date().toISOString().substring(0, 19)
    const response = await apiClient.get<OperationsSummary>('/analytics/operations-summary', { params })
    summary.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load analytics.'
    loadState.value = 'error'
  }
}

function approvalRate(): string {
  if (!summary.value || summary.value.totalRegistrations === 0) return 'N/A'
  return ((summary.value.approvedRegistrations / summary.value.totalRegistrations) * 100).toFixed(1) + '%'
}

function cancellationRate(): string {
  if (!summary.value || summary.value.totalRegistrations === 0) return 'N/A'
  return ((summary.value.cancelledRegistrations / summary.value.totalRegistrations) * 100).toFixed(1) + '%'
}

function retentionPercent(): string {
  if (!summary.value) return 'N/A'
  return (summary.value.retentionRate * 100).toFixed(1) + '%'
}

function orderCompletionPercent(): string {
  if (!summary.value || summary.value.totalOrders === 0) return 'N/A'
  return (summary.value.orderCompletionRate * 100).toFixed(1) + '%'
}

onMounted(fetchSummary)
</script>

<template>
  <div class="analytics-dashboard">
    <h1>Analytics Dashboard</h1>

    <div class="filter-bar">
      <div class="form-row">
        <label>From</label>
        <input v-model="dateFrom" type="date" />
      </div>
      <div class="form-row">
        <label>To</label>
        <input v-model="dateTo" type="date" />
      </div>
      <button :disabled="loadState === 'loading'" @click="fetchSummary">
        {{ loadState === 'loading' ? 'Loading...' : 'Refresh' }}
      </button>
    </div>

    <p v-if="loadState === 'loading'">Loading analytics...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success' && summary" class="metrics-grid">
      <div class="metric-card">
        <div class="metric-value">{{ summary.totalRegistrations }}</div>
        <div class="metric-label">Total Registrations</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.approvedRegistrations }}</div>
        <div class="metric-label">Approved Registrations</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.cancelledRegistrations }}</div>
        <div class="metric-label">Cancelled Registrations</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.totalClaims }}</div>
        <div class="metric-label">Total Claims</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.allowedClaims }}</div>
        <div class="metric-label">Allowed Claims</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.deniedClaims }}</div>
        <div class="metric-label">Denied Claims</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ summary.totalOrders }}</div>
        <div class="metric-label">Total Orders</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ approvalRate() }}</div>
        <div class="metric-label">Approval Rate</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ cancellationRate() }}</div>
        <div class="metric-label">Cancellation Rate</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ orderCompletionPercent() }}</div>
        <div class="metric-label">Order Completion Rate</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ retentionPercent() }}</div>
        <div class="metric-label">Retention Rate</div>
      </div>
    </div>

    <div v-if="loadState === 'success' && summary" class="detail-sections">
      <section v-if="summary.staffWorkload && Object.keys(summary.staffWorkload).length > 0">
        <h2>Staff Workload (Fulfillment Actions)</h2>
        <table class="data-table">
          <thead><tr><th>Staff Account ID</th><th>Actions</th></tr></thead>
          <tbody>
            <tr v-for="(count, staffId) in summary.staffWorkload" :key="staffId">
              <td>{{ staffId }}</td><td>{{ count }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="summary.popularCategories && Object.keys(summary.popularCategories).length > 0">
        <h2>Popular Service Categories</h2>
        <table class="data-table">
          <thead><tr><th>Event Mode</th><th>Registrations</th></tr></thead>
          <tbody>
            <tr v-for="(count, mode) in summary.popularCategories" :key="mode">
              <td>{{ mode }}</td><td>{{ count }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.analytics-dashboard { max-width: 900px; }
.filter-bar { display: flex; gap: 1rem; align-items: flex-end; margin-bottom: 1.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input { padding: 0.4rem; }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 2rem; }
.metric-card { border: 1px solid #ddd; border-radius: 6px; padding: 1.25rem; text-align: center; }
.metric-value { font-size: 1.8rem; font-weight: bold; color: #1a1a2e; }
.metric-label { font-size: 0.85rem; color: #666; margin-top: 0.25rem; }
.detail-sections section { margin-bottom: 1.5rem; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { border: 1px solid #ddd; padding: 0.5rem; text-align: left; }
.data-table th { background: #f5f5f5; }
button { padding: 0.5rem 1rem; cursor: pointer; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
</style>
