import { createRouter, createWebHistory } from 'vue-router';

// 改进懒加载方式，添加错误处理和预加载
const lazyLoad = (viewPath) => {
  return () => import(`@/views/${viewPath}.vue`).catch(err => {
    console.error(`加载组件失败: ${viewPath}`, err);
    // 如果组件加载失败，返回一个简单的错误组件
    return import('@/views/Error.vue').catch(() => {
      // 如果连错误页面都无法加载，返回一个内联的错误组件
      return {
        template: `
          <div style="padding: 20px; text-align: center;">
            <h2>组件加载失败</h2>
            <p>无法加载 ${viewPath} 组件，请刷新页面或联系管理员</p>
            <button @click="$router.go(-1)" style="padding: 8px 16px;">返回上一页</button>
          </div>
        `
      };
    });
  });
};

// 路由组件懒加载
const LoginView = lazyLoad('Login');
const RegisterView = lazyLoad('Register');
const HomeView = lazyLoad('Home');
const UploadView = lazyLoad('Upload');
const ResultView = lazyLoad('Result');
const HistoryView = lazyLoad('History');
const UserManagementView = lazyLoad('UserManagement');
const PersonalInfoView = lazyLoad('PersonalInfo');
const SettingsView = lazyLoad('Settings');
const VideoUploadView = lazyLoad('VideoUpload');
const VideoHistoryView = lazyLoad('VideoHistory');
const VideoResultView = lazyLoad('VideoResult');

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: RegisterView,
    meta: { requiresAuth: false }
  },
  {
    path: '/home',
    name: 'Home',
    component: HomeView,
    meta: { requiresAuth: true }
  },
  {
    path: '/upload',
    name: 'Upload',
    component: UploadView,
    meta: { requiresAuth: true }
  },
  {
    path: '/result/:id',
    name: 'Result',
    component: ResultView,
    meta: { requiresAuth: true }
  },
  {
    path: '/history',
    name: 'History',
    component: HistoryView,
    meta: { requiresAuth: true }
  },
  {
    path: '/user/profile',
    name: 'PersonalInfo',
    component: PersonalInfoView,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/users',
    name: 'UserManagement',
    component: UserManagementView,
    meta: { 
      requiresAuth: true,
      requiresAdmin: true
    }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: SettingsView,
    meta: { requiresAuth: true }
  },
  {
    path: '/video-upload',
    name: 'VideoUpload',
    component: VideoUploadView,
    meta: {
      requiresAuth: true,
      title: '视频上传分析'
    }
  },
  {
    path: '/video-history',
    name: 'VideoHistory',
    component: VideoHistoryView,
    meta: {
      requiresAuth: true,
      title: '视频分析历史'
    }
  },
  {
    path: '/video-status/:taskId',
    name: 'VideoStatus',
    component: VideoUploadView,
    meta: {
      requiresAuth: true,
      title: '视频处理状态'
    }
  },
  {
    path: '/video-result/:id',
    name: 'VideoResult',
    component: VideoResultView,
    meta: {
      requiresAuth: true,
      title: '视频分析结果'
    }
  },
  {
    path: '/video-result/smart/:id',
    name: 'VideoResultSmart',
    component: VideoResultView,
    meta: {
      requiresAuth: true,
      title: '视频分析结果(智能ID)'
    }
  },
  {
    path: '/video-result/id/:id',
    name: 'VideoResultId',
    component: VideoResultView,
    meta: {
      requiresAuth: true,
      title: '视频分析结果'
    },
    props: (route) => ({
      idType: 'auto'
    })
  },
  {
    path: '/video-result/uuid/:id',
    name: 'VideoResultUuid',
    component: VideoResultView,
    meta: {
      requiresAuth: true,
      title: '视频分析结果(UUID格式)'
    },
    props: (route) => ({
      idType: 'uuid'
    })
  },
  {
    path: '/video-result/mongodb/:id',
    name: 'VideoResultMongodb',
    component: VideoResultView,
    meta: {
      requiresAuth: true,
      title: '视频分析结果(MongoDB格式)'
    },
    props: (route) => ({
      idType: 'mongodb'
    })
  },
  // 添加错误路由
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: lazyLoad('Error'),
    meta: { requiresAuth: false }
  }
];

// 添加路由组件预加载函数
const preloadRouteComponents = () => {
  // 预加载关键组件
  setTimeout(() => {
    import('@/views/Home.vue');
    import('@/views/Upload.vue');
  }, 2000);
};

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
});

// 导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('auth_token');
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin);
  const isAuthPage = to.path === '/login' || to.path === '/register';

  // 验证令牌格式是否有效（简单验证非空且长度合适）
  const isValidToken = token && token.length > 20;

  if (requiresAuth && !isValidToken) {
    // 令牌无效时清除所有认证信息
    if (token) {
      console.warn('令牌无效或格式不正确，清除认证状态');
      localStorage.removeItem('auth_token');
      localStorage.removeItem('user');
      localStorage.removeItem('user_id');
    }
    
    // 需要认证但未登录或令牌无效，重定向到登录页
    next({ path: '/login', query: { redirect: to.fullPath } });
  } else if (requiresAdmin) {
    // 检查管理员权限
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      const role = (user.role || '').toLowerCase();
      const isAdmin = role === 'admin' || role === 'administrator';
      
      if (!isAdmin) {
        // 不是管理员，重定向到首页
        console.warn('用户尝试访问管理员页面，但没有管理员权限');
        next('/home');
        return;
      }
    } else {
      // 用户信息不存在，重定向到登录页
      next({ path: '/login', query: { redirect: to.fullPath } });
      return;
    }
    
    next(); // 有管理员权限，正常访问
  } else if (isAuthPage && isValidToken) {
    // 已登录用户访问登录/注册页，重定向到首页
    next('/home');
  } else {
    // 其他情况正常导航
    next();
  }
});

// 路由准备就绪后预加载组件
router.isReady().then(() => {
  preloadRouteComponents();
});

export default router; 