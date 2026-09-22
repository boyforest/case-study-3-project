import { useState } from 'react'
import { App, Button, Form, Input, Modal, Space, Tag } from 'antd'
import CrudTable from '../../components/CrudTable'
import { activateMember, createMember, deactivateMember, listMembers, resetMemberPassword, updateMember } from '../../api/member'
import { getUser } from '../../utils/auth'

const filters = [
  { name: 'keyword', label: 'Keyword', placeholder: 'Name or member no.' },
  {
    name: 'status', label: 'Status', type: 'select',
    options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]
  }
]

const columns = [
  { title: 'Member no.', dataIndex: 'memberNo' },
  { title: 'Name', dataIndex: 'name' },
  { title: 'Phone', dataIndex: 'phone' },
  { title: 'Email', dataIndex: 'email' },
  { title: 'Role', dataIndex: 'role' },
  {
    title: 'Status', dataIndex: 'status',
    render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'red'}>{value}</Tag>
  }
]

const formFields = [
  { name: 'memberNo', label: 'Member number', createOnly: true, maxLength: 10, rules: [{ required: true }] },
  { name: 'name', label: 'Name', maxLength: 100, rules: [{ required: true }] },
  { name: 'phone', label: 'Phone', maxLength: 20 },
  { name: 'email', label: 'Email', maxLength: 100 },
  { name: 'address', label: 'Address', maxLength: 200 },
  { name: 'password', label: 'Initial password', createOnly: true, maxLength: 100, rules: [{ required: true }, { min: 6 }] }
]

export default function MembersPage() {
  const { message } = App.useApp()
  const [resetTarget, setResetTarget] = useState(null)
  const [form] = Form.useForm()

  async function toggleStatus(record, reload) {
    try {
      if (record.status === 'ACTIVE') {
        await deactivateMember(record.id)
        message.success('Member deactivated')
      } else {
        await activateMember(record.id)
        message.success('Member activated')
      }
      reload()
    } catch (error) {
      message.error(error.message)
    }
  }

  async function submitReset() {
    try {
      const values = await form.validateFields()
      await resetMemberPassword(resetTarget.id, values)
      message.success('Password reset')
      setResetTarget(null)
      form.resetFields()
    } catch (error) {
      if (error?.message) message.error(error.message)
    }
  }

  return (
    <>
      <CrudTable
        title="Members"
        filters={filters}
        columns={columns}
        formFields={formFields}
        listApi={listMembers}
        createApi={createMember}
        updateApi={updateMember}
        extraActions={(record, reload) => (
          <Space size="small">
            <Button size="small" disabled={record.id === getUser()?.id} onClick={() => toggleStatus(record, reload)}>
              {record.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
            </Button>
            <Button size="small" onClick={() => setResetTarget(record)}>Reset password</Button>
          </Space>
        )}
      />
      <Modal
        title={`Reset password for ${resetTarget?.name || ''}`}
        open={Boolean(resetTarget)}
        okText="Reset"
        onOk={submitReset}
        onCancel={() => { setResetTarget(null); form.resetFields() }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="password" label="New password" rules={[{ required: true }, { min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
