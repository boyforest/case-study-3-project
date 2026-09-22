import { useEffect, useState } from 'react'
import { Alert, Card, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { availableProducts } from '../api/product'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function ShopPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    availableProducts()
      .then(setData)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [])

  if (!data) {
    return <Spin style={{ display: 'block', marginTop: 80 }} />
  }

  if (!data.round) {
    return (
      <Alert
        type="info"
        showIcon
        message="No round is open for ordering"
        description="Ordering opens on Friday morning. Please check back later."
      />
    )
  }

  const columns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
    { title: 'Bay', dataIndex: 'bay' }
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Round {data?.round?.roundNo} — available products
        </Typography.Title>
        <Typography.Text type="secondary">
          Orders close {data?.round?.ordersCloseAt?.replace('T', ' ')} · Pickup {data?.round?.pickupDate}
        </Typography.Text>
        <Tag style={{ marginLeft: 12 }} color="green">OPEN</Tag>
      </Card>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data?.products || []} pagination={false} />
    </Space>
  )
}
