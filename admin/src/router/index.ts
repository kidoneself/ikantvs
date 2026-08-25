import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { canAccessRoute } from '@/utils/permission'
import { canAccessAdmin } from '@/config/roles'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { guest: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'content/media',
        name: 'media',
        component: () => import('@/views/content/MediaList.vue'),
        meta: { title: '影视库' },
      },
      {
        path: 'content/daily',
        name: 'daily',
        component: () => import('@/views/content/DailyList.vue'),
        meta: { title: '每日更新' },
      },
      {
        path: 'content/pool',
        name: 'pool',
        component: () => import('@/views/content/PoolIngest.vue'),
        meta: { title: '同行录入', mode: 'peer' },
      },
      {
        path: 'content/pool-self',
        name: 'pool-self',
        component: () => import('@/views/content/PoolIngest.vue'),
        meta: { title: '自营录入', mode: 'self' },
      },
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('@/views/analytics/Index.vue'),
        meta: { title: '访问统计' },
      },
      {
        path: 'ops/sensitive',
        name: 'sensitive',
        component: () => import('@/views/ops/Sensitive.vue'),
        meta: { title: '敏感词' },
      },
      {
        path: 'ops/transfer-jobs',
        name: 'transfer-jobs',
        component: () => import('@/views/ops/TransferJobs.vue'),
        meta: { title: '转存任务' },
      },
      {
        path: 'ops/transfer-accounts',
        name: 'transfer-accounts',
        component: () => import('@/views/ops/TransferAccounts.vue'),
        meta: { title: '网盘账号' },
      },
      {
        path: 'ops/transfer',
        redirect: '/ops/transfer-jobs',
      },
      {
        path: 'ops/doc-monitor',
        name: 'doc-monitor',
        component: () => import('@/views/ops/DocMonitor.vue'),
        meta: { title: '文档采集' },
      },
      {
        path: 'ops/ip-guard',
        name: 'ip-guard',
        component: () => import('@/views/ops/IpGuard.vue'),
        meta: { title: 'IP 防护' },
      },
      {
        path: 'ops/live-qrcode',
        name: 'live-qrcode',
        component: () => import('@/views/ops/LiveQrcode.vue'),
        meta: { title: '加群活码' },
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '后台账号' },
      },
      {
        path: 'system/settings',
        name: 'settings',
        component: () => import('@/views/system/Settings.vue'),
        meta: { title: '系统设置' },
      },
    ],
  },
]

const router = createRouter({
  // 支持挂在子路径（测试机 /__a/）；默认 Vite base 为 /
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.user && auth.token) await auth.init()

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  if (to.meta.requiresAuth && auth.isLoggedIn && !canAccessAdmin(auth.role)) {
    auth.logout()
    return { name: 'login', query: { redirect: to.fullPath, error: 'no_staff' } }
  }
  const name = to.name as string
  if (to.meta.requiresAuth && name && !canAccessRoute(name, auth.role)) {
    return { name: 'dashboard' }
  }
})

router.afterEach((to) => {
  const base = '爱看后台'
  document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
