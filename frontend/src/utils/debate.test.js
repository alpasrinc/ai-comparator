import { describe, expect, it } from 'vitest'
import {
  PROVIDERS,
  buildDebateRequest,
  createRoundEntries,
  normalizeDebateDetail,
  validateDebateForm,
} from './debate'

describe('validateDebateForm', () => {
  it('boş konuda hata verir', () => {
    expect(validateDebateForm('  ', PROVIDERS)).toMatch(/Konu/)
  })

  it('iki katılımcıdan az olunca hata verir', () => {
    expect(validateDebateForm('konu', ['OPENAI'])).toMatch(
      /en az iki katılımcı/i,
    )
  })

  it('geçerli girdide boş string döner', () => {
    expect(validateDebateForm('konu', ['OPENAI', 'GEMINI'])).toBe('')
  })
})

describe('buildDebateRequest', () => {
  it('katılımcıları sabit sağlayıcı sırasında verir ve konuyu trimler', () => {
    const payload = buildDebateRequest({
      topic: '  Kediler mi köpekler mi?  ',
      participants: ['GEMINI', 'OPENAI'],
      rounds: 3,
      synthesizer: 'OPENAI',
    })

    expect(payload).toEqual({
      topic: 'Kediler mi köpekler mi?',
      participants: ['OPENAI', 'GEMINI'],
      rounds: 3,
      synthesizer: 'OPENAI',
    })
  })
})

describe('createRoundEntries', () => {
  it('her katılımcı için streaming boş entry üretir', () => {
    expect(createRoundEntries(['OPENAI', 'GEMINI'])).toEqual([
      { provider: 'OPENAI', content: '', error: null, streaming: true },
      { provider: 'GEMINI', content: '', error: null, streaming: true },
    ])
  })
})

describe('normalizeDebateDetail', () => {
  it('detay mesajlarını turlara ve ortak cevaba çevirir', () => {
    const detail = {
      topic: 'Konu',
      participants: ['OPENAI', 'GEMINI'],
      finalAnswer: 'ortak cevap',
      messages: [
        { roundNumber: 1, provider: 'OPENAI', role: 'PARTICIPANT', content: 'o1' },
        { roundNumber: 1, provider: 'GEMINI', role: 'PARTICIPANT', content: 'g1' },
        { roundNumber: null, provider: 'OPENAI', role: 'SYNTHESIS', content: 'ortak cevap' },
      ],
    }

    const { rounds, synthesis } = normalizeDebateDetail(detail)

    expect(rounds).toHaveLength(1)
    expect(rounds[0].round).toBe(1)
    expect(rounds[0].entries.map((e) => e.provider)).toEqual([
      'OPENAI',
      'GEMINI',
    ])
    expect(synthesis).toEqual({
      content: 'ortak cevap',
      streaming: false,
      error: null,
    })
  })

  it('ortak cevap yoksa synthesis null döner', () => {
    const { synthesis } = normalizeDebateDetail({
      finalAnswer: null,
      messages: [],
    })
    expect(synthesis).toBeNull()
  })
})
