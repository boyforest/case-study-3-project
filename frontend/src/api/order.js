import request from '../utils/request'

export function myOrders() {
  return request.get('/api/orders/mine')
}

export function placeOrder(data) {
  return request.put('/api/orders/mine', data)
}

export function cancelOrder() {
  return request.delete('/api/orders/mine')
}

export function ordersForRound(roundId) {
  return request.get('/api/orders', { params: { roundId } })
}

export function placeOrderForMember(data) {
  return request.post('/api/orders/for-member', data)
}
