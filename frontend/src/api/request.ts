import axios from 'axios'
const service = axios.create({
  baseURL: '/api',
  timeout: 10000
})

service.interceptors.request.use(config => config)
service.interceptors.response.use(res => res.data, err => Promise.reject(err))
export default service