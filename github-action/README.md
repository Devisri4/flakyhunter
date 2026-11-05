# FlakyHunter upload action

Uploads your test results to [FlakyHunter](https://flakyhunter.vercel.app) after every CI run.

```yaml
- name: Upload test results to FlakyHunter
  if: always()          # also (especially) when tests failed
  uses: Devisri4/flakyhunter/github-action@main
  with:
    api-key: ${{ secrets.FLAKYHUNTER_API_KEY }}
    # files: defaults to Maven Surefire and Gradle reports
    # files: playwright-report/results.json
```

| Input | Default | |
| --- | --- | --- |
| `api-key` | — | Project API key (**Settings → API keys**), stored as a repository secret |
| `files` | Surefire + Gradle report globs | One or more globs, up to 20 files |
| `format` | detect | `junit` or `playwright` |
| `url` | `https://flakyhunter.vercel.app` | |
| `fail-on-error` | `false` | Uploading never breaks your build unless you set `true` |

The action sends the branch, commit, run id and run attempt, so re-running a workflow can never count the
same run twice (idempotency).

For Playwright, add the JSON reporter: `npx playwright test --reporter=json,html` with
`PLAYWRIGHT_JSON_OUTPUT_NAME=results.json`, then `files: results.json`.
