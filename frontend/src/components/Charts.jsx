import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

/*
 * Small single-series charts. One series per chart (so no legend and never a second y-axis), thin marks,
 * a recessive grid, and a hover tooltip on every chart. Colours come from CSS variables, so dark mode follows
 * the page.
 */

const AXIS = { stroke: 'var(--border)', tick: { fill: 'var(--muted)', fontSize: 12 }, tickLine: false }

/** "2026-11-05" → "Nov 5" (dates are UTC days from the API). */
export function shortDay(iso) {
  const d = new Date(`${iso}T00:00:00Z`)
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
}

function ChartTooltip({ active, payload, label, format, labelFormat }) {
  if (!active || !payload?.length) return null
  const value = payload[0].value
  return (
    <div className="chart-tooltip">
      <div className="muted small">{labelFormat ? labelFormat(label) : label}</div>
      <strong>{value === null || value === undefined ? 'no runs' : format(value)}</strong>
    </div>
  )
}

/** A line over time. `data` rows need `x` and the `dataKey`; null values leave a gap (e.g. days without runs). */
export function TrendLine({ data, dataKey, format, domain = ['auto', 'auto'], height = 180, labelFormat = shortDay }) {
  // with only a few values (or gaps between them) a bare line is invisible, so draw a dot on each value
  const values = data.filter((d) => d[dataKey] !== null && d[dataKey] !== undefined).length
  const dot = values <= 12 ? { r: 3, fill: 'var(--primary)', stroke: 'var(--card)', strokeWidth: 1.5 } : false
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
        <CartesianGrid stroke="var(--border)" strokeDasharray="0" vertical={false} />
        <XAxis dataKey="x" tickFormatter={labelFormat} minTickGap={24} {...AXIS} />
        <YAxis domain={domain} allowDecimals={false} tickFormatter={format} width={48} axisLine={false} {...AXIS} />
        <Tooltip
          content={<ChartTooltip format={format} labelFormat={labelFormat} />}
          cursor={{ stroke: 'var(--muted)', strokeWidth: 1 }}
        />
        <Line
          type="monotone"
          dataKey={dataKey}
          stroke="var(--primary)"
          strokeWidth={2}
          dot={dot}
          activeDot={{ r: 4, stroke: 'var(--card)', strokeWidth: 2 }}
          connectNulls={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

/** Bars per day (e.g. runs per day). */
export function DailyBars({ data, dataKey, format = (v) => String(v), height = 140 }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }} barCategoryGap={2}>
        <CartesianGrid stroke="var(--border)" vertical={false} />
        <XAxis dataKey="x" tickFormatter={shortDay} minTickGap={24} {...AXIS} />
        <YAxis allowDecimals={false} width={48} axisLine={false} {...AXIS} />
        <Tooltip
          content={<ChartTooltip format={format} labelFormat={shortDay} />}
          cursor={{ fill: 'var(--border)', opacity: 0.4 }}
        />
        <Bar dataKey={dataKey} fill="var(--primary)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
