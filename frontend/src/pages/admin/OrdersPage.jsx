import { useEffect, useState } from 'react'
import { App, Button, Card, Select, Space, Table, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listRounds } from '../../api/round'
import { ordersForRound } from '../../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function OrdersPage() {
  const { message } = App.useApp()
  const [rounds, setRounds] = useState([])
  const [roundId, setRoundId] = useState(null)
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    listRounds({ page: 1, size: 50 })
      .then(data => {
        const records = data?.records || []
        setRounds(records)
        if (records.length > 0) setRoundId(records[0].id)
      })
      .catch(error => message.error(error.message))
  }, [])

  useEffect(() => {
    if (!roundId) return
    let ignore = false
    setLoading(true)
    setOrders([])
    ordersForRound(roundId)
      .then(data => { if (!ignore) setOrders(data) })
      .catch(error => { if (!ignore) message.error(error.message) })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }
  }, [roundId, reloadKey])

  const orderColumns = [
    { title: 'Member', key: 'member', render: (_, r) => `${r.memberNo} — ${r.memberName}` },
    { title: 'Status', dataIndex: 'status', render: v => <Tag color="green">{v}</Tag> },
    { title: 'Lines', key: 'lines', render: (_, r) => r.lines.length },
    { title: 'Total (AUD)', dataIndex: 'total', render: v => Number(v).toFixed(2) }
  ]

  const lineColumns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Quantity', dataIndex: 'quantity', render: v => Number(v) },
    { title: 'Unit price', dataIndex: 'unitPrice', render: v => Number(v).toFixed(2) },
    { title: 'Line total', dataIndex: 'lineTotal', render: v => Number(v).toFixed(2) }
  ]

  const selectedRound = rounds.find(r => r.id === roundId)

  return (
    <Card
      title="Round orders"
      extra={(
        <Space>
          <Typography.Text type="secondary">
            {selectedRound ? `${selectedRound.status} · pickup ${selectedRound.pickupDate}` : ''}
          </Typography.Text>
          <Select
            style={{ minWidth: 200 }}
            placeholder="Select a round"
            value={roundId}
            onChange={setRoundId}
            options={rounds.map(r => ({ value: r.id, label: `Round ${r.roundNo} (${r.status})` }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => setReloadKey(k => k + 1)} />
        </Space>
      )}
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={orderColumns}
        dataSource={orders}
        pagination={false}
        locale={{ emptyText: 'No orders in this round' }}
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
