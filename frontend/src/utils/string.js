// String utility functions for the learning flow.

/**
 * Compute Levenshtein edit distance between two strings.
 * Iterative two-row DP, O(m*n) time, O(min(m,n)) space.
 *
 * Used by the spelling steps to grant "almost right" (d == 1) half-credit
 * instead of zero-tolerance fail. A single-letter typo on a 10-letter word
 * is statistically very different from "I have no idea how to spell this".
 */
export function levenshtein(a, b) {
  if (a === b) return 0
  if (!a) return b.length
  if (!b) return a.length

  // Ensure b is the shorter to minimize space
  if (a.length < b.length) {
    [a, b] = [b, a]
  }

  let prev = new Array(b.length + 1)
  let curr = new Array(b.length + 1)

  for (let j = 0; j <= b.length; j++) prev[j] = j

  for (let i = 1; i <= a.length; i++) {
    curr[0] = i
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      curr[j] = Math.min(
        curr[j - 1] + 1,        // insertion
        prev[j] + 1,            // deletion
        prev[j - 1] + cost      // substitution
      )
    }
    ;[prev, curr] = [curr, prev]
  }
  return prev[b.length]
}

/**
 * Classify a spelling attempt against the target word.
 * Returns one of: 'correct' | 'typo' | 'wrong'.
 *
 *   d == 0           → 'correct' (full credit)
 *   d == 1           → 'typo'    (single-letter mistake — partial credit)
 *   d >= 2 or empty  → 'wrong'   (no credit)
 *
 * Case-insensitive; trims whitespace.
 */
export function classifySpelling(userInput, targetWord) {
  const input = (userInput || '').trim().toLowerCase()
  const target = (targetWord || '').trim().toLowerCase()
  if (!input) return 'wrong'
  const d = levenshtein(input, target)
  if (d === 0) return 'correct'
  if (d === 1) return 'typo'
  return 'wrong'
}
