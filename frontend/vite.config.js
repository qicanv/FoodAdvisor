import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [
    vue()
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
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})