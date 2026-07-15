import request from './request'

export const login = (username, password) => {
  return request.post('/api/auth/login', {
    username,
    password
  })
}

export const register = (username, password, nickname) => {
  return request.post('/api/auth/register', {
    username,
    password,
    nickname
  })
}

export const getCurrentUser = () => {
  return request.get('/api/auth/me')
}

export const logout = () => {
  return request.post('/api/auth/logout')
}