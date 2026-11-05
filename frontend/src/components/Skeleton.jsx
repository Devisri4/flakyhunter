/** A grey shimmering block shown while data loads, roughly the size of what will appear. */
export function Skeleton({ height = 80 }) {
  return <div className="skeleton" style={{ height }} aria-busy="true" aria-label="Loading" />
}
