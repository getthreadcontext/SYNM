import { useState, useEffect } from 'react'
import {
  Container,
  Stack,
  Title,
  Card,
  Tabs,
  TextInput,
  NumberInput,
  Switch,
  Button,
  Group,
  Text,
  Textarea,
  Select,
  Divider,
  Grid,
  Badge,
  Alert,
} from '@mantine/core'
import { notifications } from '@mantine/notifications'

// API base helper
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
    const k = window.prompt('Unauthorized. Enter SynM API key (see synm_api_key.txt):')
    if (k) {
      document.cookie = `synm_key=${k}; path=/; SameSite=Lax`
      res = await doFetch()
    }
  }
  return res
}

interface ServerSettings {
  // World settings
  difficulty: string
  gamemode: string
  hardcore: boolean
  pvp: boolean
  spawnProtection: number
  maxPlayers: number
  viewDistance: number
  simulationDistance: number
  
  // Server rules
  allowFlight: boolean
  allowNether: boolean
  allowEnd: boolean
  generateStructures: boolean
  spawnAnimals: boolean
  spawnMonsters: boolean
  spawnNpcs: boolean
  
  // Chat & messaging
  enableCommandBlock: boolean
  enableQuery: boolean
  enableRcon: boolean
  motd: string
  playerIdleTimeout: number
  
  // Performance
  maxTickTime: number
  maxWorldSize: number
  networkCompressionThreshold: number
  
  // Custom rules
  customRules: string[]
}

