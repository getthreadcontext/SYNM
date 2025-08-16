import { useEffect, useMemo, useState } from 'react'
import {
  AppShell,
  Group,
  Title,
  Button,
  Stack,
  Text,
  ScrollArea,
  Tabs,
  Card,
  Badge,
  Progress,
  NumberInput,
  TextInput,
  Textarea,
  Select,
  Table,
  Divider,
  Grid,
  Container,
  NavLink,
} from '@mantine/core'
import { notifications } from '@mantine/notifications'
import ServerSettings from './ServerSettings'

// Minimal shapes matching backend JSON
interface PlayerRow {
  uuid: string
  username: string
  online: boolean
  health: number
  maxHealth: number
  foodLevel: number
  gameMode: string
  x: number
  y: number
  z: number
  totalPlayTimeFormatted?: string
  experienceLevel?: number
  dimension?: string
  firstJoined?: number
  lastSeen?: number
}

interface PlayerDetail extends PlayerRow {
  note?: string
  frozen?: boolean
  godMode?: boolean
  vanished?: boolean
  canFly?: boolean
  isFlying?: boolean
  sessions?: Array<{ start: number; end?: number; ip?: string }>
  hotbar?: Array<{ displayName: string; count: number; isEmpty: boolean }>
  armor?: Array<{ displayName: string; count: number; isEmpty: boolean }>
  offhand?: { displayName: string; count: number; isEmpty: boolean }
}

const EFFECT_OPTIONS = [
  { value: 'speed', label: 'Speed' },
  { value: 'slowness', label: 'Slowness' },
  { value: 'haste', label: 'Haste' },
  { value: 'mining_fatigue', label: 'Mining Fatigue' },
  { value: 'strength', label: 'Strength' },
  { value: 'instant_health', label: 'Instant Health' },
  { value: 'instant_damage', label: 'Instant Damage' },
  { value: 'jump_boost', label: 'Jump Boost' },
  { value: 'nausea', label: 'Nausea' },
  { value: 'regeneration', label: 'Regeneration' },
  { value: 'resistance', label: 'Resistance' },
  { value: 'fire_resistance', label: 'Fire Resistance' },
  { value: 'water_breathing', label: 'Water Breathing' },
  { value: 'invisibility', label: 'Invisibility' },
  { value: 'blindness', label: 'Blindness' },
  { value: 'night_vision', label: 'Night Vision' },
  { value: 'hunger', label: 'Hunger' },
  { value: 'weakness', label: 'Weakness' },
  { value: 'poison', label: 'Poison' },
  { value: 'wither', label: 'Wither' },
  { value: 'health_boost', label: 'Health Boost' },
  { value: 'absorption', label: 'Absorption' },
  { value: 'saturation', label: 'Saturation' },
  { value: 'glowing', label: 'Glowing' },
  { value: 'levitation', label: 'Levitation' },
  { value: 'luck', label: 'Luck' },
  { value: 'unluck', label: 'Bad Luck' },
  { value: 'slow_falling', label: 'Slow Falling' },
  { value: 'conduit_power', label: 'Conduit Power' },
  { value: 'dolphins_grace', label: "Dolphin's Grace" },
  { value: 'bad_omen', label: 'Bad Omen' },
  { value: 'hero_of_the_village', label: 'Hero of the Village' },
]

// API base helper: if opened via file://, fallback to localhost:4444
const API_BASE = typeof window !== 'undefined' && window.location.protocol === 'file:'
  ? 'http://localhost:4444'
  : ''
const DEMO = (import.meta as any).env?.VITE_DEMO === '1' || (import.meta as any).env?.VITE_DEMO === 'true'

const getAuthKey = () => {
  try {
    const map = Object.fromEntries(document.cookie.split(';').map(c => c.trim()).filter(Boolean).map(c => c.split('=')))
    return map['synm_key'] || ''
  } catch { return '' }
}

const apiFetch = async (path: string, init?: RequestInit): Promise<Response> => {
  const doFetch = async (): Promise<Response> => {
    const headers: Record<string, string> = { ...(init?.headers as any) }
    const key = DEMO ? 'demo' : getAuthKey()
    if (key) headers['X-Auth-Key'] = key
    return fetch(`${API_BASE}${path}`, { ...init, headers })
  }
  let res = await doFetch()
  if (res.status === 401 && !DEMO) {
    // Prompt for key again and retry once
    const k = window.prompt('Unauthorized. Enter SynM API key (see synm_api_key.txt):')
    if (k) {
      document.cookie = `synm_key=${k}; path=/; SameSite=Lax`
      res = await doFetch()
    }
  }
  return res
}

