export function formatDuration(ms) {
  if (ms < 1000) return `${ms} ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes} min ${Math.round(seconds % 60)} s`
}

export function formatDateTime(iso) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

/** Real git SHAs (hex, 8+ chars) are shortened to 7 characters; any other commit label is shown as sent. */
export function shortSha(sha) {
  if (!sha) return '—'
  return /^[0-9a-f]{8,40}$/i.test(sha) ? sha.slice(0, 7) : sha
}

/** Pass rate in percent; flaky (passed on retry) counts as passed, skipped tests are left out. */
export function passRate(run) {
  const executed = run.passed + run.failed + run.flaky
  return executed === 0 ? null : Math.round(((run.passed + run.flaky) / executed) * 1000) / 10
}

/** "+2.5 pts" / "−1 pts" / "±0 pts"; null when there is nothing to compare. */
export function change(current, previous, unit = ' pts') {
  if (current === null || current === undefined || previous === null || previous === undefined) return null
  const diff = Math.round((current - previous) * 10) / 10
  if (diff === 0) return `±0${unit}`
  return `${diff > 0 ? '+' : '−'}${Math.abs(diff)}${unit}`
}
