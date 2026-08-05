import { createRouter, createWebHistory } from 'vue-router'
import { onRoutePerfStart } from '@/lib/pagePerf'

const scrollTops = new Map<string, number>()
const visitedKeepAlive = new Set<string>()

function routeKey(to: { name?: unknown; path: string }) {
  return typeof to.name === 'string' ? to.name : to.path
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (typeof to.name === 'string' && to.meta.keepAlive && scrollTops.has(to.name)) {
      return { left: 0, top: scrollTops.get(to.name)! }
    }
    return { top: 0 }
  },
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: '爱看 · 网盘资源信息流', keepAlive: true } },
    { path: '/ranking', name: 'ranking', component: () => import('@/views/Ranking.vue'), meta: { title: '榜单 · 爱看', keepAlive: true } },
    { path: '/drama', name: 'drama', component: () => import('@/views/Drama.vue'), meta: { title: '短剧 · 爱看', keepAlive: true } },
    { path: '/search', name: 'search', component: () => import('@/views/Search.vue'), meta: { title: '搜索 · 爱看', keepAlive: true } },
    { path: '/find', name: 'find', component: () => import('@/views/SearchLinks.vue'), meta: { title: '资源搜索 · 爱看', keepAlive: true } },
    { path: '/about', name: 'about', component: () => import('@/views/About.vue'), meta: { title: '关于爱看 · 爱看' } },
    { path: '/disclaimer', name: 'disclaimer', component: () => import('@/views/Disclaimer.vue'), meta: { title: '免责声明 · 爱看' } },
    {
      path: '/qr',
      name: 'qr',
      component: () => import('@/views/Qr.vue'),
      meta: { title: '防止失联 · 爱看', bare: true },
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { title: '页面不存在 · 爱看' } },
  ],
})

router.beforeEach((to, from) => {
  const key = routeKey(to)
  const keepAliveRevisit = !!(to.meta.keepAlive && visitedKeepAlive.has(key))
  onRoutePerfStart(key, keepAliveRevisit)
  if (from.meta.keepAlive && typeof from.name === 'string') {
    scrollTops.set(from.name, window.scrollY)
  }
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '爱看'
  document.title = title
  if (to.meta.keepAlive) visitedKeepAlive.add(routeKey(to))
})

export default router
