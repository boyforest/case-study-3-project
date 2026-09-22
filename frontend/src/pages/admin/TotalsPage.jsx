import { useEffect, useState } from 'react'
import { Card, Select, Space, Table, Typography, message } from 'antd'
import { listRounds, roundTotals } from '../../api/round'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function TotalsPage() {
  const [rounds, setRounds] = useState([])
  const [roundId, setRoundId] = useState(null)
  const [totals, setTotals] = useState(null)
  const [loading, setLoading] = useState(false)

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
    setLoading(true)
    roundTotals(roundId)
      .then(setTotals)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [roundId])

  const columns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Total quantity', dataIndex: 'totalQuantity', render: v => Number(v) },
    { title: 'Total amount (AUD)', dataIndex: 'totalAmount', render: v => Number(v).toFixed(2) }
  ]

  return (
    <Card
      title="Round totals by product"
      extra={(
        <Space>
          <Typography.Text type="secondary">Use this to place the wholesale order</Typography.Text>
          <Select
            style={{ minWidth: 200 }}
            value={roundId}
            onChange={setRoundId}
            options={rounds.map(r => ({ value: r.id, label: `Round ${r.roundNo} (${r.status})` }))}
          />
        </Space>
      )}
    >
      <Table
        rowKey="productId"
        loading={loading}
        columns={columns}
        dataSource={totals?.rows || []}
        pagination={false}
        locale={{ emptyText: 'No orders in this round' }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}><strong>Total</strong></Table.Summary.Cell>
            <Table.Summary.Cell index={1} />
            <Table.Summary.Cell index={2} />
            <Table.Summary.Cell index={3}>
              <strong>{Number(totals?.totalAmount || 0).toFixed(2)}</strong>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Card>
  )
}
