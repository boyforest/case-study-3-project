import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Col, InputNumber, Row, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { availableProducts } from '../api/product'
import { myOrders, placeOrder } from '../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function ShopPage() {
  const [data, setData] = useState(null)
  const [quantities, setQuantities] = useState({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState(null)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    setLoadError(null)
    try {
      const [available, orders] = await Promise.all([availableProducts(), myOrders()])
      setData(available)
      const current = (orders || []).find(o => o.status === 'ACTIVE' && o.roundId === available?.round?.id)
      setQuantities(current
        ? Object.fromEntries(current.lines.map(l => [l.productId, Number(l.quantity)]))
        : {})
    } catch (error) {
      setLoadError(error.message)
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  const lines = useMemo(() => {
    if (!data?.products) return []
    return data.products
      .filter(p => quantities[p.id] > 0)
      .map(p => ({
        productId: p.id,
        name: p.name,
        unitType: p.unitType,
        quantity: quantities[p.id],
        unitPrice: Number(p.price),
        lineTotal: Number((quantities[p.id] * Number(p.price)).toFixed(2))
      }))
  }, [data, quantities])

  const total = lines.reduce((sum, l) => sum + l.lineTotal, 0)

  async function save() {
    if (lines.length === 0) {
      message.warning('Add at least one product to your order')
      return
    }
    setSaving(true)
    try {
      await placeOrder({ lines: lines.map(l => ({ productId: l.productId, quantity: l.quantity })) })
      message.success('Order saved')
      load()
    } catch (error) {
      message.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  if (loadError) {
    return <Alert type="error" showIcon message="Could not load the shop" description={loadError} />
  }

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

  const productColumns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
    { title: 'Bay', dataIndex: 'bay' },
    {
      title: 'Quantity', key: 'quantity', width: 140,
      render: (_, record) => (
        <InputNumber
          min={0}
          step={record.unitType === 'PER_UNIT' ? 1 : 0.25}
          precision={record.unitType === 'PER_UNIT' ? 0 : 3}
          value={quantities[record.id]}
          onChange={value => setQuantities(prev => ({ ...prev, [record.id]: value }))}
        />
      )
    },
    {
      title: 'Line total', key: 'lineTotal', width: 110,
      render: (_, record) => quantities[record.id] > 0
        ? (quantities[record.id] * Number(record.price)).toFixed(2)
        : '—'
    }
  ]

  const basketColumns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Qty', dataIndex: 'quantity' },
    { title: 'Total', dataIndex: 'lineTotal', render: v => v.toFixed(2) }
  ]

  return (
    <Row gutter={16}>
      <Col span={16}>
        <Card
          title={`Round ${data?.round?.roundNo} — available products`}
          extra={<Tag color="green">OPEN</Tag>}
        >
          <Typography.Paragraph type="secondary">
            Orders close {data?.round?.ordersCloseAt?.replace('T', ' ')} · Pickup {data?.round?.pickupDate}
          </Typography.Paragraph>
          <Table rowKey="id" loading={loading} columns={productColumns}
                 dataSource={data?.products || []} pagination={false} />
        </Card>
      </Col>
      <Col span={8}>
        <Card
          title="Your order"
          extra={<Button type="primary" loading={saving} onClick={save}>Save order</Button>}
        >
          <Table rowKey="productId" columns={basketColumns} dataSource={lines}
                 pagination={false} size="small" locale={{ emptyText: 'Add products on the left' }} />
          <Typography.Title level={5} style={{ textAlign: 'right', marginTop: 16 }}>
            Total: ${total.toFixed(2)}
          </Typography.Title>
        </Card>
      </Col>
    </Row>
  )
}
