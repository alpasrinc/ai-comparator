import { useEffect, useState } from 'react'
import CompareView from './components/CompareView'
import DebateView from './components/DebateView'
import { getHealth } from './services/api'
import './App.css'

function App() {
  const [activeTab, setActiveTab] = useState('compare')
  const [backendStatus, setBackendStatus] = useState('Kontrol ediliyor...')
  const [backendError, setBackendError] = useState('')

  useEffect(() => {
    getHealth()
      .then((data) => setBackendStatus(data.status))
      .catch((requestError) => {
        setBackendError(requestError.message)
        setBackendStatus('Bağlantı kurulamadı')
      })
  }, [])

  return (
    <div className="app-shell">
      <nav className="tab-bar" aria-label="Ana sekmeler">
        <button
          type="button"
          className={`tab ${activeTab === 'compare' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('compare')}
        >
          Karşılaştırma
        </button>
        <button
          type="button"
          className={`tab ${activeTab === 'debate' ? 'tab--active' : ''}`}
          onClick={() => setActiveTab('debate')}
        >
          Münazara
        </button>
      </nav>

      {activeTab === 'compare' ? (
        <CompareView
          backendStatus={backendStatus}
          backendError={backendError}
        />
      ) : (
        <DebateView />
      )}
    </div>
  )
}

export default App
