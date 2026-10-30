import { useState } from 'react'
import { Link, useParams } from 'react-router'

const API_URL = `${window.location.origin}/api/v1/ingest`

function Snippet({ title, code }: { title: string; code: string }) {
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

  const curl = `curl -X POST ${API_URL} \\
  -H "X-Api-Key: $FLAKYHUNTER_API_KEY" \\
  -F "files=@target/surefire-reports/TEST-com.example.MyTest.xml" \\
  -F "branch=main" \\
  -F "commit=$(git rev-parse HEAD)"`

  const powershell = `$files = Get-ChildItem target\\surefire-reports\\TEST-*.xml | ForEach-Object { "-F", "files=@$($_.FullName)" }
curl.exe --ssl-no-revoke -X POST ${API_URL} \`
  -H "X-Api-Key: $env:FLAKYHUNTER_API_KEY" \`
  @files -F "branch=main" -F "commit=$(git rev-parse HEAD)"`

  const githubActions = `- name: Upload test results to FlakyHunter
  if: always()   # also when tests failed: that's when it matters most
  run: |
    args=()
    for f in target/surefire-reports/TEST-*.xml; do args+=(-F "files=@$f"); done
    curl -sS --fail-with-body -X POST ${API_URL} \\
      -H "X-Api-Key: \${{ secrets.FLAKYHUNTER_API_KEY }}" \\
      "\${args[@]}" \\
      -F "branch=\${{ github.ref_name }}" \\
      -F "commit=\${{ github.sha }}" \\
      -F "ciProvider=github" \\
      -F "runId=\${{ github.run_id }}" \\
      -F "runAttempt=\${{ github.run_attempt }}" \\
      -F "buildUrl=\${{ github.server_url }}/\${{ github.repository }}/actions/runs/\${{ github.run_id }}"`

  const surefire = `<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- re-run a failing test up to 2 times: tests that pass on a re-run are reported as flaky -->
    <rerunFailingTestsCount>2</rerunFailingTestsCount>
  </configuration>
</plugin>`

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
        <h2>2. Upload JUnit XML after your tests</h2>
        <p className="muted">
          Works with Maven Surefire, Gradle (<code>build/test-results/test/*.xml</code>) and pytest (
          <code>--junitxml=report.xml</code>). Up to 20 files and 10 MB per upload; <code>.xml.gz</code> is fine.
        </p>
        <Snippet title="GitHub Actions (add after your test step)" code={githubActions} />
        <Snippet title="curl (Linux / macOS / Git Bash)" code={curl} />
        <Snippet title="PowerShell (Windows)" code={powershell} />
        <p className="muted">
          Sending <code>runId</code> + <code>runAttempt</code> (or your own <code>idempotencyKey</code>) makes uploads
          safe to retry: the same CI run is never counted twice.
        </p>
      </section>

      <section className="card">
        <h2>3. Let Maven re-run failures (recommended)</h2>
        <p className="muted">
          A test that fails and then passes on a re-run is the clearest sign of flakiness. FlakyHunter reads Surefire&apos;s
          re-run results automatically.
        </p>
        <Snippet title="pom.xml" code={surefire} />
      </section>
    </>
  )
}
