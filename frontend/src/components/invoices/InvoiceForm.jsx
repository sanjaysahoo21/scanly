import Input from '../common/Input.jsx'
import Button from '../common/Button.jsx'
import { Save } from 'lucide-react'
import '../../styles/invoices.css'

function InvoiceForm({ invoice = {}, onChange, onSave, readOnly = false }) {
  const handleChange = (field) => (e) => {
    if (onChange) {
      onChange({ ...invoice, [field]: e.target.value })
    }
  }

  return (
    <div className="invoice-form animate-fade-in">
      <div className="invoice-form-section">
        <h4 className="invoice-form-section-title">Invoice Details</h4>
        <div className="invoice-form-grid">
          <Input
            label="Invoice Number"
            id="invoice-number"
            value={invoice.invoice_number || ''}
            onChange={handleChange('invoice_number')}
            disabled={readOnly}
            placeholder="e.g. INV-2026-0891"
          />
          <Input
            label="Invoice Date"
            id="invoice-date"
            type="date"
            value={invoice.invoice_date || ''}
            onChange={handleChange('invoice_date')}
            disabled={readOnly}
          />
          <Input
            label="Due Date"
            id="due-date"
            type="date"
            value={invoice.due_date || ''}
            onChange={handleChange('due_date')}
            disabled={readOnly}
          />
          <Input
            label="Currency"
            id="currency"
            value={invoice.currency || 'INR'}
            onChange={handleChange('currency')}
            disabled={readOnly}
          />
        </div>
      </div>

      <div className="invoice-form-section">
        <h4 className="invoice-form-section-title">Vendor Information</h4>
        <div className="invoice-form-grid">
          <Input
            label="Vendor Name"
            id="vendor-name"
            value={invoice.vendor_name || ''}
            onChange={handleChange('vendor_name')}
            disabled={readOnly}
            placeholder="Vendor name"
          />
          <Input
            label="Vendor GSTIN"
            id="vendor-gstin"
            value={invoice.vendor_gstin || ''}
            onChange={handleChange('vendor_gstin')}
            disabled={readOnly}
            placeholder="GSTIN"
          />
        </div>
        <Input
          label="Vendor Address"
          id="vendor-address"
          value={invoice.vendor_address || ''}
          onChange={handleChange('vendor_address')}
          disabled={readOnly}
          placeholder="Full address"
          className="invoice-form-full-width"
        />
      </div>

      <div className="invoice-form-section">
        <h4 className="invoice-form-section-title">Buyer Information</h4>
        <div className="invoice-form-grid">
          <Input
            label="Buyer Name"
            id="buyer-name"
            value={invoice.buyer_name || ''}
            onChange={handleChange('buyer_name')}
            disabled={readOnly}
            placeholder="Buyer name"
          />
          <Input
            label="Buyer GSTIN"
            id="buyer-gstin"
            value={invoice.buyer_gstin || ''}
            onChange={handleChange('buyer_gstin')}
            disabled={readOnly}
            placeholder="GSTIN"
          />
        </div>
        <Input
          label="Buyer Address"
          id="buyer-address"
          value={invoice.buyer_address || ''}
          onChange={handleChange('buyer_address')}
          disabled={readOnly}
          placeholder="Full address"
          className="invoice-form-full-width"
        />
      </div>

      <div className="invoice-form-section">
        <h4 className="invoice-form-section-title">Amounts</h4>
        <div className="invoice-form-grid">
          <Input
            label="Subtotal"
            id="subtotal"
            type="number"
            value={invoice.subtotal || ''}
            onChange={handleChange('subtotal')}
            disabled={readOnly}
            placeholder="0.00"
          />
          <Input
            label="Tax Amount"
            id="tax-amount"
            type="number"
            value={invoice.tax_amount || ''}
            onChange={handleChange('tax_amount')}
            disabled={readOnly}
            placeholder="0.00"
          />
          <Input
            label="Discount"
            id="discount"
            type="number"
            value={invoice.discount_amount || ''}
            onChange={handleChange('discount_amount')}
            disabled={readOnly}
            placeholder="0.00"
          />
          <Input
            label="Total Amount"
            id="total-amount"
            type="number"
            value={invoice.total_amount || ''}
            onChange={handleChange('total_amount')}
            disabled={readOnly}
            placeholder="0.00"
          />
        </div>
      </div>

      {!readOnly && onSave && (
        <div className="invoice-form-actions">
          <Button variant="primary" icon={Save} onClick={onSave} id="save-button">
            Save Changes
          </Button>
        </div>
      )}
    </div>
  )
}

export default InvoiceForm
