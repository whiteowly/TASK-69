export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accountId: number
  username: string
  approvedRoles: string[]
  activeRole: string
  permissions: string[]
}

export interface ErrorResponse {
  code: string
  message: string
  fieldErrors?: FieldError[]
  correlationId: string
  timestamp: string
}

export interface FieldError {
  field: string
  reason: string
  message: string
}

export interface RegisterRequest {
  username: string
  password: string
  accountType: 'PERSON' | 'ORGANIZATION'
}

export interface RegisterResponse {
  accountId: number
  username: string
  status: string
  createdAt: string
}

export interface AppealRequest {
  blacklistId: number
  appealText: string
  contactNote?: string
}

export interface AppealResponse {
  appealId: number
  blacklistRecordId: number
  accountId: number
  appealText: string
  contactNote?: string
  status: string
  dueDate: string
  createdAt: string
}

export interface BlacklistRequest {
  targetAccountId: number
  reasonCode: string
  note?: string
}

export interface BlacklistResponse {
  blacklistId: number
  accountId: number
  reasonCode: string
  createdAt: string
}

export interface AppealDecisionRequest {
  decision: string
  decisionNote?: string
}

export interface PasswordResetRequest {
  targetAccountId: number
  identityReviewNote: string
}

export interface PasswordResetResponse {
  resetId: number
  status: string
  temporarySecretIssued: boolean
  temporarySecret: string
}

export interface PersonVerificationRequest { legalName: string; dateOfBirth: string }
export interface PersonVerificationResponse { verificationId: number; status: string; submittedAt: string }
export interface OrgDocumentResponse { documentId: number; status: string; duplicateChecksumFlag: boolean }
export interface VerificationDecisionRequest { decision: string; reasonCode?: string; reviewNote?: string }
export interface VerificationQueueItem { type: string; id: number; accountId: number; status: string; legalName?: string; dobMasked?: string; fileName?: string; fileSize?: number; contentType?: string; duplicateFlag?: boolean; createdAt: string }
export interface RoleRequestDto { role: string; scopeType?: string; scopeId?: string }
export interface RoleSwitchRequest { role: string; scopeId?: string }
export interface RoleMembershipResponse { id: number; roleType: string; scopeId?: string; status: string; createdAt: string }
export interface RoleDecisionRequest { decision: string; reviewNote?: string }

// Events (Slice 4)
export interface EventRequest { title: string; mode: string; location?: string; startAt: string; endAt: string; capacity?: number; waitlistEnabled?: boolean; manualReviewRequired?: boolean; organizationId?: string; registrationFormSchema?: string; status?: string }
export interface EventResponse { id: number; title: string; mode: string; location?: string; startAt: string; endAt: string; capacity: number; waitlistEnabled: boolean; manualReviewRequired: boolean; registrationFormSchema?: string; status: string; approvedCount?: number }
export interface RegistrationResponse { id: number; eventId: number; accountId: number; status: string; createdAt: string }
export interface RosterEntry { registrationId: number; accountId: number; status: string; createdAt: string }

// Resources (Slice 5)
export interface ResourceRequest { type: string; title: string; description?: string; inventoryCount?: number; fileVersion?: string; organizationId?: string; usagePolicyId?: number }
export interface ResourceResponse { id: number; type: string; title: string; description?: string; inventoryCount?: number; status: string }
export interface ClaimResult { result: string; reasonCode?: string; printableNoticeId?: number }
export interface PolicyRequest { name: string; scope: string; maxActions: number; windowDays: number; resourceAction: string }

// Rewards (Slice 6)
export interface RewardResponse { id: number; title: string; tier?: string; inventoryCount: number; perUserLimit: number; fulfillmentType: string; status: string }
export interface RewardOrderResponse { id: number; rewardId: number; accountId?: number; quantity?: number; fulfillmentType?: string; status: string; statusChangedAt?: string; trackingNumber?: string; voucherCode?: string; note?: string; createdAt: string; updatedAt?: string }
export interface AddressRequest { line1: string; line2?: string; city: string; state: string; zip: string }

// Alerts (Slice 7)
export interface AlertRuleResponse { id: number; alertType: string; severity: string; thresholdOperator: string; thresholdValue: number; cooldownSeconds: number }
export interface WorkOrderResponse { id: number; title: string; severity: string; status: string; assignedTo?: number; firstResponseSeconds?: number; timeToCloseSeconds?: number; createdAt: string }

// Analytics (Slice 8)
export interface OperationsSummary { totalRegistrations: number; approvedRegistrations: number; cancelledRegistrations: number; waitlistedRegistrations: number; totalClaims: number; allowedClaims: number; deniedClaims: number; totalOrders: number; deliveredOrders: number; redeemedOrders: number; registrationApprovalRate: number; orderCompletionRate: number; staffWorkload: Record<string, number>; popularCategories: Record<string, number>; retentionRate: number }
export interface ReportExecutionResponse { id: number; status: string; exportFilePath?: string; templateId?: number; resultData?: string }
