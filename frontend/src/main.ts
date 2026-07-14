import { createApp } from 'vue'
import App from './App.vue'
// 路由
import router from './router'
// Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element 图标
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 全局注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册路由、UI库
app.use(router)
app.use(ElementPlus)

app.mount('#app')