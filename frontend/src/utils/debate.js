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
  intensity,
}) {
  return {
    topic: topic.trim(),
    // sabit sağlayıcı sırasını koru
    participants: PROVIDERS.filter((provider) =>
      participants.includes(provider),
    ),
    rounds,
    synthesizer,
    intensity,
  }
}

export function createRoundEntries(participants) {
  return participants.map((provider) => ({
    provider,
    content: '',
    error: null,
    streaming: true,
    usage: null,
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
        usage: message.usage ?? null,
      })
      byRound.set(message.roundNumber, list)
    })

  const rounds = [...byRound.keys()]
    .sort((a, b) => a - b)
    .map((round) => ({ round, entries: byRound.get(round) }))

  const synthesisMessage = [...(detail.messages ?? [])]
    .reverse()
    .find((message) => message.role === 'SYNTHESIS')

  const synthesis = detail.finalAnswer
    ? {
        content: detail.finalAnswer,
        streaming: false,
        error: null,
        usage: synthesisMessage?.usage ?? null,
      }
    : null

  return { rounds, synthesis }
}
