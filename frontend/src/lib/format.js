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

export function shortSha(sha) {
  return sha ? sha.slice(0, 7) : '—'
}

/** Pass rate in percent; flaky (passed on retry) counts as passed, skipped tests are left out. */
export function passRate(run) {
  const executed = run.passed + run.failed + run.flaky
  return executed === 0 ? null : Math.round(((run.passed + run.flaky) / executed) * 1000) / 10
}
