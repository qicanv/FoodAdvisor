import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 5000,
})

request.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

export default request