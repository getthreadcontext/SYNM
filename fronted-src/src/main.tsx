import React from 'react'
import ReactDOM from 'react-dom/client'
import { MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import '@mantine/core/styles.css'
import '@mantine/notifications/styles.css'
import App from './ui/App'

// API base helper for auth bootstrap too
const API_BASE = typeof window !== 'undefined' && window.location.protocol === 'file:'
  ? 'http://localhost:4444'
  : ''
// Demo mode flag from Vite env
const DEMO = (import.meta as any).env?.VITE_DEMO === '1' || (import.meta as any).env?.VITE_DEMO === 'true'

// Simple auth bootstrapping: if API key not initialized, allow user to set a custom key or auto-generate.
async function ensureAuthKey() {
  try {
    if (DEMO) {
      // In demo, just set the cookie to a known value and skip prompts
      document.cookie = `synm_key=demo; path=/; SameSite=Lax`
      return
    }
    const status = await fetch(`${API_BASE}/api/auth/status`).then(r => r.json()).catch(() => ({ initialized: false }))
    const cookies = Object.fromEntries(document.cookie.split(';').map(c => c.trim()).filter(Boolean).map(c => c.split('=')))
    if (!status.initialized) {
      // Ask user if they want to set their own key
      const userKey = window.prompt('Set a SynM API key (leave empty to auto-generate):')?.trim()
      let res: Response
      if (userKey) {
        const body = new URLSearchParams({ key: userKey })
        res = await fetch(`${API_BASE}/api/auth/init`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString() })
      } else {
        res = await fetch(`${API_BASE}/api/auth/init`, { method: 'POST' })
      }
      if (res.ok) {
        const data = await res.json()
        const key = data.key || userKey || ''
        if (key) document.cookie = `synm_key=${key}; path=/; SameSite=Lax`
      }
    } else {
      // If initialized but no cookie, prompt user for key once
      if (!cookies['synm_key']) {
        const k = window.prompt('Enter SynM API key (found in synm_api_key.txt):')
        if (k) document.cookie = `synm_key=${k}; path=/; SameSite=Lax`
      }
    }
  } catch {}
}

ensureAuthKey().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <MantineProvider defaultColorScheme="dark">
        <Notifications position="top-right" />
        <App />
      </MantineProvider>
    </React.StrictMode>
  )
})
