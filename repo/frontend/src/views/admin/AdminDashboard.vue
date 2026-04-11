<script setup lang="ts">
</script>

<template>
  <div class="admin-dashboard">
    <h2>Admin Dashboard</h2>
    <div class="admin-links">
      <router-link to="/workspace/admin/blacklist" class="admin-card">
        <h3>Blacklist Management</h3>
        <p>Blacklist accounts for policy violations.</p>
      </router-link>
      <router-link to="/workspace/admin/appeals" class="admin-card">
        <h3>Appeal Review</h3>
        <p>Review and decide on pending appeals.</p>
      </router-link>
      <router-link to="/workspace/admin/password-resets" class="admin-card">
        <h3>Password Reset</h3>
        <p>Reset passwords for verified accounts.</p>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.admin-dashboard {
  padding: 1rem;
}

.admin-links {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 1rem;
}

.admin-card {
  display: block;
  padding: 1.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  text-decoration: none;
  color: #333;
  width: 220px;
}

.admin-card:hover {
  background: #f5f5f5;
}

.admin-card h3 {
  margin: 0 0 0.5rem;
}

.admin-card p {
  margin: 0;
  font-size: 0.9rem;
  color: #666;
}
</style>
