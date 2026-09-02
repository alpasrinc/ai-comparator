const formatter = new Intl.NumberFormat('tr-TR')

function TokenUsageBadge({ usage }) {
  if (!usage) {
    return null
  }

  const input = usage.inputTokens ?? 0
  const output = usage.outputTokens ?? 0
  const cacheRead = usage.cacheReadTokens ?? 0
  const cacheWrite = usage.cacheWriteTokens ?? 0
  const total = input + output + cacheRead + cacheWrite

  if (total <= 0) {
    return null
  }

  // Prompt caching devredeyken "giriş" yalnızca önbelleğe düşmeyen kalandır.
  const title = cacheRead > 0
    ? 'Bu yanıtta harcanan token sayısı. Giriş, önbellekten okunmayan kalan '
      + 'kısımdır; toplam giriş + önbellek + çıkıştır.'
    : 'Bu yanıtta harcanan token sayısı'

  return (
    <div className="token-usage" title={title}>
      <span className="token-usage__icon" aria-hidden="true">
        ◱
      </span>
      <span className="token-usage__part">
        {formatter.format(input)} <em>giriş</em>
      </span>
      {cacheRead > 0 && (
        <>
          <span className="token-usage__sep" aria-hidden="true">
            ·
          </span>
          <span className="token-usage__part token-usage__part--cache">
            {formatter.format(cacheRead)} <em>önbellek</em>
          </span>
        </>
      )}
      <span className="token-usage__sep" aria-hidden="true">
        ·
      </span>
      <span className="token-usage__part">
        {formatter.format(output)} <em>çıkış</em>
      </span>
      <span className="token-usage__sep" aria-hidden="true">
        ·
      </span>
      <span className="token-usage__part token-usage__part--total">
        {formatter.format(total)} <em>toplam</em>
      </span>
    </div>
  )
}

export default TokenUsageBadge
