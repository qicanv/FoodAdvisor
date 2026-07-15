import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
})

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200 || code === 201) {
      return { success: true, data, message }
    } else if (code === 40101 || code === 40102) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return { success: false, code, message }
    }
    return { success: false, code, message, data }
  },
  error => {
    const response = error.response
    if (response) {
      const { code, message, data } = response.data
      if (code === 40101 || code === 40102) {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('userInfo')
        window.location.href = '/login'
      }
      return { success: false, code: response.status, message, data }
    }
    return { success: false, code: -1, message: '网络请求失败', data: null }
  }
)

export default request