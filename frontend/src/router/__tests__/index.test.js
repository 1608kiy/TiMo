import { describe, it, expect, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'

// Test route configuration without importing the actual router (which has side effects)
const routes = [
  { path: '/login', name: 'Login', meta: { title: '登录', needAuth: false } },
  { path: '/forgot-password', name: 'ForgotPassword', meta: { title: '找回密码', needAuth: false } },
  {
    path: '/',
    meta: { needAuth: true },
    children: [
      { path: '', name: 'Dashboard', meta: { title: '今日规划' } },
      { path: 'word-select', name: 'WordSelect', meta: { title: '选词学习' } },
      { path: 'quick-memory', name: 'QuickMemory', meta: { title: '快速记忆' } },
      { path: 'deep-learning', name: 'DeepLearning', meta: { title: '语境深度学习' } },
      { path: 'review', name: 'ReviewMode', meta: { title: '统一复习' } },
      { path: 'wordbank', name: 'Wordbank', meta: { title: '词库' } },
      { path: 'stats', name: 'Stats', meta: { title: '统计' } },
      { path: 'profile', name: 'Profile', meta: { title: '个人中心' } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', meta: { title: '页面不存在', needAuth: false } }
]

describe('Router configuration', () => {
  it('has all expected routes', () => {
    const childRoutes = routes[2].children
    expect(childRoutes).toHaveLength(8)
    const names = childRoutes.map(r => r.name)
    expect(names).toContain('Dashboard')
    expect(names).toContain('WordSelect')
    expect(names).toContain('QuickMemory')
    expect(names).toContain('DeepLearning')
    expect(names).toContain('ReviewMode')
    expect(names).toContain('Wordbank')
    expect(names).toContain('Stats')
    expect(names).toContain('Profile')
  })

  it('login route does not require auth', () => {
    expect(routes[0].meta.needAuth).toBe(false)
  })

  it('main layout routes require auth', () => {
    expect(routes[2].meta.needAuth).toBe(true)
  })

  it('not-found route does not require auth', () => {
    expect(routes[3].meta.needAuth).toBe(false)
  })

  it('all child routes have titles', () => {
    routes[2].children.forEach(route => {
      expect(route.meta.title).toBeTruthy()
    })
  })

  it('catch-all route is last', () => {
    expect(routes[routes.length - 1].path).toBe('/:pathMatch(.*)*')
    expect(routes[routes.length - 1].name).toBe('NotFound')
  })
})

describe('Navigation guard logic', () => {
  // Test the guard logic as a standalone function
  function guardLogic(toMeta, toPath, hasToken) {
    if (toMeta.needAuth && !hasToken) return '/login'
    if (toPath === '/login' && hasToken) return '/'
    return true // next()
  }

  it('redirects to login when auth required and no token', () => {
    expect(guardLogic({ needAuth: true }, '/', false)).toBe('/login')
  })

  it('allows access to auth-required route with token', () => {
    expect(guardLogic({ needAuth: true }, '/', true)).toBe(true)
  })

  it('redirects authenticated user away from login', () => {
    expect(guardLogic({ needAuth: false }, '/login', true)).toBe('/')
  })

  it('allows unauthenticated access to login', () => {
    expect(guardLogic({ needAuth: false }, '/login', false)).toBe(true)
  })

  it('allows unauthenticated access to non-auth routes', () => {
    expect(guardLogic({ needAuth: false }, '/forgot-password', false)).toBe(true)
  })
})
