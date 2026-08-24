import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [status, setStatus] = useState('Kontrol ediliyor...')
  const [error, setError] = useState('')

  useEffect(() => {
    fetch('http://localhost:8080/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        return response.json()
      })
      .then((data) => {
        setStatus(data.status)
      })
      .catch((requestError) => {
        setError(requestError.message)
        setStatus('Bağlantı kurulamadı')
      })
  }, [])

  return (
    <main>
      <h1>AI Comparator</h1>
      <p>React → Spring Boot bağlantı testi</p>

      <section>
        <strong>Backend durumu:</strong> {status}

        {error && (
          <p>
            Hata: {error}
          </p>
        )}
      </section>
    </main>
  )
}

export default App