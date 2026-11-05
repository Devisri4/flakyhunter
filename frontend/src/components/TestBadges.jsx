/** Colour + icon + word, so the state never depends on colour alone. */
export const STATE_INFO = {
  FLAKY: { icon: '↯', label: 'Flaky', hint: 'Passes and fails without a code change' },
  BROKEN: { icon: '✗', label: 'Broken', hint: 'Failed in each of the last runs: a real bug' },
  SUSPECT: { icon: '?', label: 'Suspect', hint: 'Weak flaky signal: worth watching' },
  STABLE: { icon: '✓', label: 'Stable', hint: 'Consistently passing' },
  NEW: { icon: '•', label: 'New', hint: 'Not enough runs yet for a verdict' },
}

export function StateBadge({ state }) {
  const info = STATE_INFO[state]
  return (
    <span className={`state state-${state.toLowerCase()}`} title={info.hint}>
      {info.icon} {info.label}
    </span>
  )
}

const OUTCOME_LABEL = { P: 'passed', F: 'failed', R: 'passed after a retry' }

/** One small square per recent run, oldest on the left. */
export function OutcomeStrip({ outcomes }) {
  if (!outcomes) return <span className="muted">no runs yet</span>
  return (
    <span className="strip" role="img" aria-label={`Last ${outcomes.length} runs, oldest first: ${[...outcomes].map((c) => OUTCOME_LABEL[c]).join(', ')}`}>
      {[...outcomes].map((c, i) => (
        <span key={i} className={`cell cell-${c}`} title={`run ${i + 1}: ${OUTCOME_LABEL[c]}`} />
      ))}
    </span>
  )
}
