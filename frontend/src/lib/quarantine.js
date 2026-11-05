/** Escapes text so it matches literally inside a regular expression. */
function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')
}

/**
 * A `--grep-invert` pattern that makes Playwright skip the quarantined tests. Names that FlakyHunter suffixed
 * with " [project]" (several Playwright projects) are matched without the suffix. Returns null when empty.
 */
export function grepInvertPattern(quarantined) {
  const titles = [...new Set(quarantined.map((q) => q.name.replace(/ \[[^\]]+\]$/, '')))]
  if (titles.length === 0) return null
  return titles.map(escapeRegex).join('|')
}
