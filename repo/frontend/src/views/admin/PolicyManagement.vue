<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { PolicyRequest } from '@/types'

interface PolicyResponse {
  id: number
  name: string
  scope: string
  maxActions: number
  windowDays: number
  resourceAction: string
}

const policies = ref<PolicyResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const submitState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const submitError = ref('')

const form = ref<PolicyRequest>({
  name: '',
  scope: 'USER',
  maxActions: 1,
  windowDays: 30,
  resourceAction: 'CLAIM',
})

async function fetchPolicies() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<PolicyResponse[]>('/resource-policies')
    policies.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load policies.'
    loadState.value = 'error'
  }
}

async function createPolicy() {
  submitState.value = 'loading'
  submitError.value = ''
  try {
    await apiClient.post('/resource-policies', form.value)
    submitState.value = 'success'
    form.value = { name: '', scope: 'USER', maxActions: 1, windowDays: 30, resourceAction: 'CLAIM' }
    await fetchPolicies()
  } catch (err: any) {
    submitError.value = err.response?.data?.message || 'Failed to create policy.'
    submitState.value = 'error'
  }
}

onMounted(fetchPolicies)
</script>

<template>
  <div class="policy-management">
    <h1>Policy Management</h1>

    <section class="create-section">
      <h2>Create Usage Policy</h2>
      <form @submit.prevent="createPolicy">
        <div class="form-row">
          <label for="policy-name">Name</label>
          <input id="policy-name" v-model="form.name" required />
        </div>
        <div class="form-row">
          <label for="policy-scope">Scope</label>
          <select id="policy-scope" v-model="form.scope">
            <option value="USER">USER</option>
            <option value="HOUSEHOLD">HOUSEHOLD</option>
          </select>
        </div>
        <div class="form-row">
          <label for="policy-max">Max Actions</label>
          <input id="policy-max" v-model.number="form.maxActions" type="number" min="1" required />
        </div>
        <div class="form-row">
          <label for="policy-window">Window (Days)</label>
          <input id="policy-window" v-model.number="form.windowDays" type="number" min="1" required />
        </div>
        <div class="form-row">
          <label for="policy-action">Resource Action</label>
          <select id="policy-action" v-model="form.resourceAction">
            <option value="CLAIM">CLAIM</option>
            <option value="DOWNLOAD">DOWNLOAD</option>
          </select>
        </div>
        <button type="submit" :disabled="submitState === 'loading'">
          {{ submitState === 'loading' ? 'Creating...' : 'Create Policy' }}
        </button>
        <p v-if="submitState === 'success'" class="success-message">Policy created.</p>
        <p v-if="submitState === 'error'" class="error-message">{{ submitError }}</p>
      </form>
    </section>

    <section class="list-section">
      <h2>Policies</h2>
      <p v-if="loadState === 'loading'">Loading policies...</p>
      <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>
      <div v-if="loadState === 'success'">
        <p v-if="policies.length === 0">No policies found.</p>
        <div v-for="p in policies" :key="p.id" class="policy-item">
          <strong>{{ p.name }}</strong>
          <span class="badge">{{ p.scope }}</span>
          <span class="badge">{{ p.resourceAction }}</span>
          <span>Max {{ p.maxActions }} per {{ p.windowDays }} days</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.policy-management { max-width: 800px; }
.create-section, .list-section { margin-bottom: 2rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input, .form-row select { width: 100%; padding: 0.4rem; box-sizing: border-box; }
.policy-item { border: 1px solid #ddd; border-radius: 4px; padding: 0.75rem; margin-bottom: 0.5rem; display: flex; gap: 0.75rem; align-items: center; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
