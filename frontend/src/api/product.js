import request from '../utils/request'

export function listProducts(params) {
  return request.get('/api/products', { params })
}

export function createProduct(data) {
  return request.post('/api/products', data)
}

export function updateProduct(id, data) {
  return request.put(`/api/products/${id}`, data)
}

export function withdrawProduct(id) {
  return request.post(`/api/products/${id}/withdraw`)
}

export function availableProducts() {
  return request.get('/api/products/available')
}
