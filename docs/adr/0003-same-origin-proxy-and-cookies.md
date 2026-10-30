# ADR-0003: Same-origin `/api` proxy on Vercel + httpOnly cookie JWTs

**Status:** Accepted

## Context
The React app is hosted on Vercel and the API on Render — two different domains. Cross-domain calls need
CORS and third-party cookies, which browsers increasingly block. Storing tokens in `localStorage` exposes
them to XSS.

## Decision
Vercel **rewrites `/api/*` to the Render backend**, so the browser only ever talks to one domain.
Login sets a short-lived access JWT (15 min) and a rotating refresh token (7 days) in
`HttpOnly; Secure; SameSite=Lax; Path=/api` cookies. State-changing requests carry a CSRF token header.

## Consequences
- Cookies are first-party; no CORS configuration in production.
- JavaScript can't read the tokens, limiting XSS damage.
- Every API call goes through Vercel's edge first (small extra latency, acceptable).
