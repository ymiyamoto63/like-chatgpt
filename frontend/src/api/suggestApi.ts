import { API_BASE_URL } from '../constants/api'

export class SuggestResponseFormatError extends Error {}

function validateSuggestApiResponse(value: unknown): string | null {
  if (typeof value !== 'object' || value === null) {
    throw new SuggestResponseFormatError('Suggest API response is not an object')
  }
  const { completion } = value as Record<string, unknown>
  if (completion !== null && typeof completion !== 'string') {
    throw new SuggestResponseFormatError(
      'Suggest API response "completion" is not a string or null',
    )
  }
  return completion === '' ? null : completion
}

export async function postSuggest(text: string): Promise<string | null> {
  const response = await fetch(`${API_BASE_URL}/api/suggest`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  })

  if (!response.ok) {
    throw new Error(`Suggest API request failed with status ${response.status}`)
  }

  return validateSuggestApiResponse(await response.json())
}
