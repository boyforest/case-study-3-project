import { App, Button, Form, InputNumber, Modal, Space, Tag, Table, Typography, DatePicker } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { closeRound, createRound, listRounds, packRound } from '../../api/round'

const statusColors = { OPEN: 'green', CLOSED: 'blue', PACKED: 'default' }

export default function RoundsPage() {
  const { message, modal } = App.useApp()
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const data = await listRounds({ page: 1, size: 50 })
      setRecords(data?.records || [])
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  async function submitCreate() {
    try {
      const values = await form.validateFields()
      await createRound({
        roundNo: values.roundNo,
        ordersOpenAt: values.ordersOpenAt.format('YYYY-MM-DDTHH:mm:ss'),
        ordersCloseAt: values.ordersCloseAt.format('YYYY-MM-DDTHH:mm:ss'),
        pickupDate: values.pickupDate.format('YYYY-MM-DD')
      })
      message.success('Round opened')
      setCreating(false)
      form.resetFields()
      load()
    } catch (error) {
      if (error?.message) message.error(error.message)
    }
  }

  async function doTransition(id, action, label) {
    modal.confirm({
      title: `${label} this round?`,
      okText: label,
      onOk: async () => {
        try {
          await action(id)
          message.success(`Round ${label.toLowerCase()}ed`)
          load()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }

  const columns = [
    { title: 'Round', dataIndex: 'roundNo' },
    { title: 'Opens', dataIndex: 'ordersOpenAt', render: v => v?.replace('T', ' ') },
    { title: 'Closes', dataIndex: 'ordersCloseAt', render: v => v?.replace('T', ' ') },
    { title: 'Pickup', dataIndex: 'pickupDate' },
    { title: 'Status', dataIndex: 'status', render: v => <Tag color={statusColors[v]}>{v}</Tag> },
    {
      title: 'Actions', key: 'actions',
      render: (_, record) => (
        <Space>
          {record.status === 'OPEN' && (
            <Button size="small" onClick={() => doTransition(record.id, closeRound, 'Close')}>Close</Button>
          )}
          {record.status === 'CLOSED' && (
            <Button size="small" onClick={() => doTransition(record.id, packRound, 'Pack')}>Mark packed</Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Rounds</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Open new round</Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={records} pagination={false} />
      <Modal
        title="Open new round"
        open={creating} okText="Open" onOk={submitCreate}
        onCancel={() => setCreating(false)} destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="roundNo" label="Round number" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="ordersOpenAt" label="Orders open" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="ordersCloseAt" label="Orders close" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="pickupDate" label="Pickup date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
