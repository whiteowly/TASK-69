<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { RewardResponse, RewardOrderResponse, AddressRequest } from '@/types'

const rewards = ref<RewardResponse[]>([])
const loadState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const loadError = ref('')

interface OrderFormState {
  quantity: number
  fulfillmentType: string
  address: AddressRequest
  state: 'idle' | 'loading' | 'success' | 'error'
  order?: RewardOrderResponse
  error: string
}

const orderForms = ref<Map<number, OrderFormState>>(new Map())

function getOrderForm(rewardId: number): OrderFormState {
  if (!orderForms.value.has(rewardId)) {
    orderForms.value.set(rewardId, {
      quantity: 1,
      fulfillmentType: 'SHIP',
      address: { line1: '', line2: '', city: '', state: '', zip: '' },
      state: 'idle',
      error: '',
    })
  }
  return orderForms.value.get(rewardId)!
}

async function fetchRewards() {
  loadState.value = 'loading'
  try {
    const response = await apiClient.get<RewardResponse[]>('/rewards')
    rewards.value = response.data
    loadState.value = 'success'
  } catch (err: any) {
    loadError.value = err.response?.data?.message || 'Failed to load rewards.'
    loadState.value = 'error'
  }
}

async function placeOrder(rewardId: number) {
  const f = getOrderForm(rewardId)
  f.state = 'loading'
  f.error = ''
  try {
    const payload: any = { quantity: f.quantity, fulfillmentType: f.fulfillmentType, rewardId }
    if (f.fulfillmentType === 'SHIP') {
      // Create the shipping address first, then use its ID
      const addrRes = await apiClient.post<{ id: number }>('/accounts/me/addresses', f.address)
      payload.addressId = addrRes.data.id
    }
    const response = await apiClient.post<RewardOrderResponse>('/reward-orders', payload)
    f.order = response.data
    f.state = 'success'
  } catch (err: any) {
    f.error = err.response?.data?.message || 'Order failed.'
    f.state = 'error'
  }
}

onMounted(fetchRewards)
</script>

<template>
  <div class="reward-catalog">
    <h1>Reward Catalog</h1>

    <p v-if="loadState === 'loading'">Loading rewards...</p>
    <p v-if="loadState === 'error'" class="error-message">{{ loadError }}</p>

    <div v-if="loadState === 'success'">
      <p v-if="rewards.length === 0">No rewards available.</p>
      <div v-for="reward in rewards" :key="reward.id" class="reward-item">
        <div class="reward-header">
          <strong>{{ reward.title }}</strong>
          <span v-if="reward.tier" class="badge">{{ reward.tier }}</span>
          <span class="badge">{{ reward.status }}</span>
        </div>
        <p>Inventory: {{ reward.inventoryCount }} | Per-user limit: {{ reward.perUserLimit }} | Fulfillment: {{ reward.fulfillmentType }}</p>

        <div v-if="getOrderForm(reward.id).state !== 'success'" class="order-form">
          <div class="form-row">
            <label>Quantity</label>
            <input v-model.number="getOrderForm(reward.id).quantity" type="number" min="1" :max="reward.perUserLimit" />
          </div>
          <div class="form-row">
            <label>Fulfillment</label>
            <select v-model="getOrderForm(reward.id).fulfillmentType">
              <option value="SHIP">Ship</option>
              <option value="VOUCHER">Voucher</option>
              <option value="PICKUP">Pickup</option>
            </select>
          </div>
          <template v-if="getOrderForm(reward.id).fulfillmentType === 'SHIP'">
            <div class="form-row">
              <label>Address Line 1</label>
              <input v-model="getOrderForm(reward.id).address.line1" required />
            </div>
            <div class="form-row">
              <label>Address Line 2</label>
              <input v-model="getOrderForm(reward.id).address.line2" />
            </div>
            <div class="form-row">
              <label>City</label>
              <input v-model="getOrderForm(reward.id).address.city" required />
            </div>
            <div class="form-row">
              <label>State</label>
              <input v-model="getOrderForm(reward.id).address.state" required maxlength="2" placeholder="e.g. IL" />
            </div>
            <div class="form-row">
              <label>Zip</label>
              <input v-model="getOrderForm(reward.id).address.zip" required placeholder="e.g. 62701" />
            </div>
          </template>
          <button :disabled="getOrderForm(reward.id).state === 'loading'" @click="placeOrder(reward.id)">
            {{ getOrderForm(reward.id).state === 'loading' ? 'Placing Order...' : 'Place Order' }}
          </button>
          <p v-if="getOrderForm(reward.id).state === 'error'" class="error-message">{{ getOrderForm(reward.id).error }}</p>
        </div>

        <div v-if="getOrderForm(reward.id).state === 'success'" class="order-result">
          <p class="success-message">Order placed! Status: {{ getOrderForm(reward.id).order!.status }}</p>
          <p v-if="getOrderForm(reward.id).order!.voucherCode">Voucher: {{ getOrderForm(reward.id).order!.voucherCode }}</p>
          <p v-if="getOrderForm(reward.id).order!.trackingNumber">Tracking: {{ getOrderForm(reward.id).order!.trackingNumber }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reward-catalog { max-width: 800px; }
.reward-item { border: 1px solid #ddd; border-radius: 4px; padding: 1rem; margin-bottom: 0.75rem; }
.reward-header { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.25rem; }
.badge { font-size: 0.8rem; padding: 0.1rem 0.4rem; background: #e2e3e5; border-radius: 3px; }
.order-form { border-top: 1px solid #eee; padding-top: 0.75rem; margin-top: 0.5rem; }
.form-row { margin-bottom: 0.5rem; }
.form-row label { display: block; font-weight: bold; font-size: 0.85rem; margin-bottom: 0.15rem; }
.form-row input, .form-row select { width: 100%; padding: 0.4rem; box-sizing: border-box; }
button { padding: 0.5rem 1rem; cursor: pointer; margin-top: 0.25rem; }
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error-message { color: red; margin-top: 0.5rem; }
.success-message { color: green; margin-top: 0.5rem; }
</style>
