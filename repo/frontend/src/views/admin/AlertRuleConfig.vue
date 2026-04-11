<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

interface AlertRule {
  id: number
  alertType: string
  scopeType?: string
  scopeId?: string
  severity: string
  thresholdOperator: string
  thresholdValue: number
  thresholdUnit?: string
  durationSeconds: number
  cooldownSeconds: number
  updatedAt: string
}

interface EditState {
  severity: string
  thresholdOperator: string
  thresholdValue: number
  cooldownSeconds: number
  state: 'idle' | 'loading' | 'success' | 'error'
  error: string
}

const defaults = ref<AlertRule[]>([])
const overrides = ref<AlertRule[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')
const editStates = ref<Map<string, EditState>>(new Map())

function ruleKey(rule: AlertRule): string {
  return rule.scopeType ? `${rule.scopeType}/${rule.scopeId}/${rule.alertType}` : `default/${rule.alertType}`
}

function getEditState(rule: AlertRule): EditState {
  const key = ruleKey(rule)
  if (!editStates.value.has(key)) {
    editStates.value.set(key, {
      severity: rule.severity,
      thresholdOperator: rule.thresholdOperator,
      thresholdValue: rule.thresholdValue,
      cooldownSeconds: rule.cooldownSeconds,
      state: 'idle',
      error: '',
    })
  }
  return editStates.value.get(key)!
}

async function fetchRules() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<{ defaults: AlertRule[]; overrides: AlertRule[] }>('/alerts/rules')
    defaults.value = response.data.defaults || []
    overrides.value = response.data.overrides || []
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load alert rules.'
    loadState.value = 'error'
  }
}

async function updateRule(rule: AlertRule) {
  const s = getEditState(rule)
  s.state = 'loading'
  s.error = ''
  try {
    const payload = {
      severity: s.severity,
      thresholdOperator: s.thresholdOperator,
      thresholdValue: s.thresholdValue,
      cooldownSeconds: s.cooldownSeconds,
    }
    if (rule.scopeType) {
      await apiClient.put(`/alerts/rules/overrides/${rule.scopeType}/${rule.scopeId}/${rule.alertType}`, payload)
    } else {
      await apiClient.put(`/alerts/rules/defaults/${rule.alertType}`, payload)
    }
    s.state = 'success'
    await fetchRules()
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Update failed.'
    s.state = 'error'
  }
}

onMounted(fetchRules)
</script>

<template>
  <div class="alert-rule-config">
    <h1>Alert Rule Configuration</h1>

    <p v-if="loadState === 'loading'">Loading alert rules...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <section v-if="defaults.length > 0">
        <h2>Default Rules</h2>
        <div v-for="rule in defaults" :key="ruleKey(rule)" class="rule-item">
          <div class="rule-header">
            <strong>{{ rule.alertType }}</strong>
            <span class="badge" :class="'severity-' + rule.severity.toLowerCase()">{{ rule.severity }}</span>
            <span>{{ rule.thresholdOperator }} {{ rule.thresholdValue }}</span>
          </div>
          <div class="edit-form">
            <div class="form-row">
              <label>Severity</label>
              <input v-model="getEditState(rule).severity" type="text" />
            </div>
            <div class="form-row">
              <label>Threshold Value</label>
              <input v-model.number="getEditState(rule).thresholdValue" type="number" />
            </div>
            <div class="form-row">
              <label>Cooldown (seconds)</label>
              <input v-model.number="getEditState(rule).cooldownSeconds" type="number" min="0" />
            </div>
            <button :disabled="getEditState(rule).state === 'loading'" @click="updateRule(rule)">
              {{ getEditState(rule).state === 'loading' ? 'Saving...' : 'Save' }}
            </button>
            <p v-if="getEditState(rule).state === 'error'" class="error-message">{{ getEditState(rule).error }}</p>
            <p v-if="getEditState(rule).state === 'success'" class="success-message">Saved.</p>
          </div>
        </div>
      </section>

      <section v-if="overrides.length > 0">
        <h2>Overrides</h2>
        <div v-for="rule in overrides" :key="ruleKey(rule)" class="rule-item">
          <div class="rule-header">
            <strong>{{ rule.alertType }}</strong> ({{ rule.scopeType }}/{{ rule.scopeId }})
            <span class="badge" :class="'severity-' + rule.severity.toLowerCase()">{{ rule.severity }}</span>
          </div>
          <div class="edit-form">
            <div class="form-row">
              <label>Threshold Value</label>
              <input v-model.number="getEditState(rule).thresholdValue" type="number" />
            </div>
            <button :disabled="getEditState(rule).state === 'loading'" @click="updateRule(rule)">
              {{ getEditState(rule).state === 'loading' ? 'Saving...' : 'Save' }}
            </button>
            <p v-if="getEditState(rule).state === 'error'" class="error-message">{{ getEditState(rule).error }}</p>
            <p v-if="getEditState(rule).state === 'success'" class="success-message">Saved.</p>
          </div>
        </div>
      </section>

      <p v-if="defaults.length === 0 && overrides.length === 0">No alert rules configured.</p>
    </div>
  </div>
</template>

<style scoped>
.alert-rule-config { max-width: 800px; }
.rule-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.rule-header { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 0.5rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; border-radius: 3px; }
.severity-critical { background: #f8d7da; color: #721c24; }
.severity-high { background: #fff3cd; color: #856404; }
.severity-medium { background: #d1ecf1; color: #0c5460; }
.severity-low { background: #e2e3e5; color: #383d41; }
.edit-form { border-top: 1px solid #eee; padding-top: 0.5rem; }
.form-row { margin-bottom: 0.4rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input { width: 100%; padding: 0.4rem; box-sizing: border-box; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
