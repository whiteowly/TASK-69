<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="workspace-shell">
    <header class="workspace-header">
      <div class="header-left">
        <h1>CROH</h1>
      </div>
      <div class="header-right">
        <span class="user-info">{{ auth.username }} ({{ auth.activeRole }})</span>
        <select class="role-switcher" disabled>
          <option>{{ auth.activeRole }}</option>
        </select>
        <button class="logout-btn" @click="handleLogout">Logout</button>
      </div>
    </header>
    <div class="workspace-body">
      <nav class="workspace-sidebar">
        <ul v-if="auth.activeRole === 'ADMIN'">
          <li><router-link to="/workspace/admin">Dashboard</router-link></li>
          <li><router-link to="/workspace/admin/verification">Verification Queue</router-link></li>
          <li><router-link to="/workspace/admin/roles">Role Approvals</router-link></li>
          <li><router-link to="/workspace/admin/blacklist">Blacklist</router-link></li>
          <li><router-link to="/workspace/admin/appeals">Appeals</router-link></li>
          <li><router-link to="/workspace/admin/password-resets">Password Resets</router-link></li>
          <li><router-link to="/workspace/admin/registrations">Registration Review</router-link></li>
          <li><router-link to="/workspace/admin/policies">Policies</router-link></li>
          <li><router-link to="/workspace/admin/fulfillment">Fulfillment</router-link></li>
          <li><router-link to="/workspace/admin/alerts">Alerts</router-link></li>
          <li><router-link to="/workspace/admin/work-orders">Work Orders</router-link></li>
          <li><router-link to="/workspace/admin/analytics">Analytics</router-link></li>
          <li><router-link to="/workspace/admin/reports">Reports</router-link></li>
          <li><router-link to="/workspace/admin/audit-logs">Audit Logs</router-link></li>
        </ul>
        <ul v-else-if="auth.activeRole === 'PARTICIPANT'">
          <li><router-link to="/workspace/PARTICIPANT">Home</router-link></li>
          <li><router-link to="/workspace/PARTICIPANT/verification">Verification</router-link></li>
          <li><router-link to="/workspace/PARTICIPANT/roles">My Roles</router-link></li>
          <li><router-link to="/workspace/PARTICIPANT/events">Events</router-link></li>
          <li><router-link to="/workspace/PARTICIPANT/resources">Resources</router-link></li>
          <li><router-link to="/workspace/PARTICIPANT/rewards">Rewards</router-link></li>
        </ul>
        <ul v-else-if="auth.activeRole === 'ORG_OPERATOR'">
          <li><router-link to="/workspace/ORG_OPERATOR">Home</router-link></li>
          <li><router-link to="/workspace/ORG_OPERATOR/events">Events</router-link></li>
          <li><router-link to="/workspace/ORG_OPERATOR/resources">Resources</router-link></li>
        </ul>
        <ul v-else-if="auth.activeRole === 'VOLUNTEER'">
          <li><router-link to="/workspace/VOLUNTEER">Dashboard</router-link></li>
          <li><router-link to="/workspace/VOLUNTEER/verification">Verification Review</router-link></li>
          <li><router-link to="/workspace/VOLUNTEER/registrations">Registration Review</router-link></li>
        </ul>
        <ul v-else>
          <li><a href="#">Dashboard</a></li>
        </ul>
      </nav>
      <main class="workspace-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.workspace-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background: #1a1a2e;
  color: white;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-info {
  font-size: 0.9rem;
}

.role-switcher {
  padding: 0.25rem;
}

.logout-btn {
  padding: 0.25rem 0.75rem;
  cursor: pointer;
}

.workspace-body {
  display: flex;
  flex: 1;
}

.workspace-sidebar {
  width: 200px;
  background: #f0f0f0;
  padding: 1rem;
}

.workspace-sidebar ul {
  list-style: none;
  padding: 0;
}

.workspace-sidebar li {
  margin-bottom: 0.5rem;
}

.workspace-sidebar a {
  text-decoration: none;
  color: #333;
}

.workspace-content {
  flex: 1;
  padding: 1rem;
}
</style>
