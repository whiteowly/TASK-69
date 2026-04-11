<script setup lang="ts">
import { ref } from 'vue'
import apiClient from '@/api/client'
import type { PersonVerificationResponse, OrgDocumentResponse } from '@/types'

// Person verification state
const legalName = ref('')
const dateOfBirth = ref('')
const personState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const personError = ref('')
const personResult = ref<PersonVerificationResponse | null>(null)

// Org document state
const selectedFile = ref<File | null>(null)
const orgState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const orgError = ref('')
const orgResult = ref<OrgDocumentResponse | null>(null)

async function submitPersonVerification() {
  if (!legalName.value || !dateOfBirth.value) {
    personState.value = 'error'
    personError.value = 'Legal name and date of birth are required.'
    return
  }
  personState.value = 'loading'
  personError.value = ''
  try {
    const response = await apiClient.post<PersonVerificationResponse>('/verification/person', {
      legalName: legalName.value,
      dateOfBirth: dateOfBirth.value,
    })
    personResult.value = response.data
    personState.value = 'success'
  } catch (err: any) {
    personError.value = err.response?.data?.message || 'Submission failed.'
    personState.value = 'error'
  }
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
}

async function submitOrgDocument() {
  if (!selectedFile.value) {
    orgState.value = 'error'
    orgError.value = 'Please select a file.'
    return
  }
  orgState.value = 'loading'
  orgError.value = ''
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const response = await apiClient.post<OrgDocumentResponse>('/verification/org-documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    orgResult.value = response.data
    orgState.value = 'success'
  } catch (err: any) {
    orgError.value = err.response?.data?.message || 'Upload failed.'
    orgState.value = 'error'
  }
}
</script>

<template>
  <div class="verification-submit">
    <h1>Verification</h1>

    <!-- Person Verification -->
    <section class="section">
      <h2>Person Verification</h2>
      <form @submit.prevent="submitPersonVerification">
        <div class="form-group">
          <label for="legalName">Legal Name</label>
          <input id="legalName" v-model="legalName" type="text" placeholder="Full legal name" />
        </div>
        <div class="form-group">
          <label for="dateOfBirth">Date of Birth</label>
          <input id="dateOfBirth" v-model="dateOfBirth" type="date" />
        </div>
        <button type="submit" :disabled="personState === 'loading'">
          {{ personState === 'loading' ? 'Submitting...' : 'Submit Verification' }}
        </button>
      </form>
      <p v-if="personState === 'error'" class="error-message">{{ personError }}</p>
      <p v-if="personState === 'success'" class="success-message">
        Verification submitted (ID: {{ personResult?.verificationId }}). Status: {{ personResult?.status }}
      </p>
    </section>

    <!-- Org Document Upload -->
    <section class="section">
      <h2>Organization Credential Upload</h2>
      <form @submit.prevent="submitOrgDocument">
        <div class="form-group">
          <label for="orgFile">Document (PDF or JPG)</label>
          <input id="orgFile" type="file" accept=".pdf,.jpg,.jpeg" @change="onFileChange" />
        </div>
        <p v-if="selectedFile" class="file-info">
          Selected: {{ selectedFile.name }} ({{ selectedFile.size }} bytes)
        </p>
        <button type="submit" :disabled="orgState === 'loading'">
          {{ orgState === 'loading' ? 'Uploading...' : 'Upload Document' }}
        </button>
      </form>
      <p v-if="orgState === 'error'" class="error-message">{{ orgError }}</p>
      <div v-if="orgState === 'success'" class="success-message">
        <p>Document uploaded (ID: {{ orgResult?.documentId }}). Status: {{ orgResult?.status }}</p>
        <p v-if="orgResult?.duplicateChecksumFlag" class="warning-message">
          Warning: This document appears to be a duplicate of a previously uploaded file.
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.verification-submit {
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

.form-group input {
  width: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
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

.warning-message {
  color: orange;
  font-weight: bold;
}

.file-info {
  font-size: 0.9rem;
  color: #555;
  margin: 0.5rem 0;
}
</style>
