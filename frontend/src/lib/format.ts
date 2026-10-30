export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms} ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes} min ${Math.round(seconds % 60)} s`
}

export function formatDateTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export function shortSha(sha: string | null): string {
  return sha ? sha.slice(0, 7) : '—'
}

/** Pass rate in percent; flaky (passed on retry) counts as passed, skipped tests are left out. */
export function passRate(run: { passed: number; failed: number; flaky: number }): number | null {
  const executed = run.passed + run.failed + run.flaky
  return executed === 0 ? null : Math.round(((run.passed + run.flaky) / executed) * 1000) / 10
}
