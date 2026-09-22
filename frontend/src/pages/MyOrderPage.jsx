import { useEffect, useState } from 'react'
import { Card, Table, Tag, Typography, message } from 'antd'
import { myOrders } from '../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function MyOrderPage() {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    myOrders()
      .then(setOrders)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [])

  const columns = [
    { title: 'Round', dataIndex: 'roundNo' },
    {
      title: 'Status', dataIndex: 'status',
      render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'red'}>{value}</Tag>
    },
    { title: 'Total (AUD)', dataIndex: 'total', render: v => Number(v).toFixed(2) },
    { title: 'Placed', dataIndex: 'createdAt', render: v => v?.replace('T', ' ') }
  ]

  const lineColumns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Quantity', dataIndex: 'quantity', render: v => Number(v) },
    { title: 'Unit price', dataIndex: 'unitPrice', render: v => Number(v).toFixed(2) },
    { title: 'Line total', dataIndex: 'lineTotal', render: v => Number(v).toFixed(2) }
  ]

  return (
    <Card title="My orders">
      <Typography.Paragraph type="secondary">
        Orders can only be changed while the round is open for ordering.
      </Typography.Paragraph>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={orders}
        pagination={false}
        expandable={{
          expandedRowRender: order => (
            <Table rowKey="id" columns={lineColumns} dataSource={order.lines}
                   pagination={false} size="small" />
          )
        }}
      />
    </Card>
  )
}
