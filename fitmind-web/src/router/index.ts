import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import PlatformLayout from '../layout/PlatformLayout.vue'

const routes = [
  {
    path: '/',
    component: () => import('../views/Home.vue'),
    meta: {
      title: 'FitMind',
      section: '入口',
      description: 'AI 训练、营养和进度追踪的统一入口。'
    }
  },
  {
    path: '/login',
    component: () => import('../views/user/Login.vue'),
    meta: {
      title: '登录',
      section: '账户',
      description: '进入你的训练操作台。'
    }
  },
  {
    path: '/register',
    component: () => import('../views/user/Register.vue'),
    meta: {
      title: '注册',
      section: '账户',
      description: '创建新的 FitMind 账户。'
    }
  },
  {
    path: '/app',
    component: PlatformLayout,
    children: [
      {
        path: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: {
          title: '训练总览',
          section: '核心',
          description: '查看本周训练节奏、营养摄入和身体趋势。'
        }
      },
      {
        path: 'coach',
        component: () => import('../views/ai/CoachChat.vue'),
        meta: {
          title: 'FitMind 教练',
          section: '核心',
          description: '和 FitMind AI 教练直接对话，获取个性化健身建议。'
        }
      },
      {
        path: 'training',
        component: () => import('../views/plan/Training.vue'),
        meta: {
          title: '训练计划',
          section: '核心',
          description: '安排今天的训练，保持负荷与恢复平衡。'
        }
      },
      {
        path: 'diet',
        component: () => import('../views/plan/Diet.vue'),
        meta: {
          title: '饮食计划',
          section: '核心',
          description: '管理每日摄入，围绕目标调整热量和营养。'
        }
      },
      {
        path: 'community',
        component: () => import('../views/Community.vue'),
        meta: {
          title: '训练社区',
          section: '社交',
          description: '发布动态、互动和跟进训练网络。'
        }
      },
      {
        path: 'history',
        component: () => import('../views/History.vue'),
        meta: {
          title: '历史记录',
          section: '记录',
          description: '回顾训练与饮食轨迹，观察长期变化。'
        }
      },
      {
        path: 'exercise-atlas',
        component: () => import('../views/ExerciseAtlas.vue'),
        meta: {
          title: '动作图谱',
          section: '核心',
          description: '按部位与动作理解训练内容和执行方式。'
        }
      },
      {
        path: 'profile',
        component: () => import('../views/user/Profile.vue'),
        meta: {
          title: '个人画像',
          section: '记录',
          description: '维护身体参数、目标和限制条件。'
        }
      },
      {
        path: 'achievements',
        component: () => import('../views/Achievements.vue'),
        meta: {
          title: '成就系统',
          section: '记录',
          description: '追踪里程碑、连续性和高光表现。'
        }
      },
      {
        path: 'leaderboard',
        component: () => import('../views/Leaderboard.vue'),
        meta: {
          title: '排行榜',
          section: '社交',
          description: '对比训练、热量和社交表现。'
        }
      },
      {
        path: 'food-recognition',
        component: () => import('../views/FoodRecognition.vue'),
        meta: {
          title: '饮食识别',
          section: '核心',
          description: '通过图片识别食物，快速加入饮食记录。'
        }
      },
      { path: 'muscle-3d', redirect: '/app/exercise-atlas' },
      { path: 'exercises', redirect: '/app/exercise-atlas' },
      {
        path: 'admin',
        component: () => import('../views/admin/AdminPanel.vue'),
        meta: {
          title: '系统管理',
          section: '管理',
          description: '处理后台数据、资源与平台配置。'
        }
      }
    ]
  },
  { path: '/muscle-3d', redirect: '/app/exercise-atlas' },
  { path: '/dashboard', redirect: '/app/dashboard' },
  { path: '/coach', redirect: '/app/coach' },
  { path: '/training', redirect: '/app/training' },
  { path: '/diet', redirect: '/app/diet' },
  { path: '/community', redirect: '/app/community' },
  { path: '/history', redirect: '/app/history' },
  { path: '/exercises', redirect: '/app/exercise-atlas' },
  { path: '/profile', redirect: '/app/profile' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  const authRequired = to.path.startsWith('/app')

  if (authRequired && !userStore.token) {
    return '/login'
  }
  return true
})

export default router
