import Input from '../common/Input.jsx'
import Button from '../common/Button.jsx'
import { Save } from 'lucide-react'
import '../../styles/invoices.css'

function InvoiceForm({ invoice = {}, onChange, onSave, readOnly = false, saving = false }) {
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
            value={invoice.invoiceNumber || ''}
            onChange={handleChange('invoiceNumber')}
            disabled={readOnly}
            placeholder="e.g. INV-2026-0891"
          />
          <Input
            label="Invoice Date"
            id="invoice-date"
            type="date"
            value={invoice.invoiceDate || ''}
            onChange={handleChange('invoiceDate')}
            disabled={readOnly}
          />
          <Input
            label="Due Date"
            id="due-date"
            type="date"
            value={invoice.dueDate || ''}
            onChange={handleChange('dueDate')}
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
            value={invoice.vendorName || ''}
            onChange={handleChange('vendorName')}
            disabled={readOnly}
            placeholder="Vendor name"
          />
          <Input
            label="Vendor GSTIN"
            id="vendor-gstin"
            value={invoice.vendorGstin || ''}
            onChange={handleChange('vendorGstin')}
            disabled={readOnly}
            placeholder="GSTIN"
          />
        </div>
        <Input
          label="Vendor Address"
          id="vendor-address"
          value={invoice.vendorAddress || ''}
          onChange={handleChange('vendorAddress')}
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
            value={invoice.buyerName || ''}
            onChange={handleChange('buyerName')}
            disabled={readOnly}
            placeholder="Buyer name"
          />
          <Input
            label="Buyer GSTIN"
            id="buyer-gstin"
            value={invoice.buyerGstin || ''}
            onChange={handleChange('buyerGstin')}
            disabled={readOnly}
            placeholder="GSTIN"
          />
        </div>
        <Input
          label="Buyer Address"
          id="buyer-address"
          value={invoice.buyerAddress || ''}
          onChange={handleChange('buyerAddress')}
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
            value={invoice.taxAmount || ''}
            onChange={handleChange('taxAmount')}
            disabled={readOnly}
            placeholder="0.00"
          />
          <Input
            label="Discount"
            id="discount"
            type="number"
            value={invoice.discountAmount || ''}
            onChange={handleChange('discountAmount')}
            disabled={readOnly}
            placeholder="0.00"
          />
          <Input
            label="Total Amount"
            id="total-amount"
            type="number"
            value={invoice.totalAmount || ''}
            onChange={handleChange('totalAmount')}
            disabled={readOnly}
            placeholder="0.00"
          />
        </div>
      </div>

      {!readOnly && onSave && (
        <div className="invoice-form-actions">
          <Button variant="primary" icon={Save} onClick={onSave} id="save-button" disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      )}
    </div>
  )
}

export default InvoiceForm
