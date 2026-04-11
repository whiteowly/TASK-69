<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { ResourceResponse } from '@/types'

const resources = ref<ResourceResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const submitState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const submitError = ref('')

const form = ref({
  type: 'CLAIMABLE_ITEM' as string,
  title: '',
  description: '',
  inventoryCount: 0,
  fileVersion: '',
  organizationId: '',
})
const selectedFile = ref<File | null>(null)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
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

async function createResource() {
  submitState.value = 'loading'
  submitError.value = ''
  try {
    if (form.value.type === 'DOWNLOADABLE_FILE') {
      if (!selectedFile.value) {
        submitError.value = 'Please select a file to upload.'
        submitState.value = 'error'
        return
      }
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      formData.append('title', form.value.title)
      if (form.value.description) formData.append('description', form.value.description)
      if (form.value.fileVersion) formData.append('fileVersion', form.value.fileVersion)
      formData.append('organizationId', form.value.organizationId)
      await apiClient.post('/resources/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    } else {
      await apiClient.post('/resources', {
        type: form.value.type,
        title: form.value.title,
        description: form.value.description,
        inventoryCount: form.value.inventoryCount,
        organizationId: form.value.organizationId,
      })
    }
    submitState.value = 'success'
    form.value = { type: 'CLAIMABLE_ITEM', title: '', description: '', inventoryCount: 0, fileVersion: '', organizationId: form.value.organizationId }
    selectedFile.value = null
    await fetchResources()
  } catch (err: any) {
    submitError.value = err.response?.data?.message || 'Failed to create resource.'
    submitState.value = 'error'
  }
}

onMounted(fetchResources)
</script>

<template>
  <div class="resource-management">
    <h1>Resource Management</h1>

    <section class="create-section">
      <h2>Create Resource</h2>
      <form @submit.prevent="createResource">
        <div class="form-row">
          <label for="res-org">Organization ID</label>
          <input id="res-org" v-model="form.organizationId" required />
        </div>
        <div class="form-row">
          <label for="res-type">Type</label>
          <select id="res-type" v-model="form.type">
            <option value="CLAIMABLE_ITEM">CLAIMABLE_ITEM</option>
            <option value="DOWNLOADABLE_FILE">DOWNLOADABLE_FILE</option>
          </select>
        </div>
        <div class="form-row">
          <label for="res-title">Title</label>
          <input id="res-title" v-model="form.title" required />
        </div>
        <div class="form-row">
          <label for="res-desc">Description</label>
          <textarea id="res-desc" v-model="form.description" rows="2" />
        </div>
        <div class="form-row" v-if="form.type === 'CLAIMABLE_ITEM'">
          <label for="res-inventory">Inventory Count</label>
          <input id="res-inventory" v-model.number="form.inventoryCount" type="number" min="0" />
        </div>
        <div class="form-row" v-if="form.type === 'DOWNLOADABLE_FILE'">
          <label for="res-version">File Version</label>
          <input id="res-version" v-model="form.fileVersion" />
        </div>
        <div class="form-row" v-if="form.type === 'DOWNLOADABLE_FILE'">
          <label for="res-file">Upload File (PDF or JPEG, max 10 MB)</label>
          <input id="res-file" type="file" accept=".pdf,.jpg,.jpeg" @change="onFileChange" />
        </div>
        <button type="submit" :disabled="submitState === 'loading'">
          {{ submitState === 'loading' ? 'Creating...' : 'Create Resource' }}
        </button>
        <p v-if="submitState === 'success'" class="success-message">Resource created.</p>
        <p v-if="submitState === 'error'" class="error-message">{{ submitError }}</p>
      </form>
    </section>

    <section class="list-section">
      <h2>Resources</h2>
      <p v-if="loadState === 'loading'">Loading resources...</p>
      <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>
      <div v-if="loadState === 'success'">
        <p v-if="resources.length === 0">No resources found.</p>
        <div v-for="res in resources" :key="res.id" class="resource-item">
          <div class="resource-header">
            <strong>{{ res.title }}</strong>
            <span class="badge">{{ res.type }}</span>
            <span class="badge">{{ res.status }}</span>
          </div>
          <p v-if="res.description">{{ res.description }}</p>
          <p v-if="res.inventoryCount != null">Inventory: {{ res.inventoryCount }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resource-management { max-width: 800px; }
.create-section, .list-section { margin-bottom: 2rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input, .form-row select, .form-row textarea { width: 100%; padding: 0.4rem; box-sizing: border-box; }
.resource-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.resource-header { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
