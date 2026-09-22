import { useState } from 'react'
import { Button, Card, Form, Input, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { setAuth } from '../utils/auth'

export default function LoginPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  async function onFinish(values) {
    setLoading(true)
    try {
      const data = await login(values)
      setAuth(data.token, data.member)
      navigate(data.member.role === 'COORDINATOR' ? '/admin/orders' : '/shop')
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}>
      <Card style={{ width: 380 }}>
        <Typography.Title level={4} style={{ textAlign: 'center' }}>Greenhill Food Co-op</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          Weekly grocery ordering
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="memberNo" label="Member number" rules={[{ required: true }]}>
            <Input placeholder="M-094" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>Sign in</Button>
        </Form>
      </Card>
    </div>
  )
}
