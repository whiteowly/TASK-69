<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { EventRequest, EventResponse } from '@/types'

const events = ref<EventResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const submitState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const submitError = ref('')

interface FormField {
  id: string
  type: string
  label: string
  required: boolean
}

const form = ref({
  organizationId: '',
  title: '',
  mode: 'ON_SITE',
  location: '',
  startAt: '',
  endAt: '',
  capacity: 50,
  waitlistEnabled: false,
  manualReviewRequired: false,
  status: 'PUBLISHED',
  registrationFormSchema: '' as string,
})

const customFields = ref<FormField[]>([])
const newField = ref<FormField>({ id: '', type: 'text', label: '', required: false })

function addField() {
  if (!newField.value.id || !newField.value.label) return
  customFields.value.push({ ...newField.value })
  newField.value = { id: '', type: 'text', label: '', required: false }
  form.value.registrationFormSchema = JSON.stringify(customFields.value)
}

function removeField(index: number) {
  customFields.value.splice(index, 1)
  form.value.registrationFormSchema = customFields.value.length > 0
    ? JSON.stringify(customFields.value) : ''
}

async function fetchEvents() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<EventResponse[]>('/events')
    events.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load events.'
    loadState.value = 'error'
  }
}

async function createEvent() {
  submitState.value = 'loading'
  submitError.value = ''
  try {
    await apiClient.post('/events', form.value)
    submitState.value = 'success'
    form.value = { organizationId: '', title: '', mode: 'ON_SITE', location: '', startAt: '', endAt: '', capacity: 50, waitlistEnabled: false, manualReviewRequired: false, status: 'PUBLISHED', registrationFormSchema: '' }
    customFields.value = []
    await fetchEvents()
  } catch (err: any) {
    submitError.value = err.response?.data?.message || 'Failed to create event.'
    submitState.value = 'error'
  }
}

onMounted(fetchEvents)
</script>

<template>
  <div class="event-management">
    <h1>Event Management</h1>

    <section class="create-section">
      <h2>Create Event</h2>
      <form @submit.prevent="createEvent">
        <div class="form-row">
          <label for="event-org">Organization ID</label>
          <input id="event-org" v-model="form.organizationId" required placeholder="e.g., org-1" />
        </div>
        <div class="form-row">
          <label for="event-title">Title</label>
          <input id="event-title" v-model="form.title" required />
        </div>
        <div class="form-row">
          <label for="event-mode">Mode</label>
          <select id="event-mode" v-model="form.mode">
            <option value="ON_SITE">ON_SITE</option>
            <option value="ONLINE">ONLINE</option>
          </select>
        </div>
        <div class="form-row">
          <label for="event-location">Location</label>
          <input id="event-location" v-model="form.location" />
        </div>
        <div class="form-row">
          <label for="event-start">Start At</label>
          <input id="event-start" v-model="form.startAt" type="datetime-local" required />
        </div>
        <div class="form-row">
          <label for="event-end">End At</label>
          <input id="event-end" v-model="form.endAt" type="datetime-local" required />
        </div>
        <div class="form-row">
          <label for="event-capacity">Capacity</label>
          <input id="event-capacity" v-model.number="form.capacity" type="number" min="1" />
        </div>
        <div class="form-row">
          <label>
            <input v-model="form.waitlistEnabled" type="checkbox" />
            Enable Waitlist
          </label>
        </div>
        <div class="form-row">
          <label>
            <input v-model="form.manualReviewRequired" type="checkbox" />
            Manual Review Required
          </label>
        </div>
        <div class="form-row">
          <label>Custom Registration Fields</label>
          <div v-for="(f, idx) in customFields" :key="f.id" class="custom-field-row">
            <span>{{ f.label }} ({{ f.type }}) {{ f.required ? '*' : '' }}</span>
            <button type="button" @click="removeField(idx)">Remove</button>
          </div>
          <div class="add-field-row">
            <input v-model="newField.id" placeholder="Field ID" />
            <input v-model="newField.label" placeholder="Label" />
            <select v-model="newField.type">
              <option value="text">Text</option>
              <option value="number">Number</option>
              <option value="email">Email</option>
            </select>
            <label><input type="checkbox" v-model="newField.required" /> Required</label>
            <button type="button" @click="addField">Add Field</button>
          </div>
        </div>
        <button type="submit" :disabled="submitState === 'loading'">
          {{ submitState === 'loading' ? 'Creating...' : 'Create Event' }}
        </button>
        <p v-if="submitState === 'success'" class="success-message">Event created.</p>
        <p v-if="submitState === 'error'" class="error-message">{{ submitError }}</p>
      </form>
    </section>

    <section class="list-section">
      <h2>Events</h2>
      <p v-if="loadState === 'loading'">Loading events...</p>
      <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>
      <div v-if="loadState === 'success'">
        <p v-if="events.length === 0">No events found.</p>
        <div v-for="evt in events" :key="evt.id" class="event-item">
          <div class="event-header">
            <strong>{{ evt.title }}</strong>
            <span class="badge">{{ evt.mode }}</span>
            <span class="badge">{{ evt.status }}</span>
          </div>
          <p>{{ evt.startAt }} - {{ evt.endAt }}</p>
          <p v-if="evt.location">Location: {{ evt.location }}</p>
          <p>Approved: {{ evt.approvedCount ?? 0 }} / {{ evt.capacity }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.event-management { max-width: 800px; }
.create-section, .list-section { margin-bottom: 2rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input[type="text"], .form-row input[type="datetime-local"], .form-row input[type="number"], .form-row select { width: 100%; padding: 0.4rem; box-sizing: border-box; }
.event-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.event-header { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.custom-field-row { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.add-field-row { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; margin-top: 0.25rem; }
.add-field-row input, .add-field-row select { padding: 0.3rem; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
