import { useEffect, useState } from 'react'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Table, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'

export default function CrudTable({
  title, filters = [], columns = [], formFields = [], rowKey = 'id',
  listApi, createApi, updateApi, deleteApi, extraActions, toolbarExtra
}) {
  const { message, modal } = App.useApp()
  const [filterForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [records, setRecords] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)

  const visibleFields = editing?.[rowKey] ? formFields.filter(f => !f.createOnly) : formFields

  const tableColumns = [
    ...columns,
    {
      title: 'Actions', key: 'actions', fixed: 'right', width: 240,
      render: (_, record) => (
        <Space size="small">
          {updateApi && <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>Edit</Button>}
          {deleteApi && <Button size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(record)}>Delete</Button>}
          {extraActions?.(record, reload)}
        </Space>
      )
    }
  ]

  useEffect(() => { loadData(1, pagination.pageSize) }, [])

  async function loadData(page = pagination.current, size = pagination.pageSize) {
    setLoading(true)
    try {
      const params = { page, size, ...clean(filterForm.getFieldsValue()) }
      const data = await listApi(params)
      const list = Array.isArray(data) ? data : data?.records || []
      setRecords(list)
      setPagination({
        current: Number(data?.current || page),
        pageSize: Number(data?.size || size),
        total: Number(data?.total || list.length)
      })
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  function reload() { loadData(pagination.current, pagination.pageSize) }

  function openCreate() { setEditing({}); editForm.resetFields() }

  function openEdit(record) {
    setEditing(record)
    editForm.setFieldsValue(toFormValues(record, formFields))
  }

  async function save() {
    setSaving(true)
    try {
      const values = fromFormValues(await editForm.validateFields(), formFields)
      if (editing?.[rowKey]) {
        await updateApi(editing[rowKey], clean(values))
        message.success('Updated')
      } else {
        await createApi(clean(values))
        message.success('Created')
      }
      setEditing(null)
      reload()
    } catch (error) {
      if (error?.message) message.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  function confirmDelete(record) {
    modal.confirm({
      title: `Delete ${record[rowKey]}?`,
      okText: 'Delete', okButtonProps: { danger: true }, cancelText: 'Cancel',
      onOk: async () => {
        try {
          await deleteApi(record[rowKey])
          message.success('Deleted')
          reload()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>{title}</Typography.Title>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Form form={filterForm} layout="inline" onFinish={() => loadData(1, pagination.pageSize)}>
          {filters.map(field => (
            <Form.Item key={field.name} name={field.name} label={field.label}>
              <FieldControl field={field} filter />
            </Form.Item>
          ))}
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>Search</Button>
              <Button onClick={() => { filterForm.resetFields(); loadData(1, pagination.pageSize) }}>Reset</Button>
              <Button icon={<ReloadOutlined />} onClick={reload} />
            </Space>
          </Form.Item>
        </Form>
        <Space>
          {toolbarExtra?.({ reload })}
          {createApi && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>Create</Button>}
        </Space>
      </Space>
      <Table
        rowKey={rowKey} loading={loading} columns={tableColumns} dataSource={records}
        scroll={{ x: 'max-content' }}
        pagination={{ ...pagination, showSizeChanger: true, showTotal: total => `${total} items` }}
        onChange={next => loadData(next.current, next.pageSize)}
      />
      <Modal
        title={`${editing?.[rowKey] ? 'Edit' : 'Create'} ${title}`}
        open={Boolean(editing)} okText="Save" cancelText="Cancel"
        confirmLoading={saving} onOk={save} onCancel={() => setEditing(null)} destroyOnHidden
      >
        <Form form={editForm} layout="vertical">
          {visibleFields.map(field => (
            <Form.Item key={field.name} name={field.name} label={field.label} rules={field.rules || []}>
              <FieldControl field={field} />
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </div>
  )
}

function FieldControl({ field, filter }) {
  if (field.type === 'select') {
    return <Select allowClear={filter} placeholder={field.placeholder || 'Select'} options={field.options || []} style={{ minWidth: 160 }} />
  }
  if (field.type === 'datetime') {
    return <DatePicker showTime placeholder={field.placeholder || 'Select date/time'} style={{ minWidth: 200 }} />
  }
  if (field.type === 'date') {
    return <DatePicker placeholder={field.placeholder || 'Select date'} style={{ minWidth: 160 }} />
  }
  if (field.type === 'number') {
    return <InputNumber min={0} precision={field.precision} placeholder={field.placeholder} style={{ minWidth: 160 }} />
  }
  return <Input maxLength={field.maxLength} placeholder={field.placeholder} style={{ minWidth: 180 }} />
}

function clean(object) {
  return Object.fromEntries(Object.entries(object || {}).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

function toFormValues(record, fields) {
  const values = { ...record }
  fields.forEach(field => {
    if ((field.type === 'datetime' || field.type === 'date') && record?.[field.name]) {
      values[field.name] = dayjs(record[field.name])
    }
  })
  return values
}

function fromFormValues(values, fields) {
  const next = { ...values }
  fields.forEach(field => {
    if ((field.type === 'datetime' || field.type === 'date') && values?.[field.name]) {
      next[field.name] = values[field.name].format(field.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss')
    }
  })
  return next
}
