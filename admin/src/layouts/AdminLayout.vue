<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { ADMIN_MENUS, ROUTE_TITLES, type MenuItem } from '@/config/menu'
import { filterMenus } from '@/utils/permission'
import { publicSiteBase } from '@/api/media'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const publicSite = publicSiteBase()
const collapsed = ref(false)

const visibleMenus = computed(() => filterMenus(ADMIN_MENUS, auth.role))

const breadcrumbs = computed(() => {
  const name = route.name as string
  const crumbs: string[] = ['首页']
  if (name && name !== 'dashboard') {
    crumbs.push(ROUTE_TITLES[name] || (route.meta.title as string) || '')
  } else {
    crumbs.push('概览')
  }
  return crumbs.filter(Boolean)
})

function isActive(item: MenuItem) {
  if (!item.path) return false
  return route.path === item.path || route.path.startsWith(item.path + '/')
}

function navTo(item: MenuItem) {
  if (!item.path) return
  router.push(item.path)
}

function logout() {
  auth.logout()
  ElMessage.success('已退出')
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-layout" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="logo" @click="router.push('/dashboard')">
        <span class="logo-icon">爱</span>
        <span v-show="!collapsed" class="logo-text">爱看后台</span>
      </div>

      <nav class="menu">
        <template v-for="group in visibleMenus" :key="group.key">
          <!-- 无子菜单 -->
          <div
            v-if="!group.children"
            class="menu-item"
            :class="{ active: isActive(group), planned: !group.ready }"
            @click="navTo(group)"
          >
            <el-icon><component :is="group.icon" /></el-icon>
            <span v-show="!collapsed">{{ group.title }}</span>
          </div>

          <!-- 分组子菜单 -->
          <template v-else>
            <div v-show="!collapsed" class="menu-group">{{ group.title }}</div>
            <div
              v-for="child in group.children"
              :key="child.key"
              class="menu-item sub"
              :class="{ active: isActive(child), planned: !child.ready }"
              @click="navTo(child)"
            >
              <el-icon><component :is="child.icon" /></el-icon>
              <span v-show="!collapsed">{{ child.title }}</span>
              <el-tag v-if="!child.ready && !collapsed" size="small" type="info" class="tag">规划</el-tag>
            </div>
          </template>
        </template>
      </nav>

      <div class="sidebar-toggle" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </aside>

    <div class="main-wrap">
      <header class="header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(c, i) in breadcrumbs" :key="i">{{ c }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="header-right">
          <a :href="publicSite" target="_blank" rel="noopener" class="link-front">用户前台</a>
          <el-tag size="small" type="info">{{ auth.roleLabel }}</el-tag>
          <el-dropdown trigger="click">
            <span class="user-trigger">
              {{ auth.user?.nickname || auth.user?.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main">
        <router-view v-slot="{ Component, route: r }">
          <component
            :is="Component"
            v-if="r.meta.soonTitle"
            :title="r.meta.soonTitle"
            :desc="r.meta.soonDesc"
          />
          <component :is="Component" v-else />
        </router-view>
      </main>

      <footer class="footer">jyinshi-next admin · content-first roadmap</footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-w);
  background: linear-gradient(180deg, var(--sidebar-bg) 0%, var(--sidebar-bg-2) 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
  flex-shrink: 0;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    inset: 0 0 0 auto;
    width: 1px;
    background: rgba(255, 255, 255, 0.06);
  }

  .collapsed & {
    width: 68px;
  }
}

.logo {
  height: var(--header-h);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);

  .logo-icon {
    width: 34px;
    height: 34px;
    border-radius: 9px;
    background: linear-gradient(135deg, #6366f1, #4f46e5);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 800;
    flex-shrink: 0;
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.45);
  }

  .logo-text {
    color: #fff;
    font-weight: 700;
    font-size: 1.02rem;
    letter-spacing: 0.01em;
    white-space: nowrap;
  }
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 16px;
}

.menu-group {
  padding: 16px 12px 6px;
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.32);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 12px;
  margin-bottom: 2px;
  border-radius: 9px;
  color: var(--sidebar-text);
  cursor: pointer;
  font-size: 0.9rem;
  position: relative;
  transition: all 0.16s ease;

  .el-icon {
    font-size: 17px;
    flex-shrink: 0;
  }

  &.sub {
    padding-left: 14px;
  }

  .tag {
    margin-left: auto;
    transform: scale(0.78);
    opacity: 0.7;
  }

  &:hover {
    color: var(--sidebar-text-strong);
    background: rgba(255, 255, 255, 0.06);
  }

  &.planned:not(.active) {
    opacity: 0.6;
  }

  &.active {
    color: #fff;
    background: linear-gradient(135deg, #6366f1, #4f46e5);
    box-shadow: 0 6px 16px rgba(79, 70, 229, 0.4);
  }
}

.collapsed .menu-item {
  justify-content: center;
  padding: 11px 0;

  &.sub {
    padding-left: 0;
  }
}

.sidebar-toggle {
  padding: 13px;
  text-align: center;
  color: var(--sidebar-text);
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  transition: color 0.15s;

  &:hover {
    color: #fff;
  }
}

.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: var(--header-h);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(8px);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}

:deep(.el-breadcrumb__inner) {
  color: var(--text-muted);
  font-weight: 500;
}
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.link-front {
  font-size: 0.86rem;
  color: var(--text-soft);
  padding: 5px 12px;
  border-radius: 7px;
  border: 1px solid var(--border);
  transition: all 0.15s;

  &:hover {
    color: var(--brand);
    border-color: var(--brand-soft-2);
    background: var(--brand-soft);
  }
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text);
  padding: 5px 10px;
  border-radius: 7px;
  transition: background 0.15s;

  &:hover {
    background: var(--fill);
  }
}

.main {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

.footer {
  padding: 12px 24px;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-align: center;
  border-top: 1px solid var(--border);
  background: var(--surface);
}
</style>
