import request from '../utils/request'

export function listMembers(params) {
  return request.get('/api/members', { params })
}

export function createMember(data) {
  return request.post('/api/members', data)
}

export function updateMember(id, data) {
  return request.put(`/api/members/${id}`, data)
}

export function deactivateMember(id) {
  return request.post(`/api/members/${id}/deactivate`)
}

export function activateMember(id) {
  return request.post(`/api/members/${id}/activate`)
}

export function resetMemberPassword(id, data) {
  return request.post(`/api/members/${id}/reset-password`, data)
}
