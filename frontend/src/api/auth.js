import request from '../utils/request'

export function login(data) {
  return request.post('/api/auth/login', data)
}

export function me() {
  return request.get('/api/auth/me')
}
