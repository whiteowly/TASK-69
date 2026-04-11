<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const ready = ref(false)

onMounted(async () => {
  try {
    await auth.fetchMe()
  } catch {
    // Not authenticated — router guard will redirect to /login
  } finally {
    ready.value = true
  }
})
</script>

<template>
  <router-view v-if="ready" />
  <div v-else class="app-loading">Loading...</div>
</template>

<style>
.app-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  font-size: 1.2rem;
  color: #666;
}
</style>
