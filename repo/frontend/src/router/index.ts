import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import LockedView from '@/views/LockedView.vue'
import AppealView from '@/views/AppealView.vue'
import WorkspaceShell from '@/views/WorkspaceShell.vue'
import AdminDashboard from '@/views/admin/AdminDashboard.vue'
import BlacklistPanel from '@/views/admin/BlacklistPanel.vue'
import AppealReviewPanel from '@/views/admin/AppealReviewPanel.vue'
import PasswordResetPanel from '@/views/admin/PasswordResetPanel.vue'
import VerificationQueue from '@/views/admin/VerificationQueue.vue'
import RoleApprovalPanel from '@/views/admin/RoleApprovalPanel.vue'
import VerificationSubmit from '@/views/participant/VerificationSubmit.vue'
import RoleManagement from '@/views/participant/RoleManagement.vue'
import EventManagement from '@/views/org/EventManagement.vue'
import ResourceManagement from '@/views/org/ResourceManagement.vue'
import EventBrowse from '@/views/participant/EventBrowse.vue'
import ResourceBrowse from '@/views/participant/ResourceBrowse.vue'
import RewardCatalog from '@/views/participant/RewardCatalog.vue'
import RegistrationReview from '@/views/admin/RegistrationReview.vue'
import PolicyManagement from '@/views/admin/PolicyManagement.vue'
import FulfillmentPanel from '@/views/admin/FulfillmentPanel.vue'
import AlertRuleConfig from '@/views/admin/AlertRuleConfig.vue'
import WorkOrderPanel from '@/views/admin/WorkOrderPanel.vue'
import AnalyticsDashboard from '@/views/admin/AnalyticsDashboard.vue'
import ReportPanel from '@/views/admin/ReportPanel.vue'
import AuditLogViewer from '@/views/admin/AuditLogViewer.vue'
import VolunteerDashboard from '@/views/volunteer/VolunteerDashboard.vue'
import VolunteerVerificationQueue from '@/views/volunteer/VolunteerVerificationQueue.vue'
import VolunteerRegistrationReview from '@/views/volunteer/VolunteerRegistrationReview.vue'

const PlaceholderView = { template: '<div><h2>Welcome to your workspace</h2></div>' }

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/locked', name: 'locked', component: LockedView },
  { path: '/appeal', name: 'appeal', component: AppealView },
  {
    path: '/workspace/admin',
    component: WorkspaceShell,
    meta: { requiresAuth: true, requiresRole: 'ADMIN' },
    children: [
      { path: '', name: 'admin-dashboard', component: AdminDashboard },
      { path: 'blacklist', name: 'admin-blacklist', component: BlacklistPanel },
      { path: 'appeals', name: 'admin-appeals', component: AppealReviewPanel },
      { path: 'password-resets', name: 'admin-password-resets', component: PasswordResetPanel },
      { path: 'verification', name: 'admin-verification', component: VerificationQueue },
      { path: 'roles', name: 'admin-roles', component: RoleApprovalPanel },
      { path: 'registrations', name: 'admin-registrations', component: RegistrationReview },
      { path: 'policies', name: 'admin-policies', component: PolicyManagement },
      { path: 'fulfillment', name: 'admin-fulfillment', component: FulfillmentPanel },
      { path: 'alerts', name: 'admin-alerts', component: AlertRuleConfig },
      { path: 'work-orders', name: 'admin-work-orders', component: WorkOrderPanel },
      { path: 'analytics', name: 'admin-analytics', component: AnalyticsDashboard },
      { path: 'reports', name: 'admin-reports', component: ReportPanel },
      { path: 'audit-logs', name: 'admin-audit-logs', component: AuditLogViewer },
    ],
  },
  {
    path: '/workspace/PARTICIPANT',
    component: WorkspaceShell,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'participant-home', component: PlaceholderView },
      { path: 'verification', name: 'participant-verification', component: VerificationSubmit },
      { path: 'roles', name: 'participant-roles', component: RoleManagement },
      { path: 'events', name: 'participant-events', component: EventBrowse },
      { path: 'resources', name: 'participant-resources', component: ResourceBrowse },
      { path: 'rewards', name: 'participant-rewards', component: RewardCatalog },
    ],
  },
  {
    path: '/workspace/ORG_OPERATOR',
    component: WorkspaceShell,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'org-home', component: PlaceholderView },
      { path: 'events', name: 'org-events', component: EventManagement },
      { path: 'resources', name: 'org-resources', component: ResourceManagement },
    ],
  },
  {
    path: '/workspace/VOLUNTEER',
    component: WorkspaceShell,
    meta: { requiresAuth: true, requiresRole: 'VOLUNTEER' },
    children: [
      { path: '', name: 'volunteer-home', component: VolunteerDashboard },
      { path: 'verification', name: 'volunteer-verification', component: VolunteerVerificationQueue },
      { path: 'registrations', name: 'volunteer-registrations', component: VolunteerRegistrationReview },
    ],
  },
  {
    path: '/workspace/:role',
    name: 'workspace',
    component: WorkspaceShell,
    meta: { requiresAuth: true },
    children: [],
  },
  { path: '/', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const publicRoutes = ['/login', '/locked', '/appeal', '/register']

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (publicRoutes.includes(to.path)) {
    return true
  }

  if (!auth.isAuthenticated) {
    return '/login'
  }

  if (auth.accountStatus === 'LOCKED') {
    return '/locked'
  }

  if (auth.accountStatus === 'BLACKLISTED') {
    return '/appeal'
  }

  const requiredRole = to.meta.requiresRole as string | undefined
  if (!requiredRole) {
    const parentWithRole = to.matched.find(r => r.meta.requiresRole)
    if (parentWithRole && auth.activeRole !== parentWithRole.meta.requiresRole) {
      return `/workspace/${auth.activeRole}`
    }
  } else if (auth.activeRole !== requiredRole) {
    return `/workspace/${auth.activeRole}`
  }

  return true
})

export default router
