import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useQuarantineList } from '../../api/hooks'
import { grepInvertPattern } from '../../lib/quarantine'

const ORIGIN = window.location.origin
const API_URL = `${ORIGIN}/api/v1/ingest`

function Snippet({ title, code }) {
  const [copied, setCopied] = useState(false)
  return (
    <div className="snippet">
      <div className="row spread">
        <strong>{title}</strong>
        <button
          className="button ghost"
          onClick={() => navigator.clipboard.writeText(code).then(() => setCopied(true))}
        >
          {copied ? 'Copied ✓' : 'Copy'}
        </button>
      </div>
      <pre>{code}</pre>
    </div>
  )
}

export function SetupPage() {
  const { slug = '' } = useParams()
  const quarantined = useQuarantineList(slug)
  const pattern = grepInvertPattern(quarantined.data ?? [])

  const action = `- name: Upload test results to FlakyHunter
  if: always()   # also when tests failed: that's when it matters most
  uses: Devisri4/flakyhunter/github-action@main
  with:
    api-key: \${{ secrets.FLAKYHUNTER_API_KEY }}
    # files defaults to Maven Surefire + Gradle reports, e.g. for Playwright:
    # files: results.json`

  const curl = `curl -X POST ${API_URL} \\
  -H "X-Api-Key: $FLAKYHUNTER_API_KEY" \\
  -F "files=@target/surefire-reports/TEST-com.example.MyTest.xml" \\
  -F "branch=main" \\
  -F "commit=$(git rev-parse HEAD)"`

  const powershell = `$files = Get-ChildItem target\\surefire-reports\\TEST-*.xml | ForEach-Object { "-F", "files=@$($_.FullName)" }
curl.exe --ssl-no-revoke -X POST ${API_URL} \`
  -H "X-Api-Key: $env:FLAKYHUNTER_API_KEY" \`
  @files -F "branch=main" -F "commit=$(git rev-parse HEAD)"`

  const playwright = `# playwright.config: retries: 2 — and add the JSON reporter:
PLAYWRIGHT_JSON_OUTPUT_NAME=results.json npx playwright test --reporter=json,html

curl -X POST ${API_URL} -H "X-Api-Key: $FLAKYHUNTER_API_KEY" \\
  -F "files=@results.json" -F "format=playwright" -F "branch=main"`

  const surefire = `<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- re-run a failing test up to 2 times: tests that pass on a re-run are reported as flaky -->
    <rerunFailingTestsCount>2</rerunFailingTestsCount>
  </configuration>
</plugin>`

  const junit5 = `<!-- pom.xml -->
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
  <groupId>com.github.Devisri4.flakyhunter</groupId>
  <artifactId>flakyhunter-junit5</artifactId>
  <version>main-SNAPSHOT</version>
  <scope>test</scope>
</dependency>

# src/test/resources/junit-platform.properties
junit.jupiter.extensions.autodetection.enabled=true

# CI environment
FLAKYHUNTER_API_KEY=\${{ secrets.FLAKYHUNTER_API_KEY }}
FLAKYHUNTER_URL=${ORIGIN}`

  const grep = pattern
    ? `npx playwright test --grep-invert "${pattern.replace(/"/g, '\\"')}"`
    : '# nothing is quarantined right now'

  return (
    <>
      <p>
        <Link to={`/p/${slug}`}>← Project</Link>
      </p>
      <h1>Send test results</h1>

      <section className="card">
        <h2>1. Get an API key</h2>
        <p>
          Create one in <Link to={`/p/${slug}/settings`}>Settings → API keys</Link> (owners only). In CI, store it as a
          secret named <code>FLAKYHUNTER_API_KEY</code> — never put it in your code.
        </p>
      </section>

      <section className="card">
        <h2>2. Upload the reports after your tests</h2>
        <p className="muted">
          JUnit XML (Maven Surefire, Gradle <code>build/test-results/test/*.xml</code>, pytest{' '}
          <code>--junitxml=report.xml</code>) and Playwright JSON. Up to 20 files and 10 MB per upload;{' '}
          <code>.gz</code> is fine. The format is detected automatically.
        </p>
        <Snippet title="GitHub Actions (recommended)" code={action} />
        <Snippet title="Playwright" code={playwright} />
        <Snippet title="curl (Linux / macOS / Git Bash)" code={curl} />
        <Snippet title="PowerShell (Windows)" code={powershell} />
        <p className="muted">
          Sending <code>runId</code> + <code>runAttempt</code> (the GitHub Action does) or your own{' '}
          <code>idempotencyKey</code> makes uploads safe to retry: the same CI run is never counted twice.
        </p>
      </section>

      <section className="card">
        <h2>3. Let failing tests re-run (recommended)</h2>
        <p className="muted">
          A test that fails and then passes on a re-run is the clearest sign of flakiness. FlakyHunter reads Surefire&apos;s
          and Playwright&apos;s retry results automatically.
        </p>
        <Snippet title="Maven Surefire (pom.xml)" code={surefire} />
      </section>

      <section className="card">
        <h2>4. Skip quarantined tests in CI</h2>
        <p className="muted">
          Quarantine a test on its detail page; CI then skips it until it expires. FlakyHunter publishes the list at{' '}
          <code>GET /api/v1/quarantine</code> (API key).
        </p>
        <Snippet title="JUnit 5 extension" code={junit5} />
        <Snippet
          title={`Playwright (${quarantined.data?.length ?? 0} quarantined right now)`}
          code={grep}
        />
      </section>
    </>
  )
}
