/**
 * 后台菜单配置 + 角色权限。
 *
 * roles：可见该菜单的最低角色集合（满足其一且 level 够即可）。
 * 未写 roles = 所有 staff（contributor+）可见。
 */
import type { StaffRole } from './roles'

export interface MenuItem {
  key: string
  title: string
  icon: string
  path?: string
  ready?: boolean
  /** 最低角色要求；不填则 contributor 及以上 */
  minRole?: StaffRole
  roles?: StaffRole[]
  children?: MenuItem[]
}

export const ADMIN_MENUS: MenuItem[] = [
  {
    key: 'dashboard',
    title: '概览',
    icon: 'Odometer',
    path: '/dashboard',
    ready: true,
    minRole: 'contributor',
  },
  {
    key: 'content',
    title: '内容',
    icon: 'Film',
    children: [
      {
        key: 'media',
        title: '影视库',
        icon: 'VideoCamera',
        path: '/content/media',
        ready: true,
        minRole: 'contributor',
      },
      {
        key: 'daily',
        title: '每日更新',
        icon: 'Calendar',
        path: '/content/daily',
        ready: true,
        minRole: 'reviewer',
      },
      {
        key: 'pool',
        title: '同行录入',
        icon: 'DocumentAdd',
        path: '/content/pool',
        ready: true,
        minRole: 'contributor',
      },
      {
        key: 'pool-self',
        title: '自营录入',
        icon: 'FolderAdd',
        path: '/content/pool-self',
        ready: true,
        minRole: 'contributor',
      },
    ],
  },
  {
    key: 'transfer',
    title: '转存',
    icon: 'FolderOpened',
    children: [
      {
        key: 'transfer-jobs',
        title: '转存任务',
        icon: 'List',
        path: '/ops/transfer-jobs',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'transfer-accounts',
        title: '网盘账号',
        icon: 'Key',
        path: '/ops/transfer-accounts',
        ready: true,
        minRole: 'admin',
      },
    ],
  },
  {
    key: 'ops',
    title: '运营',
    icon: 'SetUp',
    children: [
      {
        key: 'analytics',
        title: '访问统计',
        icon: 'DataLine',
        path: '/analytics',
        ready: true,
        minRole: 'reviewer',
      },
      {
        key: 'sensitive',
        title: '敏感词',
        icon: 'Warning',
        path: '/ops/sensitive',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'live-qrcode',
        title: '加群活码',
        icon: 'Iphone',
        path: '/ops/live-qrcode',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'doc-monitor',
        title: '文档采集',
        icon: 'Document',
        path: '/ops/doc-monitor',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'ip-guard',
        title: 'IP 防护',
        icon: 'Lock',
        path: '/ops/ip-guard',
        ready: true,
        minRole: 'admin',
      },
    ],
  },
  {
    key: 'system',
    title: '系统',
    icon: 'Setting',
    children: [
      {
        key: 'users',
        title: '后台账号',
        icon: 'User',
        path: '/system/users',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'settings',
        title: '系统设置',
        icon: 'Tools',
        path: '/system/settings',
        ready: true,
        minRole: 'admin',
      },
    ],
  },
]

export const ROUTE_TITLES: Record<string, string> = {
  dashboard: '概览',
  media: '影视库',
  daily: '每日更新',
  pool: '同行录入',
  'pool-self': '自营录入',
  analytics: '访问统计',
  sensitive: '敏感词',
  'transfer-jobs': '转存任务',
  'transfer-accounts': '网盘账号',
  'doc-monitor': '文档采集',
  'ip-guard': 'IP 防护',
  'live-qrcode': '加群活码',
  users: '后台账号',
  settings: '系统设置',
}

/** 路由 meta.minRole */
export const ROUTE_MIN_ROLE: Record<string, StaffRole> = {
  dashboard: 'contributor',
  media: 'contributor',
  daily: 'reviewer',
  pool: 'contributor',
  'pool-self': 'contributor',
  analytics: 'reviewer',
  sensitive: 'admin',
  'transfer-jobs': 'admin',
  'transfer-accounts': 'admin',
  'doc-monitor': 'admin',
  'ip-guard': 'admin',
  'live-qrcode': 'admin',
  users: 'admin',
  settings: 'admin',
}
