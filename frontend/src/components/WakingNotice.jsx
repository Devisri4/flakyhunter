import { useEffect, useState } from 'react'

/**
 * Appears when a page is still loading after a few seconds. The free backend sleeps when nobody uses it
 * and takes 2–3 minutes to wake, so visitors should know the wait is expected and the page will load by itself.
 */
export function WakingNotice({ delayMs = 4000 }) {
  const [show, setShow] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setShow(true), delayMs)
    return () => clearTimeout(timer)
  }, [delayMs])

  if (!show) return null
  return (
    <p className="notice" role="status">
      ⏳ Waking up the free server… The first visit after a quiet spell can take 2–3 minutes. This page will load
      by itself.
    </p>
  )
}
