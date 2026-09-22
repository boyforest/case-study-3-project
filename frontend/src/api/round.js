import request from '../utils/request'

export function currentRound() {
  return request.get('/api/rounds/current')
}

export function listRounds(params) {
  return request.get('/api/rounds', { params })
}

export function createRound(data) {
  return request.post('/api/rounds', data)
}

export function closeRound(id) {
  return request.post(`/api/rounds/${id}/close`)
}

export function packRound(id) {
  return request.post(`/api/rounds/${id}/pack`)
}

export function roundTotals(id) {
  return request.get(`/api/rounds/${id}/totals`)
}

export function roundOrders(id) {
  return request.get('/api/orders', { params: { roundId: id } })
}