export default function ServerSettings() {
  const [settings, setSettings] = useState<ServerSettings>({
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
    customRules: []
  })
  
  const [newRule, setNewRule] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const loadSettings = async () => {
    setLoading(true)
    try {
      const res = await apiFetch('/api/server/settings')
      if (res.ok) {
        const data = await res.json()
        setSettings(prev => ({ ...prev, ...data }))
      }
    } catch (e) {
      notifications.show({ color: 'red', title: 'Error', message: 'Failed to load server settings' })
    } finally {
      setLoading(false)
    }
  }

  const saveSettings = async () => {
    setSaving(true)
    try {
      const res = await apiFetch('/api/server/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings)
      })
      if (res.ok) {
        notifications.show({ color: 'green', message: 'Settings saved successfully' })
      } else {
        throw new Error('Failed to save')
      }
    } catch (e) {
      notifications.show({ color: 'red', title: 'Error', message: 'Failed to save settings' })
    } finally {
      setSaving(false)
    }
  }

  const addCustomRule = () => {
    if (newRule.trim()) {
      setSettings(prev => ({
        ...prev,
        customRules: [...prev.customRules, newRule.trim()]
      }))
      setNewRule('')
    }
  }

  const removeCustomRule = (index: number) => {
    setSettings(prev => ({
      ...prev,
      customRules: prev.customRules.filter((_, i) => i !== index)
    }))
  }

  useEffect(() => {
    loadSettings()
  }, [])

  return (
    <Container size="lg">
      <Stack gap="md">
        <Group justify="space-between" align="center">
          <Title order={2}>Server Settings</Title>
          <Group>
            <Button variant="light" onClick={loadSettings} loading={loading}>
              Refresh
            </Button>
            <Button onClick={saveSettings} loading={saving}>
              Save Changes
            </Button>
          </Group>
        </Group>

        <Alert color="blue" title="Server Restart Required">
          Most settings require a server restart to take effect.
        </Alert>

        <Tabs defaultValue="world">
          <Tabs.List>
            <Tabs.Tab value="world">World Settings</Tabs.Tab>
            <Tabs.Tab value="rules">Server Rules</Tabs.Tab>
            <Tabs.Tab value="chat">Chat & Commands</Tabs.Tab>
            <Tabs.Tab value="performance">Performance</Tabs.Tab>
            <Tabs.Tab value="custom">Custom Rules</Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="world" pt="md">
            <Card withBorder p="md">
              <Title order={4} mb="md">World Configuration</Title>
              <Grid gutter="md">
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <Select
                    label="Difficulty"
                    value={settings.difficulty}
                    onChange={(value) => setSettings(prev => ({ ...prev, difficulty: value || 'normal' }))}
                    data={[
                      { value: 'peaceful', label: 'Peaceful' },
                      { value: 'easy', label: 'Easy' },
                      { value: 'normal', label: 'Normal' },
                      { value: 'hard', label: 'Hard' }
                    ]}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <Select
                    label="Default Game Mode"
                    value={settings.gamemode}
                    onChange={(value) => setSettings(prev => ({ ...prev, gamemode: value || 'survival' }))}
                    data={[
                      { value: 'survival', label: 'Survival' },
                      { value: 'creative', label: 'Creative' },
                      { value: 'adventure', label: 'Adventure' },
                      { value: 'spectator', label: 'Spectator' }
                    ]}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Max Players"
                    value={settings.maxPlayers}
                    onChange={(value) => setSettings(prev => ({ ...prev, maxPlayers: Number(value) || 20 }))}
                    min={1}
                    max={999}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Spawn Protection Radius"
                    value={settings.spawnProtection}
                    onChange={(value) => setSettings(prev => ({ ...prev, spawnProtection: Number(value) || 16 }))}
                    min={0}
                    max={100}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="View Distance"
                    value={settings.viewDistance}
                    onChange={(value) => setSettings(prev => ({ ...prev, viewDistance: Number(value) || 10 }))}
                    min={3}
                    max={32}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Simulation Distance"
                    value={settings.simulationDistance}
                    onChange={(value) => setSettings(prev => ({ ...prev, simulationDistance: Number(value) || 10 }))}
                    min={3}
                    max={32}
                  />
                </Grid.Col>
              </Grid>
              
              <Divider my="md" />
              
              <Group>
                <Switch
                  label="Hardcore Mode"
                  checked={settings.hardcore}
                  onChange={(event) => setSettings(prev => ({ ...prev, hardcore: event.currentTarget.checked }))}
                />
                <Switch
                  label="PvP Enabled"
                  checked={settings.pvp}
                  onChange={(event) => setSettings(prev => ({ ...prev, pvp: event.currentTarget.checked }))}
                />
              </Group>
            </Card>
          </Tabs.Panel>

          <Tabs.Panel value="rules" pt="md">
            <Card withBorder p="md">
              <Title order={4} mb="md">Server Rules & Features</Title>
              <Grid gutter="md">
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <Stack gap="sm">
                    <Switch
                      label="Allow Flight"
                      description="Allow players to fly in survival mode"
                      checked={settings.allowFlight}
                      onChange={(event) => setSettings(prev => ({ ...prev, allowFlight: event.currentTarget.checked }))}
                    />
                    <Switch
                      label="Allow Nether"
                      description="Enable the Nether dimension"
                      checked={settings.allowNether}
                      onChange={(event) => setSettings(prev => ({ ...prev, allowNether: event.currentTarget.checked }))}
                    />
                    <Switch
                      label="Allow End"
                      description="Enable the End dimension"
                      checked={settings.allowEnd}
                      onChange={(event) => setSettings(prev => ({ ...prev, allowEnd: event.currentTarget.checked }))}
                    />
                    <Switch
                      label="Generate Structures"
                      description="Generate villages, dungeons, etc."
                      checked={settings.generateStructures}
                      onChange={(event) => setSettings(prev => ({ ...prev, generateStructures: event.currentTarget.checked }))}
                    />
                  </Stack>
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <Stack gap="sm">
                    <Switch
                      label="Spawn Animals"
                      description="Allow passive mobs to spawn"
                      checked={settings.spawnAnimals}
                      onChange={(event) => setSettings(prev => ({ ...prev, spawnAnimals: event.currentTarget.checked }))}
                    />
                    <Switch
                      label="Spawn Monsters"
                      description="Allow hostile mobs to spawn"
                      checked={settings.spawnMonsters}
                      onChange={(event) => setSettings(prev => ({ ...prev, spawnMonsters: event.currentTarget.checked }))}
                    />
                    <Switch
                      label="Spawn NPCs"
                      description="Allow villagers to spawn"
                      checked={settings.spawnNpcs}
                      onChange={(event) => setSettings(prev => ({ ...prev, spawnNpcs: event.currentTarget.checked }))}
                    />
                  </Stack>
                </Grid.Col>
              </Grid>
            </Card>
          </Tabs.Panel>

          <Tabs.Panel value="chat" pt="md">
            <Card withBorder p="md">
              <Title order={4} mb="md">Chat & Commands</Title>
              <Grid gutter="md">
                <Grid.Col span={12}>
                  <TextInput
                    label="Message of the Day (MOTD)"
                    value={settings.motd}
                    onChange={(event) => setSettings(prev => ({ ...prev, motd: event.currentTarget.value }))}
                    placeholder="Welcome to the server!"
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Player Idle Timeout (minutes)"
                    description="0 = disabled"
                    value={settings.playerIdleTimeout}
                    onChange={(value) => setSettings(prev => ({ ...prev, playerIdleTimeout: Number(value) || 0 }))}
                    min={0}
                    max={60}
                  />
                </Grid.Col>
              </Grid>
              
              <Divider my="md" />
              
              <Group>
                <Switch
                  label="Enable Command Blocks"
                  checked={settings.enableCommandBlock}
                  onChange={(event) => setSettings(prev => ({ ...prev, enableCommandBlock: event.currentTarget.checked }))}
                />
                <Switch
                  label="Enable Query"
                  checked={settings.enableQuery}
                  onChange={(event) => setSettings(prev => ({ ...prev, enableQuery: event.currentTarget.checked }))}
                />
                <Switch
                  label="Enable RCON"
                  checked={settings.enableRcon}
                  onChange={(event) => setSettings(prev => ({ ...prev, enableRcon: event.currentTarget.checked }))}
                />
              </Group>
            </Card>
          </Tabs.Panel>

          <Tabs.Panel value="performance" pt="md">
            <Card withBorder p="md">
              <Title order={4} mb="md">Performance Settings</Title>
              <Grid gutter="md">
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Max Tick Time (ms)"
                    description="Server watchdog timeout"
                    value={settings.maxTickTime}
                    onChange={(value) => setSettings(prev => ({ ...prev, maxTickTime: Number(value) || 60000 }))}
                    min={1000}
                    max={300000}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Max World Size"
                    description="World border limit"
                    value={settings.maxWorldSize}
                    onChange={(value) => setSettings(prev => ({ ...prev, maxWorldSize: Number(value) || 29999984 }))}
                    min={1000}
                    max={29999984}
                  />
                </Grid.Col>
                <Grid.Col span={{ base: 12, md: 6 }}>
                  <NumberInput
                    label="Network Compression Threshold"
                    description="Packet compression limit (bytes)"
                    value={settings.networkCompressionThreshold}
                    onChange={(value) => setSettings(prev => ({ ...prev, networkCompressionThreshold: Number(value) || 256 }))}
                    min={0}
                    max={1024}
                  />
                </Grid.Col>
              </Grid>
            </Card>
          </Tabs.Panel>

          <Tabs.Panel value="custom" pt="md">
            <Card withBorder p="md">
              <Title order={4} mb="md">Custom Server Rules</Title>
              <Text size="sm" c="dimmed" mb="md">
                Add custom rules or notes for your server. These are displayed to players and staff.
              </Text>
              
              <Group mb="md" align="end">
                <TextInput
                  label="New Rule"
                  placeholder="No griefing allowed"
                  value={newRule}
                  onChange={(event) => setNewRule(event.currentTarget.value)}
                  style={{ flex: 1 }}
                  onKeyPress={(event) => {
                    if (event.key === 'Enter') {
                      addCustomRule()
                    }
                  }}
                />
                <Button onClick={addCustomRule} disabled={!newRule.trim()}>
                  Add Rule
                </Button>
              </Group>

              <Stack gap="xs">
                {settings.customRules.map((rule, index) => (
                  <Group key={index} justify="space-between" p="sm" style={{ border: '1px solid var(--mantine-color-default-border)', borderRadius: 4 }}>
                    <Text>{rule}</Text>
                    <Button size="xs" color="red" variant="light" onClick={() => removeCustomRule(index)}>
                      Remove
                    </Button>
                  </Group>
                ))}
                {settings.customRules.length === 0 && (
                  <Text c="dimmed" ta="center" py="md">
                    No custom rules added yet
                  </Text>
                )}
              </Stack>
            </Card>
          </Tabs.Panel>
        </Tabs>
      </Stack>
    </Container>
  )
}
