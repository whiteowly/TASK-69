<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { RewardOrderResponse } from '@/types'

interface OrderItem extends RewardOrderResponse {
  overdue?: boolean
}

const orders = ref<OrderItem[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

const transitionStates = ref<Map<number, { state: 'idle' | 'loading' | 'success' | 'error'; error: string }>>(new Map())
const inputValues = ref<Map<number, { trackingNumber: string; voucherCode: string; exceptionReason: string; exceptionDescription: string }>>(new Map())

const STATUS_TRANSITIONS: Record<string, string[]> = {
  ORDERED: ['ALLOCATED'],
  ALLOCATED: ['PACKED', 'VOUCHER_ISSUED'],
  PACKED: ['SHIPPED'],
  SHIPPED: ['DELIVERED'],
  VOUCHER_ISSUED: ['REDEEMED'],
}

function getTransitionState(id: number) {
  if (!transitionStates.value.has(id)) {
    transitionStates.value.set(id, { state: 'idle', error: '' })
  }
  return transitionStates.value.get(id)!
}

function getInputs(id: number) {
  if (!inputValues.value.has(id)) {
    inputValues.value.set(id, { trackingNumber: '', voucherCode: '', exceptionReason: '', exceptionDescription: '' })
  }
  return inputValues.value.get(id)!
}

function nextStates(status: string): string[] {
  return STATUS_TRANSITIONS[status] || []
}

function isOverdue(order: OrderItem): boolean {
  if (!['PACKED', 'SHIPPED'].includes(order.status)) return false
  const dateStr = order.statusChangedAt ?? order.updatedAt
  if (!dateStr) return false
  const statusDate = new Date(dateStr)
  const now = new Date()
  const days = (now.getTime() - statusDate.getTime()) / (1000 * 60 * 60 * 24)
  return days > 7
}

async function fetchOrders() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<OrderItem[]>('/reward-orders')
    orders.value = response.data.map(o => ({ ...o, overdue: isOverdue(o) }))
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load orders.'
    loadState.value = 'error'
  }
}

async function transitionOrder(orderId: number, newStatus: string) {
  const s = getTransitionState(orderId)
  const inputs = getInputs(orderId)
  s.state = 'loading'
  s.error = ''
  try {
    // Transition the order state
    await apiClient.post(`/reward-orders/${orderId}/transition`, { toState: newStatus, note: '' })

    // Set tracking number if provided during SHIPPED transition
    if (inputs.trackingNumber && newStatus === 'SHIPPED') {
      await apiClient.post(`/reward-orders/${orderId}/tracking`, { trackingNumber: inputs.trackingNumber })
    }
    // Issue voucher code if provided during VOUCHER_ISSUED transition
    if (inputs.voucherCode && newStatus === 'VOUCHER_ISSUED') {
      await apiClient.post(`/reward-orders/${orderId}/voucher`, { voucherCode: inputs.voucherCode })
    }

    s.state = 'success'
    await fetchOrders()
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Transition failed.'
    s.state = 'error'
  }
}

async function createException(orderId: number) {
  const s = getTransitionState(orderId)
  const inputs = getInputs(orderId)
  s.state = 'loading'
  s.error = ''
  try {
    await apiClient.post('/fulfillment-exceptions', {
      orderId,
      reasonCode: inputs.exceptionReason,
      description: inputs.exceptionDescription,
    })
    s.state = 'success'
    inputs.exceptionReason = ''
    inputs.exceptionDescription = ''
  } catch (err: any) {
    s.error = err.response?.data?.message || 'Exception creation failed.'
    s.state = 'error'
  }
}

onMounted(fetchOrders)
</script>

<template>
  <div class="fulfillment-panel">
    <h1>Fulfillment Panel</h1>

    <p v-if="loadState === 'loading'">Loading orders...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="orders.length === 0">No orders found.</p>
      <div v-for="order in orders" :key="order.id" class="order-item" :class="{ overdue: order.overdue }">
        <div class="order-header">
          <strong>Order #{{ order.id }}</strong>
          <span class="badge">{{ order.status }}</span>
          <span v-if="order.overdue" class="badge overdue-badge">OVERDUE</span>
        </div>
        <p>Reward #{{ order.rewardId }} | Qty: {{ order.quantity || 1 }} | Created: {{ order.createdAt }}</p>
        <p v-if="order.trackingNumber">Tracking: {{ order.trackingNumber }}</p>
        <p v-if="order.voucherCode">Voucher: {{ order.voucherCode }}</p>

        <div v-if="nextStates(order.status).length > 0" class="transition-actions">
          <div class="form-row">
            <input v-model="getInputs(order.id).trackingNumber" placeholder="Tracking number (for SHIPPED)" />
          </div>
          <div class="form-row">
            <input v-model="getInputs(order.id).voucherCode" placeholder="Voucher code (for VOUCHER_ISSUED)" />
          </div>
          <div class="button-group">
            <button
              v-for="ns in nextStates(order.status)"
              :key="ns"
              :disabled="getTransitionState(order.id).state === 'loading'"
              @click="transitionOrder(order.id, ns)"
            >
              {{ ns }}
            </button>
          </div>
        </div>

        <div class="exception-section">
          <input v-model="getInputs(order.id).exceptionReason" placeholder="Exception reason code" />
          <input v-model="getInputs(order.id).exceptionDescription" placeholder="Description (optional)" />
          <button
            :disabled="getTransitionState(order.id).state === 'loading' || !getInputs(order.id).exceptionReason"
            @click="createException(order.id)"
          >
            Create Exception
          </button>
        </div>

        <p v-if="getTransitionState(order.id).state === 'error'" class="error-message">{{ getTransitionState(order.id).error }}</p>
        <p v-if="getTransitionState(order.id).state === 'success'" class="success-message">Updated.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fulfillment-panel { max-width: 900px; }
.order-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.order-item.overdue { border-color: #dc3545; }
.order-header { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
.overdue-badge { background: #f8d7da; color: #721c24; }
.transition-actions { border-top: 1px solid #eee; padding-top: 0.5rem; margin-top: 0.5rem; }
.exception-section { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
.exception-section input { flex: 1; padding: 0.4rem; }
.form-row { margin-bottom: 0.4rem; }
.form-row input { width: 100%; padding: 0.4rem; box-sizing: border-box; }
.button-group { display: flex; gap: 0.5rem; margin-top: 0.25rem; }
button { padding: 0.5rem 1rem; cursor: pointer; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
