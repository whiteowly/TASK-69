<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { ResourceResponse, ClaimResult } from '@/types'

const resources = ref<ResourceResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

const actionStates = ref<Map<number, { state: 'idle' | 'loading' | 'success' | 'error'; result?: ClaimResult; error: string }>>(new Map())

function getActionState(resId: number) {
  if (!actionStates.value.has(resId)) {
    actionStates.value.set(resId, { state: 'idle', error: '' })
  }
  return actionStates.value.get(resId)!
}

async function fetchResources() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<ResourceResponse[]>('/resources')
    resources.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load resources.'
    loadState.value = 'error'
  }
}

async function claimResource(resId: number) {
  const s = getActionState(resId)
  s.state = 'loading'
  s.error = ''
  try {
    const response = await apiClient.post<ClaimResult>(`/resources/${resId}/claim`)
    s.result = response.data
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Claim failed.'
    s.state = 'error'
  }
}

async function downloadResource(resId: number) {
  const s = getActionState(resId)
  s.state = 'loading'
  s.error = ''
  try {
    const response = await apiClient.post<ClaimResult>(`/resources/files/${resId}/download`, { fileVersion: 'latest' })
    s.result = response.data
    s.state = 'success'
    if (response.data.result === 'ALLOWED') {
      window.open(`/api/v1/resources/${resId}/file`, '_blank')
    }
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Download failed.'
    s.state = 'error'
  }
}

onMounted(fetchResources)
</script>

<template>
  <div class="resource-browse">
    <h1>Browse Resources</h1>

    <p v-if="loadState === 'loading'">Loading resources...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="resources.length === 0">No resources available.</p>
      <div v-for="res in resources" :key="res.id" class="resource-item">
        <div class="resource-header">
          <strong>{{ res.title }}</strong>
          <span class="badge">{{ res.type }}</span>
          <span class="badge">{{ res.status }}</span>
        </div>
        <p v-if="res.description">{{ res.description }}</p>
        <p v-if="res.inventoryCount != null">Inventory: {{ res.inventoryCount }}</p>

        <div v-if="getActionState(res.id).state !== 'success'">
          <button
            v-if="res.type === 'CLAIMABLE_ITEM'"
            :disabled="getActionState(res.id).state === 'loading'"
            @click="claimResource(res.id)"
          >
            {{ getActionState(res.id).state === 'loading' ? 'Claiming...' : 'Claim' }}
          </button>
          <button
            v-if="res.type === 'DOWNLOADABLE_FILE'"
            :disabled="getActionState(res.id).state === 'loading'"
            @click="downloadResource(res.id)"
          >
            {{ getActionState(res.id).state === 'loading' ? 'Downloading...' : 'Download' }}
          </button>
          <p v-if="getActionState(res.id).state === 'error'" class="error-message">{{ getActionState(res.id).error }}</p>
        </div>

        <div v-if="getActionState(res.id).state === 'success'">
          <p :class="getActionState(res.id).result?.result === 'ALLOWED' ? 'success-message' : 'error-message'">
            Result: {{ getActionState(res.id).result?.result }}
            <span v-if="getActionState(res.id).result?.reasonCode"> ({{ getActionState(res.id).result?.reasonCode }})</span>
          </p>
          <p v-if="getActionState(res.id).result?.printableNoticeId">
            <a :href="`/api/v1/notices/${getActionState(res.id).result!.printableNoticeId}/print`" target="_blank">View Printable Notice</a>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.resource-browse { max-width: 800px; }
.resource-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.resource-header { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
