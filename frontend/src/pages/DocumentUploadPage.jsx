import FileUploader from '../components/documents/FileUploader.jsx'

function DocumentUploadPage() {
  const handleUpload = (files) => {
    // TODO: Wire to document upload API
    console.log('Uploading files:', files)
  }

  return (
    <div className="page-content">
      <div className="page-header animate-fade-in-up">
        <h1>Upload Documents</h1>
        <p>Upload invoices, receipts, or scanned documents for AI-powered data extraction.</p>
      </div>

      <div className="animate-fade-in-up stagger-1">
        <FileUploader onUpload={handleUpload} />
      </div>
    </div>
  )
}

export default DocumentUploadPage
