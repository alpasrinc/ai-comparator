export const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'GEMINI']

export function validateDebateForm(topic, participants) {
  if (!topic || !topic.trim()) {
    return 'Konu boş olamaz.'
  }
  if (participants.length < 2) {
    return 'En az iki katılımcı seçilmeli.'
  }
  return ''
}

export function buildDebateRequest({
  topic,
  participants,
  rounds,
  synthesizer,
}) {
  return {
    topic: topic.trim(),
    // sabit sağlayıcı sırasını koru
    participants: PROVIDERS.filter((provider) =>
      participants.includes(provider),
    ),
    rounds,
    synthesizer,
  }
}

export function createRoundEntries(participants) {
  return participants.map((provider) => ({
    provider,
    content: '',
    error: null,
    streaming: true,
  }))
}

export function normalizeDebateDetail(detail) {
  const byRound = new Map()

  ;(detail.messages ?? [])
    .filter((message) => message.role === 'PARTICIPANT')
    .forEach((message) => {
      const list = byRound.get(message.roundNumber) ?? []
      list.push({
        provider: message.provider,
        content: message.content,
        error: null,
        streaming: false,
      })
      byRound.set(message.roundNumber, list)
    })

  const rounds = [...byRound.keys()]
    .sort((a, b) => a - b)
    .map((round) => ({ round, entries: byRound.get(round) }))

  const synthesis = detail.finalAnswer
    ? { content: detail.finalAnswer, streaming: false, error: null }
    : null

  return { rounds, synthesis }
}
