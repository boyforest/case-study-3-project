import axios from 'axios'
import { clearAuth, getToken } from './auth'

const request = axios.create({ baseURL: '', timeout: 15000 })

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const data = response.data
    if (data && data.code !== 0 && data.code !== 200) {
      const error = new Error(data.message || `Error ${data.code}`)
      error.code = data.code
      return Promise.reject(error)
    }
    return data?.data
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || 'Request failed'
    if (status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(new Error(message))
  }
)

export default request
