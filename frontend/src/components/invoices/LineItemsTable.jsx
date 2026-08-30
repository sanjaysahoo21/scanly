import { Plus, Trash2, Package } from 'lucide-react'
import Button from '../common/Button.jsx'
import '../../styles/invoices.css'

function LineItemsTable({ items = [], onChange, readOnly = false }) {
  const handleItemChange = (index, field, value) => {
    if (onChange) {
      const updated = items.map((item, i) =>
        i === index ? { ...item, [field]: value } : item
      )
      onChange(updated)
    }
  }

  const addItem = () => {
    if (onChange) {
      onChange([
        ...items,
        { description: '', hsnCode: '', quantity: 1, unitPrice: 0, taxRate: 0, totalPrice: 0 },
      ])
    }
  }

  const removeItem = (index) => {
    if (onChange) {
      onChange(items.filter((_, i) => i !== index))
    }
  }

  if (items.length === 0 && readOnly) {
    return (
      <div className="table-container">
        <div className="empty-state" style={{ padding: 'var(--space-8)' }}>
          <Package className="empty-state-icon" />
          <h3>No line items</h3>
          <p>No line items were extracted for this invoice.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="line-items-section">
      <div className="line-items-header">
        <h4>Line Items</h4>
        {!readOnly && (
          <Button variant="ghost" size="sm" icon={Plus} onClick={addItem}>
            Add Item
          </Button>
        )}
      </div>

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>Description</th>
              <th>HSN Code</th>
              <th>Qty</th>
              <th>Unit Price</th>
              <th>Tax %</th>
              <th>Total</th>
              {!readOnly && <th></th>}
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => (
              <tr key={index}>
                <td>
                  <input
                    className="line-item-input line-item-desc"
                    value={item.description || ''}
                    onChange={(e) => handleItemChange(index, 'description', e.target.value)}
                    disabled={readOnly}
                    placeholder="Item description"
                  />
                </td>
                <td>
                  <input
                    className="line-item-input line-item-sm"
                    value={item.hsnCode || ''}
                    onChange={(e) => handleItemChange(index, 'hsnCode', e.target.value)}
                    disabled={readOnly}
                    placeholder="HSN"
                  />
                </td>
                <td>
                  <input
                    className="line-item-input line-item-num"
                    type="number"
                    value={item.quantity || ''}
                    onChange={(e) => handleItemChange(index, 'quantity', e.target.value)}
                    disabled={readOnly}
                  />
                </td>
                <td>
                  <input
                    className="line-item-input line-item-num"
                    type="number"
                    value={item.unitPrice || ''}
                    onChange={(e) => handleItemChange(index, 'unitPrice', e.target.value)}
                    disabled={readOnly}
                  />
                </td>
                <td>
                  <input
                    className="line-item-input line-item-num"
                    type="number"
                    value={item.taxRate || ''}
                    onChange={(e) => handleItemChange(index, 'taxRate', e.target.value)}
                    disabled={readOnly}
                  />
                </td>
                <td>
                  <input
                    className="line-item-input line-item-num"
                    type="number"
                    value={item.totalPrice || ''}
                    onChange={(e) => handleItemChange(index, 'totalPrice', e.target.value)}
                    disabled={readOnly}
                  />
                </td>
                {!readOnly && (
                  <td>
                    <button
                      className="line-item-remove"
                      onClick={() => removeItem(index)}
                      aria-label="Remove item"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default LineItemsTable
