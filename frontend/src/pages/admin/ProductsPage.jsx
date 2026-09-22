import { App, Button, Tag } from 'antd'
import CrudTable from '../../components/CrudTable'
import { createProduct, listProducts, updateProduct, withdrawProduct } from '../../api/product'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

const filters = [
  { name: 'keyword', label: 'Keyword', placeholder: 'Product name' },
  {
    name: 'status', label: 'Status', type: 'select',
    options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'WITHDRAWN', label: 'Withdrawn' }]
  }
]

const columns = [
  { title: 'Name', dataIndex: 'name' },
  { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
  { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
  { title: 'Bay', dataIndex: 'bay' },
  {
    title: 'Status', dataIndex: 'status',
    render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'orange'}>{value}</Tag>
  }
]

const formFields = [
  { name: 'name', label: 'Name', maxLength: 100, rules: [{ required: true }] },
  {
    name: 'unitType', label: 'Sold as', type: 'select', rules: [{ required: true }],
    options: [{ value: 'PER_UNIT', label: 'Per unit (each)' }, { value: 'PER_KG', label: 'Per kilogram' }]
  },
  { name: 'price', label: 'Price (AUD)', type: 'number', precision: 2, rules: [{ required: true }] },
  { name: 'bay', label: 'Bay (e.g. B1, VEG)', maxLength: 10 }
]

export default function ProductsPage() {
  const { message, modal } = App.useApp()

  function confirmWithdraw(record, reload) {
    modal.confirm({
      title: `Withdraw "${record.name}"?`,
      content: 'It will no longer be orderable. Existing orders are not affected.',
      okText: 'Withdraw',
      onOk: async () => {
        try {
          await withdrawProduct(record.id)
          message.success('Product withdrawn')
          reload()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }

  return (
    <CrudTable
      title="Products"
      filters={filters}
      columns={columns}
      formFields={formFields}
      listApi={listProducts}
      createApi={createProduct}
      updateApi={updateProduct}
      extraActions={(record, reload) => record.status === 'ACTIVE' && (
        <Button size="small" onClick={() => confirmWithdraw(record, reload)}>Withdraw</Button>
      )}
    />
  )
}
