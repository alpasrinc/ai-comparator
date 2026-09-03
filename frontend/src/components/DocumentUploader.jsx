import { useRef, useState } from 'react'

function DocumentUploader({ documents, disabled, onUpload, onDelete }) {
  const inputRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function handleChange(event) {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }

    setBusy(true)
    setError('')

    try {
      await onUpload(file)
    } catch (uploadError) {
      setError(uploadError.message)
    } finally {
      setBusy(false)
      if (inputRef.current) {
        inputRef.current.value = ''
      }
    }
  }

  async function handleDelete(documentId) {
    setError('')

    try {
      await onDelete(documentId)
    } catch (deleteError) {
      setError(deleteError.message)
    }
  }

  return (
    <div className="document-uploader">
      <button
        type="button"
        className="document-uploader__button"
        disabled={disabled || busy}
        onClick={() => inputRef.current?.click()}
      >
        {busy ? 'Yükleniyor…' : 'Dosya ekle'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.txt,.md,application/pdf,text/plain,text/markdown"
        hidden
        onChange={handleChange}
      />

      {error && <p className="document-uploader__error">{error}</p>}

      <ul className="document-uploader__list">
        {documents.map((document) => (
          <li key={document.id} className="document-uploader__chip">
            <span>{document.filename}</span>
            <span className="document-uploader__count">
              {document.chunkCount} parça
            </span>
            <button
              type="button"
              aria-label={`${document.filename} belgesini sil`}
              onClick={() => handleDelete(document.id)}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default DocumentUploader
