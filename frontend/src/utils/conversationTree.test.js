import { describe, expect, test } from 'vitest'
import { buildConversationTree } from './conversationTree'

function userMessage(id, parentMessageId, content, createdAt) {
  return {
    id,
    parentMessageId,
    role: 'USER',
    provider: null,
    content,
    createdAt,
  }
}

function assistantMessage(id, parentMessageId, provider, content, createdAt) {
  return {
    id,
    parentMessageId,
    role: 'ASSISTANT',
    provider,
    content,
    createdAt,
  }
}

describe('buildConversationTree', () => {
  test('boş mesaj listesinde çökmeden root: null döner', () => {
    const result = buildConversationTree([], null)

    expect(result.root).toBeNull()
    expect(result.activePath.size).toBe(0)
  })

  test('activeMessageId null olsa da mevcut mesajlardan ağaç kurar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'Cevap', '2026-01-01T10:00:01Z'),
    ]

    const result = buildConversationTree(messages, null)

    expect(result.root).not.toBeNull()
    expect(result.root.message.id).toBe(1)
    expect(result.activePath.size).toBe(0)
  })

  test('tek turluk ağacı doğru kurar (1 USER + 3 ASSISTANT çocuk)', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'OpenAI cevabı', '2026-01-01T10:00:01Z'),
      assistantMessage(3, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:02Z'),
      assistantMessage(4, 1, 'GEMINI', 'Gemini cevabı', '2026-01-01T10:00:03Z'),
    ]

    const result = buildConversationTree(messages, 3)

    expect(result.root.message.id).toBe(1)
    expect(result.root.children).toHaveLength(3)
    expect(result.root.children.map((child) => child.message.id)).toEqual([
      2, 3, 4,
    ])
  })

  test('aktif yolu kökten aktif mesaja kadar doğru hesaplar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:01Z'),
      userMessage(3, 2, 'Örnek verir misin?', '2026-01-01T10:01:00Z'),
      assistantMessage(4, 3, 'OPENAI', 'Örnek cevap', '2026-01-01T10:01:01Z'),
    ]

    const result = buildConversationTree(messages, 4)

    expect(Array.from(result.activePath).sort()).toEqual([1, 2, 3, 4])
  })

  test('aynı asistan mesajından birden fazla kullanıcı dalını (gerçek dallanma) doğru kurar', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(2, 1, 'OPENAI', 'OpenAI cevabı', '2026-01-01T10:00:01Z'),
      assistantMessage(3, 1, 'ANTHROPIC', 'Claude cevabı', '2026-01-01T10:00:02Z'),
      userMessage(4, 2, 'İlk dal sorusu', '2026-01-01T10:01:00Z'),
      assistantMessage(5, 4, 'GEMINI', 'İlk dal cevabı', '2026-01-01T10:01:01Z'),
      userMessage(6, 2, 'İkinci dal sorusu', '2026-01-01T10:02:00Z'),
      assistantMessage(7, 6, 'OPENAI', 'İkinci dal cevabı', '2026-01-01T10:02:01Z'),
    ]

    const result = buildConversationTree(messages, 7)

    const openAiChild = result.root.children.find(
      (child) => child.message.id === 2,
    )

    expect(openAiChild.children.map((child) => child.message.id)).toEqual([
      4, 6,
    ])
    expect(Array.from(result.activePath).sort()).toEqual([1, 2, 6, 7])
  })

  test('retry sonucu aynı sağlayıcıdan gelen eski cevabı ağaçtan eler', () => {
    const messages = [
      userMessage(1, null, 'Java nedir?', '2026-01-01T10:00:00Z'),
      assistantMessage(
        2,
        1,
        'ANTHROPIC',
        'İlk (hatalı) cevap',
        '2026-01-01T10:00:01Z',
      ),
      assistantMessage(
        3,
        1,
        'ANTHROPIC',
        'Retry sonrası cevap',
        '2026-01-01T10:00:05Z',
      ),
    ]

    const result = buildConversationTree(messages, 3)

    expect(result.root.children).toHaveLength(1)
    expect(result.root.children[0].message.id).toBe(3)
  })
})
