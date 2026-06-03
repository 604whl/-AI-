/**
 * Parse Server-Sent Events from a fetch ReadableStream body.
 */
export async function consumeFetchSse(
  response: Response,
  onEvent: (event: string, data: string) => void,
): Promise<void> {
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const json = (await response.json()) as { message?: string }
      if (json.message) message = json.message
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  const flush = () => {
    if (dataLines.length > 0) {
      onEvent(eventName, dataLines.join('\n'))
    }
    eventName = 'message'
    dataLines = []
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let lineBreak = buffer.indexOf('\n')
    while (lineBreak >= 0) {
      let line = buffer.slice(0, lineBreak)
      buffer = buffer.slice(lineBreak + 1)
      if (line.endsWith('\r')) line = line.slice(0, -1)

      if (line === '') {
        flush()
      } else if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
      lineBreak = buffer.indexOf('\n')
    }
  }

  if (buffer.trim()) {
    const line = buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  flush()
}
