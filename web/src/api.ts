export async function fetchJSON<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  const data = await response.json()
  if (!response.ok) {
    const message = typeof data?.message === 'string' ? data.message : 'request failed'
    throw new Error(message)
  }
  return data as T
}

export function buildQuery(values: Record<string, unknown>): string {
  const params = new URLSearchParams()

  Object.entries(values).forEach(([key, rawValue]) => {
    if (rawValue === undefined || rawValue === null) {
      return
    }
    if (Array.isArray(rawValue)) {
      const values = rawValue
        .map((value) => String(value).trim())
        .filter(Boolean)
      if (!values.length) {
        return
      }
      params.set(key, values.join(','))
      return
    }
    const value = String(rawValue).trim()
    if (!value) {
      return
    }
    params.set(key, value)
  })

  const query = params.toString()
  return query ? `?${query}` : ''
}
