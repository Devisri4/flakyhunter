export type Health = {
  status: string
  components?: Record<string, { status: string }>
}

/** Turns the backend's health JSON into short lines for the page. */
export function describeHealth(health: Health): string[] {
  const lines = [`API: ${health.status}`]
  for (const [name, part] of Object.entries(health.components ?? {})) {
    if (name === 'db') lines.push(`Database: ${part.status}`)
  }
  return lines
}

export async function fetchHealth(): Promise<Health> {
  const response = await fetch('/api/actuator/health')
  // A DOWN service still answers with JSON (status 503), so read the body either way.
  const text = await response.text()
  try {
    return JSON.parse(text) as Health
  } catch {
    throw new Error(`Unexpected answer (HTTP ${response.status})`)
  }
}
