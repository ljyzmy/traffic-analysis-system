const { defineConfig } = require('@vue/cli-service')
const webpack = require('webpack')

module.exports = defineConfig({
  transpileDependencies: true,
  
  // 开发服务器配置
  devServer: {
    port: 5173,
    // 配置代理，解决开发过程中的跨域问题
    proxy: process.env.VUE_APP_ENABLE_PROXY !== 'false' ? {
      '/api/media/video': {
        target: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080', // 后端服务器地址
        changeOrigin: true,
        secure: false,
        // 处理代理时的错误
        onError: (err, req, res) => {
          console.log('媒体代理错误:', err);
          res.writeHead(500, {
            'Content-Type': 'text/plain'
          });
          res.end('媒体代理请求错误: ' + err.message);
        }
      },
      '/api': {
        target: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080', // 后端服务器地址
        changeOrigin: true,
        pathRewrite: {
          '^/api': '/api' // 保持api路径
        },
        // 处理代理时的错误
        onError: (err, req, res) => {
          console.log('代理错误:', err);
          
          // 如果无法连接到端口8080，添加更多提示信息
          if (err.code === 'ECONNREFUSED') {
            console.log(`无法连接到后端服务器，请确保后端服务已启动并且监听${process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080'}`);
            res.writeHead(504, {
              'Content-Type': 'application/json'
            });
            res.end(JSON.stringify({
              status: 'error',
              message: '无法连接到后端服务器，请确保后端服务已启动'
            }));
          } else {
            res.writeHead(500, {
              'Content-Type': 'text/plain'
            });
            res.end('代理请求错误: ' + err.message);
          }
        },
        // 关闭HTTPS验证
        secure: false,
        // 尝试重连次数
        retry: 3
      },
      '/auth': {
        target: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        pathRewrite: {
          '^/auth': '/api/auth'
        }
      }
    } : undefined,
    // 禁用与ResizeObserver循环相关的客户端覆盖层错误
    client: {
      overlay: {
        // 自定义错误过滤器
        runtimeErrors: (error) => {
          if (error.message && error.message.includes('ResizeObserver loop')) {
            // 过滤ResizeObserver循环错误
            return false;
          }
          // 显示其他运行时错误
          return true;
        }
      }
    },
    // 添加缓存控制
    headers: {
      'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0',
      'Surrogate-Control': 'no-store'
    }
  },
  
  // 构建配置
  configureWebpack: {
    // 添加外部依赖配置，减小打包体积
    externals: process.env.NODE_ENV === 'production' ? {
      'echarts': 'echarts',
      'bootstrap': 'bootstrap'
    } : {},
    
    // 解决某些依赖项的警告
    resolve: {
      fallback: {
        path: require.resolve('path-browserify')
      }
    },
    plugins: [
      // 定义 Vue 特性标志
      new webpack.DefinePlugin({
        __VUE_PROD_DEVTOOLS__: false,
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false
      })
    ],
    
    // 禁用缓存设置
    cache: false,
    
    // 优化分块策略
    optimization: {
      runtimeChunk: 'single',
      splitChunks: {
        chunks: 'all',
        maxInitialRequests: Infinity,
        minSize: 20000,
        cacheGroups: {
          vendor: {
            test: /[\\/]node_modules[\\/]/,
            name(module) {
              const packageName = module.context.match(/[\\/]node_modules[\\/](.*?)([\\/]|$)/)[1];
              return `npm.${packageName.replace('@', '')}`;
            }
          }
        }
      }
    },
    
    // 输出配置
    output: {
      filename: '[name].[hash].js',
      chunkFilename: '[name].[hash].js'
    }
  },
  
  // 禁用eslint检查
  lintOnSave: false,
  
  // 生产环境配置
  productionSourceMap: false
})
