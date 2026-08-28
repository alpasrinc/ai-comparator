const formatter = new Intl.NumberFormat('tr-TR')

function TokenUsageBadge({ usage }) {
  if (!usage) {
    return null
  }

  const input = usage.inputTokens ?? 0
  const output = usage.outputTokens ?? 0
  const total = input + output

  if (total <= 0) {
    return null
  }

  return (
    <div className="token-usage" title="Bu yanıtta harcanan token sayısı">
      <span className="token-usage__icon" aria-hidden="true">
        ◱
      </span>
      <span className="token-usage__part">
        {formatter.format(input)} <em>giriş</em>
      </span>
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
