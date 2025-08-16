import express from 'express'
import cors from 'cors'

const app = express()
app.use(cors())
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

const PORT = 4445

// In-memory mock data
let auth = { initialized: false, key: '' }
let playersOnline = [
  {
    uuid: '00000000-0000-0000-0000-000000000001',
    username: 'Steve',
    online: true,
    health: 18,
    maxHealth: 20,
    foodLevel: 18,
    gameMode: 'survival',
    x: 100.5,
    y: 64,
    z: -20.3,
    totalPlayTimeFormatted: '1h 23m',
    experienceLevel: 8,
    dimension: 'minecraft:overworld',
    firstJoined: Date.now() - 86400 * 1000,
    lastSeen: Date.now(),
  },
  {
    uuid: '00000000-0000-0000-0000-000000000002',
    username: 'Alex',
    online: true,
    health: 20,
    maxHealth: 20,
    foodLevel: 20,
    gameMode: 'creative',
    x: -12.1,
    y: 70,
    z: 45.9,
    totalPlayTimeFormatted: '3h 10m',
    experienceLevel: 30,
    dimension: 'minecraft:overworld',
    firstJoined: Date.now() - 7 * 86400 * 1000,
    lastSeen: Date.now(),
  },
]

let playersOffline = [
  {
    uuid: '00000000-0000-0000-0000-000000000003',
    username: 'Herobrine',
    online: false,
    health: 20,
    maxHealth: 20,
    foodLevel: 20,
    gameMode: 'survival',
    x: 0,
    y: 64,
    z: 0,
    totalPlayTimeFormatted: '12h 0m',
    experienceLevel: 50,
    dimension: 'minecraft:the_nether',
    firstJoined: Date.now() - 30 * 86400 * 1000,
    lastSeen: Date.now() - 2 * 86400 * 1000,
  },
]

// Server settings mock data
let serverSettings = {
  difficulty: 'normal',
  gamemode: 'survival',
  hardcore: false,
  pvp: true,
  spawnProtection: 16,
  maxPlayers: 20,
  viewDistance: 10,
  simulationDistance: 10,
  allowFlight: false,
  allowNether: true,
  allowEnd: true,
  generateStructures: true,
  spawnAnimals: true,
  spawnMonsters: true,
  spawnNpcs: true,
  enableCommandBlock: false,
  enableQuery: false,
  enableRcon: false,
  motd: 'A Minecraft Server',
  playerIdleTimeout: 0,
  maxTickTime: 60000,
  maxWorldSize: 29999984,
  networkCompressionThreshold: 256,
  customRules: [
    'No griefing allowed',
    'Be respectful to other players',
    'No offensive builds or language'
  ]
}

const details = new Map()
for (const p of [...playersOnline, ...playersOffline]) {
  details.set(p.uuid, {
    ...p,
    note: '',
    frozen: false,
    godMode: false,
    vanished: false,
    canFly: p.gameMode === 'creative',
    isFlying: false,
    sessions: [
      { start: Date.now() - 6 * 3600 * 1000, end: Date.now() - 5 * 3600 * 1000, ip: '192.168.1.15' },
      { start: Date.now() - 3600 * 1000, end: null, ip: '192.168.1.15' },
    ],
    hotbar: [
      { displayName: 'Stone', count: 64, isEmpty: false },
      { displayName: 'Torch', count: 32, isEmpty: false },
      { displayName: 'Bread', count: 6, isEmpty: false },
      { displayName: '', count: 0, isEmpty: true },
      { displayName: '', count: 0, isEmpty: true },
      { displayName: '', count: 0, isEmpty: true },
      { displayName: '', count: 0, isEmpty: true },
      { displayName: '', count: 0, isEmpty: true },
      { displayName: '', count: 0, isEmpty: true },
    ],
    armor: [
      { displayName: 'Iron Helmet', count: 1, isEmpty: false },
      { displayName: 'Iron Chestplate', count: 1, isEmpty: false },
      { displayName: 'Iron Leggings', count: 1, isEmpty: false },
      { displayName: 'Iron Boots', count: 1, isEmpty: false },
    ],
    offhand: { displayName: 'Shield', count: 1, isEmpty: false },
  })
}

// Middleware to simulate auth
app.use((req, res, next) => {
  // Optionally check for header 'X-Auth-Key'
  next()
})

// Routes
app.get('/api/auth/status', (req, res) => {
  res.json({ initialized: auth.initialized })
})

app.post('/api/auth/init', (req, res) => {
  // For demo, always use a fixed password
  if (!auth.initialized) {
    auth = { initialized: true, key: 'demo' }
  } else if (auth.key !== 'demo') {
    auth.key = 'demo'
  }
  res.json({ ok: true, key: auth.key })
})

app.get('/api/players', (req, res) => {
  res.json({ online: playersOnline, offline: playersOffline })
})

app.get('/api/player/:uuid', (req, res) => {
  const d = details.get(req.params.uuid)
  if (!d) return res.status(404).json({ error: 'Not found' })
  res.json(d)
})

app.post('/api/action/:action', (req, res) => {
  const { action } = req.params
  const { uuid } = req.body
  const d = uuid ? details.get(uuid) : null

  // Simple stateful toggles/actions
  switch (action) {
    case 'setgamemode':
      if (d && req.body.gamemode) {
        d.gameMode = String(req.body.gamemode)
        d.canFly = d.gameMode === 'creative'
      }
      break
    case 'teleport':
      if (d) {
        d.x = Number(req.body.x)
        d.y = Number(req.body.y)
        d.z = Number(req.body.z)
      }
      break
    case 'setnote':
      if (d) d.note = String(req.body.note || '')
      break
    case 'effect':
    case 'cleareffects':
    case 'heal':
      if (d) d.health = d.maxHealth
      break
    case 'feed':
      if (d) d.foodLevel = 20
      break
    case 'kill':
      if (d) d.health = 0
      break
    case 'removehunger':
      if (d) d.foodLevel = 20
      break
    case 'clearinventory':
      if (d) {
        d.hotbar?.forEach(i => { i.isEmpty = true; i.count = 0; i.displayName = '' })
      }
      break
    case 'kick':
    case 'ban':
    case 'unban':
    case 'broadcast':
    case 'fly':
      if (d) d.isFlying = !d.isFlying
      break
    case 'vanish':
      if (d) d.vanished = !d.vanished
      break
    case 'godmode':
      if (d) d.godMode = !d.godMode
      break
    case 'freeze':
      if (d) d.frozen = String(req.body.freeze) === 'true' ? true : !d.frozen
      break
    default:
      // no-op
      break
  }

  res.json({ success: true })
})

// Server settings endpoints
app.get('/api/server/settings', (req, res) => {
  res.json(serverSettings)
})

app.post('/api/server/settings', (req, res) => {
  // Update server settings with provided data
  serverSettings = { ...serverSettings, ...req.body }
  console.log('Server settings updated:', serverSettings)
  res.json({ success: true })
})

app.listen(PORT, () => {
  console.log(`Mock API listening on http://localhost:${PORT}`)
})
