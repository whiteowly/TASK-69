<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { RoleMembershipResponse } from '@/types'

const auth = useAuthStore()

// Role list state
const roles = ref<RoleMembershipResponse[]>([])
const rolesState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const rolesError = ref('')

// Request form state
const requestRole = ref('VOLUNTEER')
const scopeType = ref('')
const scopeId = ref('')
const requestState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const requestError = ref('')
const requestSuccess = ref('')

// Switch state
const switchError = ref('')

async function fetchRoles() {
  rolesState.value = 'loading'
  try {
    const response = await apiClient.get<RoleMembershipResponse[]>('/accounts/me/roles')
    roles.value = response.data
    rolesState.value = 'success'
  } catch (err: any) {
    rolesError.value = err.response?.data?.message || 'Failed to load roles.'
    rolesState.value = 'error'
  }
}

async function submitRoleRequest() {
  requestState.value = 'loading'
  requestError.value = ''
  requestSuccess.value = ''
  try {
    await apiClient.post('/accounts/me/role-requests', {
      role: requestRole.value,
      scopeType: scopeType.value || undefined,
      scopeId: scopeId.value || undefined,
    })
    requestState.value = 'success'
    requestSuccess.value = `Role request for ${requestRole.value} submitted.`
    await fetchRoles()
  } catch (err: any) {
    requestError.value = err.response?.data?.message || 'Request failed.'
    requestState.value = 'error'
  }
}

async function handleSwitchRole(role: string, roleScopeId?: string) {
  switchError.value = ''
  try {
    await auth.switchRole(role, roleScopeId)
  } catch (err: any) {
    switchError.value = err.response?.data?.message || 'Failed to switch role.'
  }
}

onMounted(fetchRoles)
</script>

<template>
  <div class="role-management">
    <h1>Role Management</h1>

    <!-- Current Roles -->
    <section class="section">
      <h2>My Roles</h2>
      <p v-if="rolesState === 'loading'">Loading roles...</p>
      <p v-if="rolesState === 'error'" class="error-message">{{ rolesError }}</p>
      <p v-if="switchError" class="error-message">{{ switchError }}</p>
      <div v-if="rolesState === 'success'">
        <p v-if="roles.length === 0">No roles found.</p>
        <ul class="role-list">
          <li v-for="r in roles" :key="r.id" class="role-item">
            <span class="role-type">{{ r.roleType }}</span>
            <span v-if="r.scopeId" class="role-scope">(scope: {{ r.scopeId }})</span>
            <span class="role-status" :class="r.status.toLowerCase()">{{ r.status }}</span>
            <button
              v-if="r.status === 'APPROVED'"
              class="switch-btn"
              @click="handleSwitchRole(r.roleType, r.scopeId)"
            >
              Switch to this role
            </button>
          </li>
        </ul>
      </div>
    </section>

    <!-- Request New Role -->
    <section class="section">
      <h2>Request New Role</h2>
      <form @submit.prevent="submitRoleRequest">
        <div class="form-group">
          <label for="requestRole">Role</label>
          <select id="requestRole" v-model="requestRole">
            <option value="VOLUNTEER">VOLUNTEER</option>
            <option value="ORG_OPERATOR">ORG_OPERATOR</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
        <div class="form-group">
          <label for="scopeType">Scope Type (optional)</label>
          <input id="scopeType" v-model="scopeType" type="text" placeholder="e.g., ORGANIZATION" />
        </div>
        <div class="form-group">
          <label for="scopeId">Scope ID (optional)</label>
          <input id="scopeId" v-model="scopeId" type="text" placeholder="e.g., org-123" />
        </div>
        <button type="submit" :disabled="requestState === 'loading'">
          {{ requestState === 'loading' ? 'Submitting...' : 'Request Role' }}
        </button>
      </form>
      <p v-if="requestState === 'error'" class="error-message">{{ requestError }}</p>
      <p v-if="requestState === 'success'" class="success-message">{{ requestSuccess }}</p>
    </section>
  </div>
</template>

<style scoped>
.role-management {
  max-width: 600px;
}

.section {
  margin-bottom: 2rem;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: bold;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.role-list {
  list-style: none;
  padding: 0;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #eee;
}

.role-type {
  font-weight: bold;
}

.role-status {
  font-size: 0.85rem;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
}

.role-status.approved {
  background: #d4edda;
  color: #155724;
}

.role-status.pending {
  background: #fff3cd;
  color: #856404;
}

.role-status.denied {
  background: #f8d7da;
  color: #721c24;
}

.switch-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.85rem;
  cursor: pointer;
}

button {
  padding: 0.5rem 1rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  color: red;
  margin-top: 0.5rem;
}

.success-message {
  color: green;
  margin-top: 0.5rem;
}
</style>
