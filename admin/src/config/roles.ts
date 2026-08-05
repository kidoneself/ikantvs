/** 运营后台角色（与后端 user.role 一致） */
export type StaffRole = 'contributor' | 'reviewer' | 'admin'

/** @deprecated 历史 C 端账号，后台不再使用 */
export type UserRole = 'user' | StaffRole

/** 数字越大权限越高 */
export const ROLE_LEVEL: Record<UserRole, number> = {
  user: 0,
  contributor: 1,
  reviewer: 2,
  admin: 3,
}

export const STAFF_ROLE_LABEL: Record<StaffRole, string> = {
  contributor: '录入员',
  reviewer: '审核员',
  admin: '管理员',
}

export const ROLE_LABEL: Record<UserRole, string> = {
  user: '普通用户',
  ...STAFF_ROLE_LABEL,
}

export function isStaff(role?: string): role is StaffRole {
  return role === 'contributor' || role === 'reviewer' || role === 'admin'
}

export function isAtLeast(role: string | undefined, min: StaffRole): boolean {
  const r = (role || 'user') as UserRole
  return (ROLE_LEVEL[r] ?? 0) >= ROLE_LEVEL[min]
}

export function canAccessAdmin(role?: string): boolean {
  return isStaff(role)
}
