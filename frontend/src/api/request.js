import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

export const buildLoginRedirect = (pathname, search = '') =>
  `/diner?redirect=${encodeURIComponent(`${pathname}${search}`)}`

const redirectToLogin = () => window.location.assign(
  buildLoginRedirect(window.location.pathname, window.location.search)
)

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const userInfo = localStorage.getItem('user') || localStorage.getItem('userInfo')
    if (userInfo) {
      try {
        const user = JSON.parse(userInfo)
        if (user.id) {
          config.headers['X-User-Id'] = user.id
        }
      } catch (e) {
        console.log('解析用户信息失败')
      }
    }

    const userId = localStorage.getItem('userId')
    if (userId && !config.headers['X-User-Id']) {
      config.headers['X-User-Id'] = userId
    }

    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    console.log('API响应:', response.config.url, response.status, response.data)
    const { code, message, data } = response.data
    if (code === 'SUCCESS' || code === 200 || code === 201) {
      return { success: true, data, message }
    } else if (code === 'UNAUTHORIZED' || code === 40101 || code === 40102) {
      console.error('API未授权:', response.config.url)
      localStorage.removeItem('token')
      localStorage.removeItem('accessToken')
      localStorage.removeItem('user')
      localStorage.removeItem('userInfo')
      redirectToLogin()
      return { success: false, code, message }
    }
    console.warn('API返回错误:', response.config.url, code, message)
    return { success: false, code, message, data }
  },
  error => {
    console.error('API请求失败:', error.config ? error.config.url : '未知', error.message)
    if (error.response) {
      console.error('HTTP状态码:', error.response.status)
      console.error('响应体:', error.response.data)
      const { code, message, data } = error.response.data || {}
      if (code === 40101 || code === 40102) {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('userInfo')
        redirectToLogin()
      }
      return { success: false, code: error.response.status, message: message || error.message, data }
    } else if (error.request) {
      console.error('请求已发送但无响应:', error.request)
      return { success: false, code: -1, message: '请求超时或网络连接失败', data: null }
    } else {
      console.error('请求配置错误:', error.message)
      return { success: false, code: -2, message: '请求配置错误: ' + error.message, data: null }
    }
  }
)

export default request
