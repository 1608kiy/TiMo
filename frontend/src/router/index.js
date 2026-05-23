import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../components/layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', needAuth: false }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('../views/ForgotPassword.vue'),
    meta: { title: '找回密码', needAuth: false }
  },
  {
    path: '/',
    component: MainLayout,
    meta: { needAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '今日规划' }
      },
      {
        path: 'dashboard',
        redirect: '/'
      },
      {
        path: 'exam-plan',
        name: 'ExamPlan',
        component: () => import('../views/ExamPlan.vue'),
        meta: { title: '备考规划' }
      },
      {
        path: 'word-select',
        name: 'WordSelect',
        component: () => import('../views/WordSelect.vue'),
        meta: { title: '选词学习' }
      },
      {
        path: 'quick-memory',
        name: 'QuickMemory',
        component: () => import('../views/QuickMemory.vue'),
        meta: { title: '快速记忆' }
      },
      {
        path: 'deep-learning',
        name: 'DeepLearning',
        component: () => import('../views/DeepLearning.vue'),
        meta: { title: '语境深度学习' }
      },
      {
        path: 'review',
        name: 'ReviewMode',
        component: () => import('../views/ReviewMode.vue'),
        meta: { title: '统一复习' }
      },
      {
        path: 'study/reverse-recall',
        name: 'ReverseRecall',
        component: () => import('../views/ReverseRecall.vue'),
        meta: { title: '中→英召回', needAuth: true }
      },
      {
        path: 'wordbank',
        name: 'Wordbank',
        component: () => import('../views/Wordbank.vue'),
        meta: { title: '词库' }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('../views/Stats.vue'),
        meta: { title: '统计' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'calendar',
        name: 'Calendar',
        component: () => import('../views/Calendar.vue'),
        meta: { title: '学习日历' }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { needAuth: true, needAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('../views/admin/AdminDashboard.vue'),
        meta: { title: '系统总览', needAdmin: true }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../views/admin/AdminUsers.vue'),
        meta: { title: '用户管理', needAdmin: true }
      },
      {
        path: 'words',
        name: 'AdminWords',
        component: () => import('../views/admin/AdminWords.vue'),
        meta: { title: '词库管理', needAdmin: true }
      },
      {
        path: 'ai',
        name: 'AdminAI',
        component: () => import('../views/admin/AdminAI.vue'),
        meta: { title: 'AI 配置', needAdmin: true }
      },
      {
        path: 'stats',
        name: 'AdminStats',
        component: () => import('../views/admin/AdminStats.vue'),
        meta: { title: '全局统计', needAdmin: true }
      },
      {
        path: 'settings',
        name: 'AdminSettings',
        component: () => import('../views/admin/AdminSettings.vue'),
        meta: { title: '系统配置', needAdmin: true }
      },
      {
        path: 'logs',
        name: 'AdminLogs',
        component: () => import('../views/admin/AdminLogs.vue'),
        meta: { title: '操作日志', needAdmin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在', needAuth: false }
  }
]

function getRoleFromToken() {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.role || null
  } catch { return null }
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - TiMo背单词` : 'TiMo背单词'
  const token = localStorage.getItem('token')

  if (to.meta.needAuth && !token) {
    next('/login')
    return
  }

  if (to.path === '/login' && token) {
    const role = getRoleFromToken()
    next(role === 'SUPER_ADMIN' || role === 'ADMIN' ? '/admin' : '/')
    return
  }

  if (to.meta.needAdmin) {
    const role = getRoleFromToken()
    if (role !== 'SUPER_ADMIN' && role !== 'ADMIN') {
      next('/')
      return
    }
  }

  next()
})

export default router
