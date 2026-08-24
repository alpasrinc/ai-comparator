const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, options)

  if (!response.ok) {
    throw new Error(`İstek başarısız oldu: HTTP ${response.status}`)
  }

  return response.json()
}

export function getHealth() {
  return request('/api/health')
}

export function compareMessage(message) {
  return request('/api/chat/compare', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message }),
  })
}