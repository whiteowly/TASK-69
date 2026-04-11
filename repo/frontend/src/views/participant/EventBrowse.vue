<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { EventResponse, RegistrationResponse } from '@/types'

const events = ref<EventResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

interface FormField { id: string; type: string; label: string; required: boolean }

const registrationStates = ref<Map<number, { state: 'idle' | 'loading' | 'success' | 'error'; registration?: RegistrationResponse; error: string }>>(new Map())
const cancelStates = ref<Map<number, { state: 'idle' | 'loading' | 'success' | 'error'; error: string }>>(new Map())
const formValues = ref<Map<number, Record<string, string>>>(new Map())

function getRegState(eventId: number) {
  if (!registrationStates.value.has(eventId)) {
    registrationStates.value.set(eventId, { state: 'idle', error: '' })
  }
  return registrationStates.value.get(eventId)!
}

function getCancelState(eventId: number) {
  if (!cancelStates.value.has(eventId)) {
    cancelStates.value.set(eventId, { state: 'idle', error: '' })
  }
  return cancelStates.value.get(eventId)!
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

function getFormSchema(evt: EventResponse): FormField[] {
  try {
    if ((evt as any).registrationFormSchema) {
      const parsed = JSON.parse((evt as any).registrationFormSchema)
      return Array.isArray(parsed) ? parsed : []
    }
  } catch { /* ignore */ }
  return []
}

function getFormValues(eventId: number): Record<string, string> {
  if (!formValues.value.has(eventId)) {
    formValues.value.set(eventId, {})
  }
  return formValues.value.get(eventId)!
}

async function register(eventId: number) {
  const s = getRegState(eventId)
  s.state = 'loading'
  s.error = ''
  try {
    const responses = JSON.stringify(getFormValues(eventId))
    const response = await apiClient.post<RegistrationResponse>(`/events/${eventId}/registrations`, { formResponses: responses })
    s.registration = response.data
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Registration failed.'
    s.state = 'error'
  }
}

async function cancelRegistration(eventId: number, registrationId: number) {
  const s = getCancelState(eventId)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post(`/registrations/${registrationId}/cancel`)
    s.state = 'success'
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Cancellation failed.'
    s.state = 'error'
  }
}

onMounted(fetchEvents)
</script>

<template>
  <div class="event-browse">
    <h1>Browse Events</h1>

    <p v-if="loadState === 'loading'">Loading events...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="events.length === 0">No events available.</p>
      <div v-for="evt in events" :key="evt.id" class="event-item">
        <div class="event-header">
          <strong>{{ evt.title }}</strong>
          <span class="badge">{{ evt.mode }}</span>
          <span class="badge">{{ evt.status }}</span>
        </div>
        <p>{{ evt.startAt }} - {{ evt.endAt }}</p>
        <p v-if="evt.location">Location: {{ evt.location }}</p>
        <p>Spots: {{ evt.approvedCount ?? 0 }} / {{ evt.capacity }}</p>

        <div v-if="getRegState(evt.id).state === 'idle' || getRegState(evt.id).state === 'error'">
          <div v-if="getFormSchema(evt).length > 0" class="custom-form">
            <div v-for="field in getFormSchema(evt)" :key="field.id" class="form-row">
              <label :for="'field-' + evt.id + '-' + field.id">
                {{ field.label }} {{ field.required ? '*' : '' }}
              </label>
              <input
                :id="'field-' + evt.id + '-' + field.id"
                :type="field.type || 'text'"
                v-model="getFormValues(evt.id)[field.id]"
                :required="field.required"
              />
            </div>
          </div>
          <button :disabled="getRegState(evt.id).state === 'loading'" @click="register(evt.id)">Register</button>
          <p v-if="getRegState(evt.id).state === 'error'" class="error-message">{{ getRegState(evt.id).error }}</p>
        </div>

        <div v-if="getRegState(evt.id).state === 'success' && getCancelState(evt.id).state !== 'success'">
          <p class="success-message">
            Status: {{ getRegState(evt.id).registration!.status }}
          </p>
          <button
            :disabled="getCancelState(evt.id).state === 'loading'"
            @click="cancelRegistration(evt.id, getRegState(evt.id).registration!.id)"
          >
            {{ getCancelState(evt.id).state === 'loading' ? 'Cancelling...' : 'Cancel Registration' }}
          </button>
          <p v-if="getCancelState(evt.id).state === 'error'" class="error-message">{{ getCancelState(evt.id).error }}</p>
        </div>

        <p v-if="getCancelState(evt.id).state === 'success'" class="success-message">Registration cancelled.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.event-browse { max-width: 800px; }
.event-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.event-header { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.custom-form { margin-bottom: 0.5rem; }
.form-row { margin-bottom: 0.4rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; }
.form-row input { width: 100%; padding: 0.3rem; box-sizing: border-box; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