function encodeForm(data: Record<string, string>): string {
  return Object.entries(data)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
}

async function postAction(action: string, params: Record<string, string>): Promise<boolean> {
  const res = await apiFetch(`/api/action/${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: encodeForm(params),
  })
  if (!res.ok) return false
  try {
    const j = await res.json()
    return !!j?.success
  } catch {
    return true
  }
}

export default function App() {
  // Navigation state
  const [currentPage, setCurrentPage] = useState<'players' | 'settings'>('players')
  
  // Players list
  const [online, setOnline] = useState<PlayerRow[]>([])
  const [offline, setOffline] = useState<PlayerRow[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [detail, setDetail] = useState<PlayerDetail | null>(null)

  // Action form states
  const [gm, setGm] = useState<string | null>(null)
  const [tp, setTp] = useState<{ x: number | ''; y: number | ''; z: number | ''}>({ x: '', y: '', z: '' })
  const [kickReason, setKickReason] = useState('Kicked by admin')
  const [note, setNote] = useState('')
  const [effect, setEffect] = useState<string | null>('speed')
  const [duration, setDuration] = useState<number | ''>(60)
  const [amplifier, setAmplifier] = useState<number | ''>(0)
  const [broadcast, setBroadcast] = useState('')
  const [banReason, setBanReason] = useState('Banned by admin')

  // Fetch players list
  const loadPlayers = async () => {
    try {
      const res = await apiFetch('/api/players')
      if (!res.ok) throw new Error(String(res.status))
      const data = await res.json()
      setOnline(data.online || [])
      setOffline(data.offline || [])
    } catch (e) {
      notifications.show({ color: 'red', title: 'API error', message: 'Failed to load players' })
    }
  }

  // Fetch selected player details
  const loadDetail = async (uuid: string) => {
    try {
      const res = await apiFetch(`/api/player/${uuid}`)
      if (!res.ok) throw new Error(String(res.status))
      const data = await res.json()
      setDetail(data)
      setNote(data.note || '')
      // Prefill teleport with current coords
      setTp({ x: Number(data.x?.toFixed?.(2) ?? data.x ?? ''), y: Number(data.y?.toFixed?.(2) ?? data.y ?? ''), z: Number(data.z?.toFixed?.(2) ?? data.z ?? '') })
      setGm(data.gameMode?.toLowerCase?.() ?? null)
    } catch (e) {
      notifications.show({ color: 'red', title: 'API error', message: 'Failed to load player details' })
    }
  }

  // Interval refresh
  useEffect(() => {
    loadPlayers()
    const id = setInterval(loadPlayers, 5000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (selected) loadDetail(selected)
  }, [selected])

  // UI handlers
  const act = async (action: string, extra: Record<string, string> = {}) => {
    if (!selected) return
    const ok = await postAction(action, { uuid: selected, ...extra })
    if (ok) {
      notifications.show({ color: 'green', message: `${action} OK` })
      loadPlayers()
      if (selected) loadDetail(selected)
    } else {
      notifications.show({ color: 'red', message: `${action} failed` })
    }
  }

  const setGameMode = async () => {
    if (!gm || !selected) return
    await act('setgamemode', { gamemode: gm })
  }

  const doTeleport = async () => {
    if (!selected) return
    const { x, y, z } = tp
    if (x === '' || y === '' || z === '') return
    await act('teleport', { x: String(x), y: String(y), z: String(z) })
  }

  const saveNote = async () => {
    if (!selected) return
    await act('setnote', { note })
  }

  const applyEffect = async () => {
    if (!selected || !effect) return
    await act('effect', {
      effect,
      duration: String(duration || 60),
      amplifier: String(amplifier || 0),
    })
  }

  const clearEffects = async () => act('cleareffects')
  const sendBroadcast = async () => {
    await postAction('broadcast', { message: broadcast })
    notifications.show({ color: 'green', message: 'Broadcast sent' })
    setBroadcast('')
  }
  const doBan = async () => act('ban', { reason: banReason })
  const doUnban = async () => act('unban')
  const toggleFreeze = async () => act('freeze', { freeze: String(!(detail?.frozen ?? false)) })
  const toggleGod = async () => act('godmode')
  const toggleFly = async () => act('fly')
  const toggleVanish = async () => act('vanish')

  const selectedPlayerName = useMemo(() => {
    const p = [...online, ...offline].find(p => p.uuid === selected)
    return p?.username ?? '—'
  }, [selected, online, offline])

  return (
    <AppShell header={{ height: 56 }} padding="md" navbar={{ width: 360, breakpoint: 'sm' }}>
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Title order={3}>SynM Admin</Title>
          <Group>
            <Button variant="light" onClick={loadPlayers}>Refresh</Button>
          </Group>
        </Group>
      </AppShell.Header>

      {/* Sidebar: Navigation + Players */}
      <AppShell.Navbar p="xs">
        <Stack gap="sm">
          {/* Navigation */}
          <Card withBorder p="xs">
            <Stack gap="xs">
              <NavLink
                label="Player Management"
                active={currentPage === 'players'}
                onClick={() => setCurrentPage('players')}
              />
              <NavLink
                label="Server Settings"
                active={currentPage === 'settings'}
                onClick={() => setCurrentPage('settings')}
              />
            </Stack>
          </Card>

          {/* Players list - only show on players page */}
          {currentPage === 'players' && (
            <Tabs defaultValue="online">
              <Tabs.List>
                <Tabs.Tab value="online">Online ({online.length})</Tabs.Tab>
                <Tabs.Tab value="offline">Offline ({offline.length})</Tabs.Tab>
              </Tabs.List>

              <Tabs.Panel value="online">
                <ScrollArea h="calc(100vh - 260px)">
                  <Stack gap="xs" mt="sm">
                    {online.map((p) => (
                      <Card key={p.uuid} withBorder radius="md" onClick={() => setSelected(p.uuid)} style={{ cursor: 'pointer', borderColor: selected === p.uuid ? 'var(--mantine-color-blue-5)' : undefined }}>
                        <Group justify="space-between" align="center">
                          <Group>
                            <Badge color="green" variant="filled" radius="xs" mr="xs">●</Badge>
                            <Text fw={600}>{p.username}</Text>
                          </Group>
                          <Badge variant="light">{p.gameMode}</Badge>
                        </Group>
                        <Group mt={6} gap="sm">
                          <Text size="sm">HP</Text>
                          <Progress value={Math.min(100, (p.health / (p.maxHealth || 20)) * 100)} w={160} />
                          <Text size="sm">🍗 {p.foodLevel}</Text>
                        </Group>
                      </Card>
                    ))}
                    {online.length === 0 && <Text c="dimmed" ta="center" mt="sm">No players online</Text>}
                  </Stack>
                </ScrollArea>
              </Tabs.Panel>

              <Tabs.Panel value="offline">
                <ScrollArea h="calc(100vh - 260px)">
                  <Stack gap="xs" mt="sm">
                    {offline.map((p) => (
                      <Card key={p.uuid} withBorder radius="md" onClick={() => setSelected(p.uuid)} style={{ cursor: 'pointer', borderColor: selected === p.uuid ? 'var(--mantine-color-blue-5)' : undefined }}>
                        <Group justify="space-between" align="center">
                          <Group>
                            <Badge color="gray" variant="filled" radius="xs" mr="xs">●</Badge>
                            <Text fw={600}>{p.username}</Text>
                          </Group>
                          <Badge variant="light">last seen</Badge>
                        </Group>
                        <Text size="xs" c="dimmed" mt={6}>Playtime: {p.totalPlayTimeFormatted || '—'}</Text>
                      </Card>
                    ))}
                    {offline.length === 0 && <Text c="dimmed" ta="center" mt="sm">No offline players</Text>}
                  </Stack>
                </ScrollArea>
              </Tabs.Panel>
            </Tabs>
          )}
        </Stack>
      </AppShell.Navbar>

      {/* Main content */}
      <AppShell.Main>
        {currentPage === 'settings' && <ServerSettings />}
        
        {currentPage === 'players' && !selected && (
          <Container size="lg">
            <Card withBorder radius="md" p="lg">
              <Title order={4}>Select a player</Title>
              <Text c="dimmed" mt="sm">Choose a player on the left to manage.</Text>
            </Card>
          </Container>
        )}

        {currentPage === 'players' && selected && (
          <Container size="lg">
            <Stack gap="md">
              <Group align="center" justify="space-between">
                <Group>
                  <img src={`https://mc-heads.net/avatar/${encodeURIComponent(selectedPlayerName)}/32.png`} width={32} height={32} style={{ borderRadius: 4 }} />
                  <Title order={3}>{selectedPlayerName}</Title>
                  {detail?.online ? <Badge color="green">Online</Badge> : <Badge>Offline</Badge>}
                </Group>
                <Group>
                  <Badge variant="light">GM: {detail?.gameMode}</Badge>
                  {detail?.frozen && <Badge color="red" variant="filled">Frozen</Badge>}
                  {detail?.godMode && <Badge color="yellow" variant="filled">God</Badge>}
                  {detail?.vanished && <Badge color="grape" variant="filled">Vanished</Badge>}
                  {detail?.canFly && <Badge color="blue" variant="filled">Can Fly</Badge>}
                </Group>
              </Group>

              {/* Teleport + Effects combined */}
              <Card withBorder radius="md" p="md">
                <Grid gutter="md">
                  <Grid.Col span={{ base: 12, md: 6 }}>
                    <Title order={5}>Teleport</Title>
                    <Group mt="xs">
                      <NumberInput label="X" value={tp.x} onChange={(v) => setTp(s => ({ ...s, x: (typeof v === 'number' ? v : '') }))} w={120} min={-30000000} max={30000000} />
                      <NumberInput label="Y" value={tp.y} onChange={(v) => setTp(s => ({ ...s, y: (typeof v === 'number' ? v : '') }))} w={120} min={-64} max={400} />
                      <NumberInput label="Z" value={tp.z} onChange={(v) => setTp(s => ({ ...s, z: (typeof v === 'number' ? v : '') }))} w={120} min={-30000000} max={30000000} />
                      <Button onClick={doTeleport}>Teleport</Button>
                    </Group>
                  </Grid.Col>
                  <Grid.Col span={{ base: 12, md: 6 }}>
                    <Title order={5}>Potion Effects</Title>
                    <Group mt="xs" align="end" wrap="wrap">
                      <Select label="Effect" data={EFFECT_OPTIONS} value={effect} onChange={setEffect} w={200} />
                      <NumberInput label="Duration (s)" value={duration} onChange={(v) => setDuration(typeof v === 'number' ? v : '')} min={1} w={160} />
                      <NumberInput label="Amplifier" value={amplifier} onChange={(v) => setAmplifier(typeof v === 'number' ? v : '')} min={0} w={140} />
                      <Button onClick={applyEffect}>Apply</Button>
                      <Button variant="light" onClick={clearEffects}>Clear</Button>
                    </Group>
                  </Grid.Col>
                </Grid>
              </Card>

              {/* Moderation under tabs */}
              <Card withBorder radius="md" p="md">
                <Title order={5} mb="xs">Moderation</Title>
                <Tabs defaultValue="player">
                  <Tabs.List>
                    <Tabs.Tab value="player">Player</Tabs.Tab>
                    <Tabs.Tab value="bans">Bans</Tabs.Tab>
                  </Tabs.List>

                  <Tabs.Panel value="player" pt="sm">
                    <Group align="end" wrap="wrap">
                      <TextInput label="Kick reason" value={kickReason} onChange={(e) => setKickReason(e.currentTarget.value)} w={260} />
                      <Button color="orange" onClick={() => act('kick', { reason: kickReason })}>Kick</Button>
                      <Button color="red" variant="light" onClick={toggleFreeze}>{detail?.frozen ? 'Unfreeze' : 'Freeze'}</Button>
                      <Button variant="light" onClick={toggleGod}>God</Button>
                      <Button variant="light" onClick={toggleFly}>Fly</Button>
                      <Button variant="light" onClick={toggleVanish}>Vanish</Button>
                    </Group>
                  </Tabs.Panel>

                  <Tabs.Panel value="bans" pt="sm">
                    <Group align="end" wrap="wrap">
                      <TextInput label="Reason" value={banReason} onChange={(e) => setBanReason(e.currentTarget.value)} w={260} />
                      <Button color="red" onClick={doBan}>Ban</Button>
                      <Button variant="light" onClick={doUnban}>Unban</Button>
                    </Group>
                  </Tabs.Panel>
                </Tabs>
              </Card>

              {/* Quick actions stay */}
              <Card withBorder radius="md" p="md">
                <Group wrap="wrap" gap="sm">
                  <Button onClick={() => act('heal')}>Heal</Button>
                  <Button onClick={() => act('feed')}>Feed</Button>
                  <Button color="red" onClick={() => act('kill')}>Kill</Button>
                  <Button onClick={() => act('removehunger')}>Remove Hunger</Button>
                  <Button onClick={() => act('clearinventory')}>Clear Inventory</Button>
                  <Divider orientation="vertical" mx="sm" />
                  <Select
                    placeholder="Game mode"
                    data={[
                      { value: 'survival', label: 'Survival' },
                      { value: 'creative', label: 'Creative' },
                      { value: 'adventure', label: 'Adventure' },
                      { value: 'spectator', label: 'Spectator' },
                    ]}
                    value={gm}
                    onChange={setGm}
                    w={200}
                  />
                  <Button variant="light" onClick={setGameMode}>Set GM</Button>
                </Group>
              </Card>

              {/* Broadcast only (bans moved to Moderation) */}
              <Card withBorder radius="md" p="md">
                <Title order={5}>Broadcast</Title>
                <Group mt="xs" align="end">
                  <TextInput placeholder="Message to all players" value={broadcast} onChange={(e) => setBroadcast(e.currentTarget.value)} w={480} />
                  <Button onClick={sendBroadcast}>Send</Button>
                </Group>
              </Card>

              {/* Status & About */}
              <Grid gutter="md">
                <Grid.Col span={{ base: 12, md: 7 }}>
                  <Card withBorder radius="md" p="md">
                    <Title order={5}>Status</Title>
                    <Group mt="sm" gap="md">
                      <Group>
                        <Text size="sm">Health</Text>
                        <Progress value={Math.min(100, ((detail?.health || 0) / (detail?.maxHealth || 20)) * 100)} w={200} />
                      </Group>
                      <Text size="sm">🍗 {detail?.foodLevel ?? 0}</Text>
                      <Text size="sm">Level {detail?.experienceLevel ?? 0}</Text>
                      <Text size="sm">Playtime: {detail?.totalPlayTimeFormatted || '—'}</Text>
                    </Group>
                    <Divider my="sm" />
                    <Text size="sm">Pos: X {detail?.x?.toFixed?.(1)} Y {detail?.y?.toFixed?.(1)} Z {detail?.z?.toFixed?.(1)}</Text>
                    <Text size="sm" c="dimmed">Dim: {detail?.dimension}</Text>
                  </Card>
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 5 }}>
                  <Card withBorder radius="md" p="md">
                    <Group justify="space-between" align="start">
                      <Title order={5}>About</Title>
                      <img src={`https://mc-heads.net/avatar/${encodeURIComponent(selectedPlayerName)}/48.png`} width={48} height={48} style={{ borderRadius: 8 }} />
                    </Group>
                    <Stack gap={6} mt="xs">
                      <Text size="sm"><b>Username:</b> {selectedPlayerName}</Text>
                      <Text size="sm"><b>UUID:</b> {selected}</Text>
                      <Text size="sm"><b>First joined:</b> {detail?.firstJoined ? new Date(detail.firstJoined).toLocaleString() : '—'}</Text>
                      <Text size="sm"><b>Last seen:</b> {detail?.lastSeen ? new Date(detail.lastSeen).toLocaleString() : '—'}</Text>
                      <Text size="sm"><b>Total playtime:</b> {detail?.totalPlayTimeFormatted || '—'}</Text>
                      <Text size="sm"><b>Dimension:</b> {detail?.dimension || '—'}</Text>
                      <Text size="sm"><b>Game mode:</b> {detail?.gameMode || '—'}</Text>
                    </Stack>
                    <Divider my="sm" />
                    <Title order={6}>Player notes</Title>
                    <Textarea mt="xs" minRows={4} value={note} onChange={(e) => setNote(e.currentTarget.value)} />
                    <Group mt="xs" justify="flex-end">
                      <Button size="sm" onClick={saveNote}>Save Note</Button>
                    </Group>
                  </Card>
                </Grid.Col>
              </Grid>

              {/* Recent Sessions remain */}
              <Card withBorder radius="md" p="md">
                <Title order={5}>Recent Sessions</Title>
                <Table withTableBorder mt="xs">
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Start</Table.Th>
                      <Table.Th>End</Table.Th>
                      <Table.Th>IP</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {detail?.sessions?.length ? (
                      [...detail.sessions].reverse().map((s, i) => (
                        <Table.Tr key={i}>
                          <Table.Td>{new Date(s.start).toLocaleString()}</Table.Td>
                          <Table.Td>{s.end ? new Date(s.end).toLocaleString() : '—'}</Table.Td>
                          <Table.Td>{s.ip || ''}</Table.Td>
                        </Table.Tr>
                      ))
                    ) : (
                      <Table.Tr>
                        <Table.Td colSpan={3}><Text c="dimmed">No sessions</Text></Table.Td>
                      </Table.Tr>
                    )}
                  </Table.Tbody>
                </Table>
              </Card>
            </Stack>
          </Container>
        )}
      </AppShell.Main>
    </AppShell>
  )
}
