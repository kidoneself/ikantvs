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
    title: '内容中心',
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
    ],
  },
  {
    key: 'ops',
    title: '运营中心',
    icon: 'SetUp',
    children: [
      {
        key: 'analytics',
        title: '数据洞察',
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
        key: 'transfer-jobs',
        title: '转存记录',
        icon: 'List',
        path: '/ops/transfer-jobs',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'transfer-accounts',
        title: '网盘配置',
        icon: 'FolderOpened',
        path: '/ops/transfer-accounts',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'doc-monitor',
        title: '文档资源发现',
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
      {
        key: 'live-qrcode',
        title: '活码 / 加群',
        icon: 'Iphone',
        path: '/ops/live-qrcode',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'site-domains',
        title: '域名网盘',
        icon: 'Link',
        path: '/ops/site-domains',
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
        title: '账号管理',
        icon: 'User',
        path: '/system/users',
        ready: true,
        minRole: 'admin',
      },
      {
        key: 'settings',
        title: '系统配置',
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
  analytics: '数据洞察',
  sensitive: '敏感词',
  'transfer-jobs': '转存记录',
  'transfer-accounts': '网盘配置',
  'doc-monitor': '文档资源发现',
  'ip-guard': 'IP 防护',
  'live-qrcode': '活码 / 加群',
  'site-domains': '域名网盘',
  users: '账号管理',
  settings: '系统配置',
}

/** 路由 meta.minRole */
export const ROUTE_MIN_ROLE: Record<string, StaffRole> = {
  dashboard: 'contributor',
  media: 'contributor',
  daily: 'reviewer',
  analytics: 'reviewer',
  sensitive: 'admin',
  'transfer-jobs': 'admin',
  'transfer-accounts': 'admin',
  'doc-monitor': 'admin',
  'ip-guard': 'admin',
  'live-qrcode': 'admin',
  'site-domains': 'admin',
  users: 'admin',
  settings: 'admin',
}
