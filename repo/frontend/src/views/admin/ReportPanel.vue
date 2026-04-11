<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { ReportExecutionResponse } from '@/types'

interface MetricDefinition {
  id: number
  name: string
  query: string
}

interface ReportTemplate {
  id: number
  name: string
  metricIds: number[]
}

const metrics = ref<MetricDefinition[]>([])
const templates = ref<ReportTemplate[]>([])
const executions = ref<ReportExecutionResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

const metricForm = ref({ name: '', query: '' })
const metricSubmitState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const metricSubmitError = ref('')

const templateForm = ref({ name: '', metricIds: '' })
const templateSubmitState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const templateSubmitError = ref('')

const executeForm = ref({ templateId: 0 })
const executeState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const executeError = ref('')
const lastExecution = ref<ReportExecutionResponse | null>(null)

async function fetchAll() {
  loadState.value = 'loading'
  try {
    const [mRes, tRes, eRes] = await Promise.all([
      apiClient.get<MetricDefinition[]>('/reports/metric-definitions'),
      apiClient.get<ReportTemplate[]>('/reports/templates'),
      apiClient.get<ReportExecutionResponse[]>('/reports/executions'),
    ])
    metrics.value = mRes.data
    templates.value = tRes.data
    executions.value = eRes.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load reports data.'
    loadState.value = 'error'
  }
}

async function createMetric() {
  metricSubmitState.value = 'loading'
  metricSubmitError.value = ''
  try {
    await apiClient.post('/reports/metric-definitions', {
      name: metricForm.value.name,
      queryTemplate: metricForm.value.query,
      domain: 'general',
    })
    metricSubmitState.value = 'success'
    metricForm.value = { name: '', query: '' }
    await fetchAll()
  } catch (err: any) {
    metricSubmitError.value = err.response?.data?.message || 'Failed to create metric.'
    metricSubmitState.value = 'error'
  }
}

async function createTemplate() {
  templateSubmitState.value = 'loading'
  templateSubmitError.value = ''
  try {
    await apiClient.post('/reports/templates', {
      name: templateForm.value.name,
      metricIds: templateForm.value.metricIds,
    })
    templateSubmitState.value = 'success'
    templateForm.value = { name: '', metricIds: '' }
    await fetchAll()
  } catch (err: any) {
    templateSubmitError.value = err.response?.data?.message || 'Failed to create template.'
    templateSubmitState.value = 'error'
  }
}

async function executeReport() {
  executeState.value = 'loading'
  executeError.value = ''
  try {
    const response = await apiClient.post<ReportExecutionResponse>(`/reports/templates/${executeForm.value.templateId}/execute`, {})
    lastExecution.value = response.data
    executeState.value = 'success'
    await fetchAll()
  } catch (err: any) {
    executeError.value = err.response?.data?.message || 'Execution failed.'
    executeState.value = 'error'
  }
}

onMounted(fetchAll)
</script>

<template>
  <div class="report-panel">
    <h1>Report Panel</h1>

    <p v-if="loadState === 'loading'">Loading...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <template v-if="loadState === 'success'">
      <section class="section">
        <h2>Create Metric Definition</h2>
        <form @submit.prevent="createMetric">
          <div class="form-row">
            <label>Name</label>
            <input v-model="metricForm.name" required />
          </div>
          <div class="form-row">
            <label>Query</label>
            <textarea v-model="metricForm.query" rows="2" required />
          </div>
          <button type="submit" :disabled="metricSubmitState === 'loading'">
            {{ metricSubmitState === 'loading' ? 'Creating...' : 'Create Metric' }}
          </button>
          <p v-if="metricSubmitState === 'success'" class="success-message">Metric created.</p>
          <p v-if="metricSubmitState === 'error'" class="error-message">{{ metricSubmitError }}</p>
        </form>
      </section>

      <section class="section">
        <h2>Create Report Template</h2>
        <form @submit.prevent="createTemplate">
          <div class="form-row">
            <label>Name</label>
            <input v-model="templateForm.name" required />
          </div>
          <div class="form-row">
            <label>Metric IDs (comma-separated)</label>
            <input v-model="templateForm.metricIds" placeholder="1, 2, 3" required />
          </div>
          <button type="submit" :disabled="templateSubmitState === 'loading'">
            {{ templateSubmitState === 'loading' ? 'Creating...' : 'Create Template' }}
          </button>
          <p v-if="templateSubmitState === 'success'" class="success-message">Template created.</p>
          <p v-if="templateSubmitState === 'error'" class="error-message">{{ templateSubmitError }}</p>
        </form>
      </section>

      <section class="section">
        <h2>Execute Report</h2>
        <div class="form-row">
          <label>Template</label>
          <select v-model.number="executeForm.templateId">
            <option :value="0" disabled>Select template</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </div>
        <button :disabled="executeState === 'loading' || !executeForm.templateId" @click="executeReport">
          {{ executeState === 'loading' ? 'Executing...' : 'Execute' }}
        </button>
        <p v-if="executeState === 'error'" class="error-message">{{ executeError }}</p>
        <div v-if="lastExecution" class="execution-result">
          <p class="success-message">Execution #{{ lastExecution.id }} - {{ lastExecution.status }}</p>
          <a v-if="lastExecution.exportFilePath" :href="`/api/v1/reports/executions/${lastExecution.id}/download`" target="_blank">Download Export</a>
        </div>
      </section>

      <section class="section">
        <h2>Past Executions</h2>
        <div v-for="ex in executions" :key="ex.id" class="execution-item">
          <span>Execution #{{ ex.id }}</span>
          <span class="badge">{{ ex.status }}</span>
          <a v-if="ex.exportFilePath" :href="`/api/v1/reports/executions/${ex.id}/download`" target="_blank">Download</a>
        </div>
        <p v-if="executions.length === 0">No executions yet.</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.report-panel { max-width: 800px; }
.section { margin-bottom: 2rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input, .form-row select, .form-row textarea { width: 100%; padding: 0.4rem; box-sizing: border-box; }
.execution-item { border: 1px solid #ddd; border-radius: 4px; padding: 0.75rem; margin-bottom: 0.5rem; display: flex; gap: 0.75rem; align-items: center; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
