# Examples

## `sample-history/`

Twelve generated JUnit XML reports for a fictional "acme shop", one per CI run, oldest first.
Uploading them in order shows every verdict of the flakiness engine:

| Test | History (P pass, F fail, R passed on retry, S skipped, - absent) | Expected state |
| --- | --- | --- |
| `CartTest.addsItem`, `computesTotal`, `removesItem`, `CheckoutIT.showsReceipt`, `SearchTest.sortsByPrice` | `PPPPPPPPPPPP` | STABLE |
| `CartTest.appliesCoupon` | `PFPPRPPFPRPP` | FLAKY |
| `CheckoutIT.paysWithCard` | `PPRPPRPFPPRP` | FLAKY |
| `SearchTest.filtersByBrand` | `PFPFPPFPFPPF` | FLAKY |
| `CheckoutIT.refunds` | `PPPPPPPPPFFF` | BROKEN |
| `SearchTest.findsByName` | `PPPPPPPPPPPR` | SUSPECT |
| `SearchTest.suggestsSpelling` | `---------PPP` | NEW |
| `SearchTest.exportsCsv` | `SSSSSSSSSSSS` | NEW (skips never count) |

Upload them with an API key from **Settings → API keys** (PowerShell):

```powershell
# 1. Type this line by hand AFTER copying your key (the key must be the last thing you copied):
$env:FLAKYHUNTER_API_KEY = (Get-Clipboard).Trim()

# 2. Then run:
$i = 0; $files = Get-ChildItem examples\sample-history\run-*.xml | Sort-Object Name
foreach ($f in $files) {
  $i++
  $at = (Get-Date).ToUniversalTime().AddHours($i - $files.Count).ToString("yyyy-MM-ddTHH:mm:ssZ")
  curl.exe --ssl-no-revoke -sS -X POST https://flakyhunter.vercel.app/api/v1/ingest `
    -H "X-Api-Key: $env:FLAKYHUNTER_API_KEY" -F "files=@$($f.FullName)" `
    -F "branch=main" -F "commit=sample$i" -F "triggeredAt=$at" -F "idempotencyKey=sample-history-$i"
  ""
}
Remove-Item Env:FLAKYHUNTER_API_KEY
```

The `idempotencyKey` makes the upload safe to repeat: running it twice doesn't create duplicate runs.
