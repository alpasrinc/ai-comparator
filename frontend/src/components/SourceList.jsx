function SourceList({ sources, unavailable }) {
  if (unavailable) {
    return (
      <div className="source-list source-list--warning" role="status">
        Kaynaklar getirilemedi; cevaplar belgesiz üretildi.
      </div>
    )
  }

  if (!sources || sources.length === 0) {
    return null
  }

  return (
    <details className="source-list">
      <summary className="source-list__summary">
        Kaynaklar ({sources.length})
      </summary>
      <ul className="source-list__items">
        {sources.map((source, index) => (
          <li key={source.chunkId} className="source-list__item">
            <div className="source-list__meta">
              <span className="source-list__badge">[{index + 1}]</span>
              <span className="source-list__file">{source.filename}</span>
              <span className="source-list__chunk">
                parça {source.chunkIndex}
              </span>
              <span className="source-list__score">
                %{Math.round(source.similarity * 100)}
              </span>
            </div>
            <p className="source-list__excerpt">
              {source.content.slice(0, 200)}
              {source.content.length > 200 ? '…' : ''}
            </p>
          </li>
        ))}
      </ul>
    </details>
  )
}

export default SourceList
