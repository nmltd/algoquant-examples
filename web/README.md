# Web tools and SMA2 application

**[Open SMA2](https://tools.nmfin.tech/sma2/)** · **[Tools directory](https://tools.nmfin.tech/)** · **[Repository setup](../README.md)**

[Performance Analyzer](https://tools.nmfin.tech/performance/) is also hosted by this service; see [its documentation](performance/README.md). Both applications share statistics, snapshot storage, CSV/Excel exports, comparison components, styling and print preparation under the shared `web` package/resources.

SMA2 is a Java 8 web application built on AlgoQuant. Choose one of twenty supported US stocks, an inclusive period and two SMA periods in **trading days**. Each successful backtest compares three independently funded $10,000 accounts:

1. Full-equity long/short SMA2.
2. Buy-and-hold of the selected stock.
3. Buy-and-hold of the S&P 500 price index, downloaded from Yahoo as `^GSPC`.

The report provides seven headline statistics per account, four interactive summary-comparison charts, 187 configured scalar performance results per account, price/SMA and portfolio-history charts, executions, saved URLs and Excel/PDF/CSV/JSON exports. Long daily/monthly/yearly statistics tables and an S&P 500 time-series chart are not added to the webpage.

The six command-line technical demos remain separate and are described in the [root README](../README.md#six-command-line-technical-examples).

The [tools homepage disclaimer](https://tools.nmfin.tech/#disclaimer) applies to all hosted tools, applications and outputs. It states the educational/informational purpose, no warranties, use at your own risk and liability limits, while preserving rights and liabilities that cannot legally be excluded.

## Run locally

Configure Maven authentication and an external NM license as described in the [root README](../README.md#setup). From the repository root:

```bash
mvn -U clean verify
java -Djava.awt.headless=true -Dlogback.configurationFile=web/logback.xml \
  -cp 'target/classes:target/lib/*' tech.nmfin.algoquant.web.ToolsWeb
```

Open [http://localhost:8080/](http://localhost:8080/) for the tools directory or [http://localhost:8080/sma2/](http://localhost:8080/sma2/) for the app. On Windows, replace the classpath separator `:` with `;`.

| Environment variable | Behavior |
| --- | --- |
| `PORT` | HTTP port; default `8080` |
| `NM_LICENSE_FILE` | External license path; default `license/nm.lic` |
| `SMA2_DATA_MODE` | Set exactly `synthetic` for explicitly labeled offline fixtures; otherwise download Yahoo data |
| `SMA2_REPORT_DIRECTORY` | Local snapshot directory; default `reports/`, which is gitignored |
| `SMA2_REPORT_BUCKET` | Private Google Cloud Storage bucket for production snapshots |
| `SMA2_APP_REVISION` | Revision recorded in saved reports; the deployment sets the Git commit SHA |

There is no automatic synthetic fallback. A failed S&P 500 download/simulation fails a new backtest rather than silently omitting the market comparison.

## Simulation conventions

### SMA2 mechanics

- Initial equity is **USD 10,000**. The first selected close enters the prevailing trend from the previous session. Later crossovers reverse the position.
- Fast SMA above slow SMA targets **100% of equity long**; fast below slow targets **100% short**. At entry/reversal, target shares = direction × current equity ÷ current simulated fill price. Fractional shares are allowed.
- Signals use the **previous session's adjusted close** and fill at the **current session's adjusted close**. This is a delayed-close model, not a next-open model.
- Warm-up observations before the selected start initialize the SMAs without trading. Positions are held between signals; there is no daily rebalancing.
- Short-sale proceeds are offset by the short liability and do not create extra equity. If equity becomes non-positive, the model closes the position and stops trading.
- Open positions remain marked to market at the end; there is no forced terminal liquidation.

### Dates, prices and costs

Dates are inclusive and use available trading sessions. Start must be in 2000 or later, the requested interval cannot exceed 3,660 calendar days, and SMA periods must satisfy `1 <= fast < slow <= 250`. The server rejects end dates on or after today in **America/New_York**, excluding incomplete current-day bars. The default End date is the previous Monday–Friday date in New York; it skips weekends, not exchange holidays.

Stock inputs are Yahoo adjusted closes. Corporate-action/dividend adjustments are incorporated into those prices; separate dividend cash flows are not paid again. All accounts assume zero commissions, spread, slippage, financing and short-borrow costs, with no live margin or borrowing constraints.

### Independent buy-and-hold accounts

The underlying-stock account buys fractional shares with all $10,000 at the first selected adjusted close and holds them. Its NAV equals initial capital × current adjusted close ÷ first selected adjusted close.

The S&P 500 account runs through its **own AlgoQuant simulator**, buys hypothetical fractional index units once and holds them over the requested period. Its NAV tracks `^GSPC` from its first selected index close. This is a **price-index benchmark without reinvested dividends**, not a directly tradable ETF or a total-return index. Its dividend convention differs from adjusted stock prices.

All three accounts use the same scalar-measure catalog and zero-cost assumptions. Benchmark-relative measures use the `^GSPC` lookup; monthly CAPM sampling and the zero risk-free rate are stated in the report. Measures requiring completed round-trip trades may be N/A for buy-and-hold accounts.

### Statistics and interpretation

The seven headline measures are total return, CAGR, maximum drawdown, annualized volatility, Sharpe ratio, final NAV and profit/loss. Daily volatility uses sample standard deviation × √252; daily Sharpe uses mean ÷ sample standard deviation × √252. CAGR uses the elapsed calendar days between the first and last selected closes, with 365.2425 days per year.

The 187 configured scalar results cover 52 concrete library `PerformanceMeasure` implementations, supporting standalone algorithms and parameter variants. They include balances/P&L, fees, exposures, drawdowns/durations, return statistics, CAPM, information/Omega ratios, rolling annual-return summaries, VaR/CVaR and per-trade distributions. The [shared catalog](../src/main/java/tech/nmfin/algoquant/web/AllPerformanceMeasures.java) specifies the actual parameters.

Library calendar-period sampling differs from trading-session sampling. Partial edge periods follow the library's flat-padding conventions. Library annual return averages, monthly Sortino and annual Calmar conventions are labeled separately from elapsed-time CAGR. VaR/CVaR are signed lower-tail **returns**, not positive dollar losses.

Undefined, infinite and unavailable outputs keep their status and explanation. JSON numeric fields are finite or absent, and display fields retain N/A or infinity. Individual undefined statistics do not discard a completed simulation, and missing values are not replaced with zero. Differences are SMA2 minus the comparison account; a positive difference does not automatically mean better performance.

Yahoo availability and revisions affect results. Market-data cache entries are bounded to 32 intervals per app process and disappear on restart. This cache is separate from saved-report storage. Simulations are educational and do not establish executable fills or statistically significant performance.

## Webpage controls

- Summary cards/charts compare **SMA2**, **underlying buy-and-hold**, and **S&P 500**. Hover/focus a bar for its value; use legend buttons to hide/show an account.
- **Explore price and portfolio history** is expanded by default. The price/SMA and stock-account NAV charts share a date window. Zoom with buttons or Ctrl/Command+wheel, drag to pan, and use Reset view/double-click to restore history. Keyboard controls are +/−, left/right and Home.
- Search scalar measures, or use **Expand all**, **Collapse all**, and **Clear search**.
- Use **Copy report link**, **Open saved report**, or **New backtest** to navigate saved results.

Chart navigation changes the view, not stored values or exports.

## Exports

All exports use the saved snapshot without downloading prices or rerunning the backtest.

### Excel

**Download Excel report** creates a single `.xlsx` with four tabs:

| Tab | Contents |
| --- | --- |
| SMA2 statistics | All saved SMA2 scalar measures |
| Buy and hold statistics | Selected-stock buy-and-hold scalar measures |
| S&P 500 statistics | Independent `^GSPC` account's scalar measures |
| Daily data | Selected-stock prices/SMAs and all three account NAVs, aligned by trading date |

Statistics are arranged one measure per row with value, unit, status, group, library class and parameters/explanation. **Daily data** contains these columns, in order:

```text
date
adjusted_close
fast_sma
slow_sma
sma2_nav
fully_invested_buy_hold_nav
sp500_close
sp500_nav
```

The two index columns join the independently simulated S&P 500 observations by date, not array position. Missing observations remain blank; there is no forward fill. Dates/numbers are native Excel values, headers are frozen, and filters are enabled. Numeric displays use two decimals, including percentage formatting where appropriate, while cell values retain full precision.

`PerformanceWorkbook` writes the workbook using Java ZIP/XML APIs. It uses the scalar values serialized by AlgoQuant's `PerformanceReportCsvExporter`; no desktop spreadsheet runtime is required.

### PDF

Click **Export to PDF**, then select **Save as PDF** in the browser print dialog. The A4 landscape print layout includes the saved-report URL, comparisons, charts, all scalar groups, executions and strategy explanation.

Printing temporarily clears measure filters, expands report groups, restores the full history window and shows chart series. Closing the dialog restores the screen's filters, expanded groups, chart window and legend visibility. Interactive controls and the backtest form are hidden from the printed report.

### CSV and JSON

The page offers **SMA2 performance CSV**, **Buy & hold performance CSV**, and **Full report JSON**. An S&P 500 CSV is also available through the API below.

`PerformanceCsv` adapts saved scalar results to `PerformanceReport`, then calls AlgoQuant's **`PerformanceReportCsvExporter`** to write a temporary file on the server. Each CSV has one account-level `Total` row. Column headings include units, parameters and availability status. Undefined scalar values are `NaN`; explanations remain in the headings and JSON. Temporary CSV files are deleted after reading.

The former browser-built NAV CSV has been replaced by the Excel **Daily data** tab. Complete stored series remain in JSON. CSV/JSON retain raw precision.

## Saved reports and API

Every successful `POST /sma2/api/backtest` writes an immutable JSON snapshot and returns `reportId` and `reportPath`. The browser navigates to `/sma2/reports/<32-hex-id>`. New runs always receive independent IDs, even with identical inputs.

Snapshots retain inputs, generation time, app revision, schema, prices, NAV, executions and saved statistics for all three accounts. Refreshing a saved URL retrieves the snapshot without rerunning the strategies. Current assets render the page, but the saved numbers are not recomputed. Anyone with a report URL can read it; there is no public report-listing API.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/` | Tools directory |
| GET | `/sma2/` | SMA2 application |
| GET | `/sma2/api/health` | Health probe |
| GET | `/sma2/api/stocks` | Supported stock symbols |
| POST | `/sma2/api/backtest` | Run/save a backtest; form fields `symbol`, `start`, `end`, `fast`, `slow` |
| GET | `/sma2/reports/<id>` | Saved report page |
| GET | `/sma2/api/reports/<id>` | Original JSON snapshot |
| GET | `/sma2/api/reports/<id>/report.xlsx` | Combined Excel workbook |
| GET | `/sma2/api/reports/<id>/sma2.csv` | SMA2 scalar CSV |
| GET | `/sma2/api/reports/<id>/buy-hold.csv` | Underlying buy-and-hold scalar CSV |
| GET | `/sma2/api/reports/<id>/sp500.csv` | S&P 500 scalar CSV, when saved |

`/sma2` redirects to `/sma2/`. Legacy `/reports/<id>` URLs redirect to the canonical app route; root `/api` aliases remain for compatibility, including `/api/health` for probes.

Current new snapshots use **schema 4** and `sizingModel=FULL_EQUITY_V1`. Older snapshots may lack full-equity sizing, underlying comparison statistics, or the S&P 500 account. The page labels those limitations. Old one-share reports keep their original values and use a legacy benchmark column name in Excel. Missing S&P daily observations stay blank; a missing statistics tab explains that a new run is needed. Existing snapshots that already include S&P data get the new daily columns when Excel is downloaded again.

At most one simulation runs per process; a busy process returns HTTP 429. The allowlist and date/lag limits bound requests.

### Storage and retention

Local snapshots use `reports/` or `SMA2_REPORT_DIRECTORY`. Cloud Run requires `SMA2_REPORT_BUCKET` and uses the private bucket through its attached runtime identity; it refuses to fall back to local disk.

Production currently uses `gs://servers-227017-sma2-reports`. Snapshots have **no automatic expiry policy** and remain until deleted or a lifecycle policy is configured. Runtime access is limited to bucket-scoped object creation and viewing. The bucket is private even though the app serves reports to people with their URLs. No service-account key is needed.

## Shared code and application folders

| Source | Responsibility |
| --- | --- |
| [BuyAndHoldBenchmark.java](../src/main/java/tech/nmfin/algoquant/web/BuyAndHoldBenchmark.java) | Reusable independent `^GSPC` simulation, NAV, executions, statistics and benchmark lookup |
| [AllPerformanceMeasures.java](../src/main/java/tech/nmfin/algoquant/web/AllPerformanceMeasures.java) | Shared scalar-measure catalog |
| [SMA2App.java](../src/main/java/tech/nmfin/algoquant/web/sma2/SMA2App.java) | SMA2 strategy, underlying account and report assembly |
| [ToolsWeb.java](../src/main/java/tech/nmfin/algoquant/web/ToolsWeb.java) | Shared HTTP host, app registration and server lifecycle |
| [ReportStore.java](../src/main/java/tech/nmfin/algoquant/web/ReportStore.java) | Immutable local/GCS snapshots |
| [PerformanceCsv.java](../src/main/java/tech/nmfin/algoquant/web/PerformanceCsv.java) | Library CSV exporter adapter |
| [PerformanceWorkbook.java](../src/main/java/tech/nmfin/algoquant/web/sma2/PerformanceWorkbook.java) | Combined Excel export |
| [Frontend](../src/main/resources/web/sma2/) | Page, chart interactions and PDF print layout |

`BuyAndHoldBenchmark` has a default Yahoo source and accepts an injectable `PriceSource` for shared caching or deterministic fixtures. Its `run(start, end, capital)` returns the independent account and a transient benchmark lookup. SMA2 supplies its cached price loader and runs the benchmark for every new backtest.

Keep future application-specific Java, tests and assets in matching `web/<app>` folders. Shared Java belongs in `tech.nmfin.algoquant.web`; the tools-directory HTML/styles remain at `src/main/resources/web/`.

## Verification

From the repository root after configuring Maven and the license:

```bash
mvn -U clean verify
bash scripts/run-offline.sh
python3 web/smoke.py
# Explicit network-dependent check:
python3 web/smoke.py --live
```

The smoke checks write reports under `target/web-reports/`. They validate allocation/index-return identities, every CSV/worksheet value, daily date alignment, saved-report identity after restart, and invalid/missing report behavior.

The [verification workflow](../.github/workflows/verify.yml) also runs Chrome interaction/download/print checks, generates a real PDF, and builds the production container with an Excel download check. It uses the built-in GitHub token for public Maven artifacts and an already-public starter trial license. The separate deployment workflow verifies a live Yahoo-backed simulation, S&P account and four-tab Excel export.

## Cloud Run deployment

Current service: **`sma2-web`**, project **`servers-227017`**, region **`us-central1`**. The public application is [https://tools.nmfin.tech/sma2/](https://tools.nmfin.tech/sma2/); `optim.nmfin.tech` is retired.

The manual [Deploy SMA2 web to Cloud Run](../.github/workflows/sma2-cloud-run.yml) workflow builds/tests the app, pushes its image to Artifact Registry and deploys the service. Authentication uses GitHub OIDC Workload Identity Federation. The runtime license is mounted from Secret Manager at `/secrets/nm.lic`, never copied into the container or committed to source.

### One-time setup

Run in Google Cloud Shell as the intended project's owner, with billing enabled and permission to configure services/IAM. Authenticate `gh auth login` with access to manage the repository variables, clone this repository, and upload a valid server license privately.

```bash
git clone https://github.com/nmltd/algoquant-examples.git
cd algoquant-examples
export GCP_PROJECT_ID=YOUR_PROJECT_ID
export NM_LICENSE_FILE=/absolute/path/to/nm.lic
bash web/bootstrap-cloud.sh
bash web/setup-report-storage.sh
```

The bootstrap script targets `nmltd/algoquant-examples`. Adapt that repository name and the workflow restrictions if deploying a fork. Review the scripts before using another existing project. The script creates/uses the registry, runtime/deployer accounts, license secret and WIF provider restricted to the numeric repository/owner IDs, `main` and the deployment workflow. Each bootstrap invocation adds a license secret version; it is a setup operation, not a prerequisite for every deploy.

It sets these GitHub Actions repository variables:

- `GCP_PROJECT_ID`
- `GCP_RUNTIME_SERVICE_ACCOUNT`
- `GCP_DEPLOY_SERVICE_ACCOUNT`
- `GCP_WIF_PROVIDER`

The storage script creates the private `<project>-sma2-reports` bucket and grants the runtime object creation/viewing. Remove the temporary uploaded license from Cloud Shell when setup is complete.

### Deploy and verify

For an already configured installation, run the workflow on `main`:

```bash
gh workflow run sma2-cloud-run.yml --ref main --repo nmltd/algoquant-examples
gh run list --workflow sma2-cloud-run.yml --repo nmltd/algoquant-examples
```

Defaults are one CPU, 1 GiB memory, zero minimum/two maximum instances, one concurrent request per instance and a 180-second timeout. The service is publicly invokable and can cold-start. Report storage, registry, secrets and compute incur normal Google Cloud usage costs.

Deployment checks health, a real-data AAPL backtest, independently funded `^GSPC` simulation, saved-report retrieval and the four-tab Excel download. The optional workflow `diagnose` input reads service/domain status; runtime log reads additionally require logging permissions not granted by the bootstrap script.

### Custom domain

Verify ownership of `nmfin.tech` with Google, map `tools.nmfin.tech` to `sma2-web` in `us-central1`, and apply **the exact DNS records Google returns**. Wait for the managed certificate and domain mapping to become ready before checking HTTPS. The root page lists applications; SMA2 is served at `/sma2/`.

The existing hostname uses Google's domain mapping. See [Cloud Run custom domains](https://docs.cloud.google.com/run/docs/mapping-custom-domains) for current supported options, and [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets) and [GitHub OIDC authentication](https://github.com/google-github-actions/auth) for the underlying setup.

## Shared web architecture

The entry point is `tech.nmfin.algoquant.web.ToolsWeb`. It owns the tools homepage,
health checks, shared assets, license setup, executor and app registration. Each app
implements `ToolRoutes`: `sma2/Sma2Routes` and `performance/PerformanceRoutes` handle
only their own endpoints (SMA2 also retains its historical root API aliases).

Common Java classes live directly in `tech.nmfin.algoquant.web`:

- `HttpSupport`: bounded forms, method checks, resource responses and attachments.
- `SavedReport`, `ReportStore`, `SnapshotStore`: common metadata, app-scoped reads,
  JSON serialization and immutable local/GCS storage. Existing report URLs and JSON
  fields are preserved, including snapshots created before the refactor.
- `PerformanceStatistics`: daily returns and headline statistics used by both apps
  and the independent `BuyAndHoldBenchmark` simulation.
- `AllPerformanceMeasures`, `NavSeriesAnalysis`: AlgoQuant measures and NAV-only analysis.
- `PerformanceCsv`, `SpreadsheetWorkbook`: CSV export and Excel cells/layout.

Each app retains its strategy/import logic and `PerformanceWorkbook` sheet selection;
no app maintains a duplicate CSV writer or snapshot adapter. Shared browser comparison,
statistics, print and formatting code remains in `resources/web/shared`.
New apps should reuse these modules, register a `ToolRoutes` implementation in `ToolsWeb`,
and include a direct link to their implementation source.

Cloud configuration retains the existing `SMA2_*` environment variable names for compatibility.
