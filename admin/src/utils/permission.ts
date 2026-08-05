import { ROUTE_MIN_ROLE, type MenuItem } from '@/config/menu'
import { isAtLeast, type StaffRole } from '@/config/roles'

export function canSeeMenu(item: MenuItem, role?: string): boolean {
  const min = item.minRole || 'contributor'
  return isAtLeast(role, min)
}

export function filterMenus(menus: MenuItem[], role?: string): MenuItem[] {
  return menus
    .map((m) => {
      if (m.children) {
        const children = m.children.filter((c) => canSeeMenu(c, role))
        if (children.length === 0) return null
        return { ...m, children }
      }
      return canSeeMenu(m, role) ? m : null
    })
    .filter((m): m is MenuItem => m != null)
}

export function canAccessRoute(routeName: string, role?: string): boolean {
  const min = ROUTE_MIN_ROLE[routeName] as StaffRole | undefined
  if (!min) return true
  return isAtLeast(role, min)
}
