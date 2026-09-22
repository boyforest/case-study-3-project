import { Layout, Menu, Button, Space, Typography, App as AntApp } from 'antd'
import {
  ShopOutlined, ProfileOutlined, TeamOutlined, TagsOutlined,
  CalendarOutlined, UnorderedListOutlined, PieChartOutlined, LogoutOutlined
} from '@ant-design/icons'
import { useLocation, useNavigate, Outlet } from 'react-router-dom'
import { clearAuth, getUser } from '../utils/auth'

const memberItems = [
  { key: '/shop', icon: <ShopOutlined />, label: 'Place order' },
  { key: '/my-order', icon: <ProfileOutlined />, label: 'My orders' }
]

const adminItems = [
  { key: '/admin/members', icon: <TeamOutlined />, label: 'Members' },
  { key: '/admin/products', icon: <TagsOutlined />, label: 'Products' },
  { key: '/admin/rounds', icon: <CalendarOutlined />, label: 'Rounds' },
  { key: '/admin/orders', icon: <UnorderedListOutlined />, label: 'Round orders' },
  { key: '/admin/totals', icon: <PieChartOutlined />, label: 'Round totals' }
]

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getUser()
  const items = user?.role === 'COORDINATOR' ? [...memberItems, ...adminItems] : memberItems

  return (
    <AntApp>
      <Layout style={{ minHeight: '100vh' }}>
        <Layout.Sider theme="light" width={230}>
          <div style={{ padding: 16 }}>
            <Typography.Title level={5} style={{ margin: 0 }}>Greenhill Food Co-op</Typography.Title>
            <Typography.Text type="secondary">{user?.name} ({user?.memberNo})</Typography.Text>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={items}
            onClick={({ key }) => navigate(key)}
          />
        </Layout.Sider>
        <Layout>
          <Layout.Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
            <Space>
              <Typography.Text>{user?.role === 'COORDINATOR' ? 'Coordinator' : 'Member'}</Typography.Text>
              <Button icon={<LogoutOutlined />} onClick={() => { clearAuth(); navigate('/login') }}>
                Sign out
              </Button>
            </Space>
          </Layout.Header>
          <Layout.Content style={{ padding: 24 }}>
            <Outlet />
          </Layout.Content>
        </Layout>
      </Layout>
    </AntApp>
  )
}
