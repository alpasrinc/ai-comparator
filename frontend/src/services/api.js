const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const DEFAULT_TIMEOUT_MS = 35_000

async function request(path, options = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(
    () => controller.abort(),
    DEFAULT_TIMEOUT_MS,
  )

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      signal: controller.signal,
    })

    if (!response.ok) {
      const errorBody = await response.json().catch(() => null)
      const message =
        errorBody?.detail ??
        errorBody?.message ??
        `İstek başarısız oldu: HTTP ${response.status}`

      throw new Error(message)
    }

    return response.json()
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('İstek zaman aşımına uğradı. Tekrar deneyin.', {
        cause: error,
      })
    }

    if (error instanceof TypeError) {
      throw new Error('Backend bağlantısı kurulamadı.', { cause: error })
    }

    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export function getHealth() {
  return request('/api/health')
}

export function compareMessage(message, conversationId = null) {
  return request('/api/chat/compare', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId,
      message,
    }),
  })
}

export function selectActiveMessage(conversationId, messageId) {
  return request(
    `/api/conversations/${conversationId}/active-message`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ messageId }),
    },
  )
}

export function getConversations() {
  return request('/api/conversations')
}

export function getConversation(conversationId) {
  return request(`/api/conversations/${conversationId}`)
}

export function retryProvider(conversationId, userMessageId, provider) {
  return request('/api/chat/retry', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId,
      userMessageId,
      provider,
    }),
  })
}
