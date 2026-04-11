<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

const pendingVerifications = ref(0)
const pendingRegistrations = ref(0)
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')

async function fetchCounts() {
  loadState.value = 'loading'
  try {
    const [verifRes, regRes] = await Promise.all([
      apiClient.get('/admin/verification/queue'),
      apiClient.get('/registrations/pending'),
    ])
    pendingVerifications.value = verifRes.data.length
    pendingRegistrations.value = regRes.data.length
    loadState.value = 'success'
  } catch {
    loadState.value = 'error'
  }
}

onMounted(fetchCounts)
</script>

<template>
  <div class="volunteer-dashboard">
    <h1>Volunteer Dashboard</h1>

    <p v-if="loadState === 'loading'">Loading...</p>
    <p v-if="loadState === 'error'" class="error-message">Failed to load dashboard data.</p>

    <div v-if="loadState === 'success'" class="summary-cards">
      <div class="card">
        <h3>Pending Verifications</h3>
        <span class="count">{{ pendingVerifications }}</span>
        <router-link to="/workspace/VOLUNTEER/verification">Review Queue</router-link>
      </div>
      <div class="card">
        <h3>Pending Registrations</h3>
        <span class="count">{{ pendingRegistrations }}</span>
        <router-link to="/workspace/VOLUNTEER/registrations">Review Registrations</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.volunteer-dashboard { max-width: 800px; }
.summary-cards { display: flex; gap: 1rem; flex-wrap: wrap; }
.card { border: 1px solid #ddd; border-radius: 6px; padding: 1.25rem; flex: 1; min-width: 200px; }
.card h3 { margin: 0 0 0.5rem; font-size: 1rem; }
.count { display: block; font-size: 2rem; font-weight: bold; margin-bottom: 0.5rem; }
.card a { color: #007bff; text-decoration: none; font-size: 0.9rem; }
.card a:hover { text-decoration: underline; }
.error-message { color: red; }
</style>
