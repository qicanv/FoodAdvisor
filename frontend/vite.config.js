import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const backendTarget =
    env.VITE_API_BASE_URL || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
    ],

    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
      preserveSymlinks: true,
    },

    server: {
      port: 5173,

      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      pool: 'threads',
      maxWorkers: 1,
      fileParallelism: false,
    },
  }
})
