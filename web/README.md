# SMA2 web application

A separate web application in this examples project. The six technical demos are unchanged.

Choose one of twenty US stocks, inclusive start/end dates, and fast/slow SMA observation counts. The server runs the published AlgoQuant SMA2 and returns adjusted-price/SMA charts, NAV versus one-share buy-and-hold, monetary P&L, account return, maximum drawdown, annualized volatility, a zero-risk-free-rate Sharpe ratio, executions and NAV CSV download.

## Run locally

Use the root README's Maven authentication and a valid external NM license:

```bash
mvn clean verify
java -Djava.awt.headless=true -Dlogback.configurationFile=web/logback.xml -cp 'target/classes:target/lib/*' tech.nmfin.algoquant.examples.web.Sma2Web
```

Open http://localhost:8080. `PORT` overrides the port and `NM_LICENSE_FILE` overrides `license/nm.lic`. Production defaults to Yahoo downloads. `SMA2_DATA_MODE=synthetic` is explicitly for offline verification and labels every report SYNTHETIC. There is no automatic synthetic fallback when Yahoo fails.

`python3 web/smoke.py` tests offline HTTP behavior; `python3 web/smoke.py --live` tests the Yahoo path. Both write JSON reports under `target/web-reports/`. Unit tests check invalid input, invariance to future data, and NAV against an independent execution ledger.

## Simulation conventions

- Fixed $10,000 cash, USD, target +1 or -1 share. This preserves AlgoQuant SMA2 sizing; it is not a fully invested portfolio or long-only strategy.
- The signal sees the previous session's adjusted close and trades at the current session's adjusted close. Warm-up history is loaded before the selected start and does not trade.
- Dates are inclusive; incomplete daily bars are excluded by rejecting end dates on/after today in New York. Intervals are capped at roughly ten years and slow SMA at 250 observations.
- Zero commissions, spread, slippage, financing and short-borrow costs. No forced terminal liquidation. Adjusted prices are used without separate dividend cash payments.
- NAV is marked to market; P&L includes unrealized changes. Account returns use initial $10,000. Daily sample standard deviation and mean/std Sharpe use sqrt(252); undefined Sharpe is shown as N/A.
- Benchmark holds one share bought at the first selected close, with remaining cash idle. This is not a fully invested benchmark.
- Yahoo availability, revisions and adjustments affect results. Cached responses are bounded to 32 intervals per process, volatile across restarts, and not shared across instances. Market-data caching is separate from the durable saved-report store.
- At most one simulation runs at a time per process; a busy service responds 429. The stock allowlist and request/date/lag limits bound public work. For heavier use, add a queue, shared cache and edge rate limits.

## Cloud Run deployment

Live application: **https://optim.nmfin.tech**.

The manual GitHub workflow `.github/workflows/sma2-cloud-run.yml` builds/tests the app, builds a Docker image, pushes to Artifact Registry, and deploys `sma2-web` in `us-central1`. It uses GitHub OIDC Workload Identity Federation; no Google private key is required. Runtime license is mounted from Secret Manager, never copied into the container or public source.

One-time setup in **Google Cloud Shell**, signed into your intended project with permission to enable services and configure IAM:

1. Clone this public repository and authenticate GitHub CLI (`gh auth login`) with repository administration access for setting repository variables.
2. Upload a valid server license into Cloud Shell privately. Do not add it to Git. Set:

```bash
export GCP_PROJECT_ID=YOUR_PROJECT_ID
export NM_LICENSE_FILE=/absolute/path/to/nm.lic
bash web/bootstrap-cloud.sh
```

The script creates Artifact Registry, the runtime/deployer service accounts, a Secret Manager license version and a WIF provider restricted to this repository's numeric ID, owner ID, main branch and deployment workflow. It sets four GitHub Actions repository variables. Review the script before running against an existing project; billing must already be enabled. Remove your temporary Cloud Shell license copy afterward.

3. Create the private report bucket with \`bash web/setup-report-storage.sh\` (same project-owner Cloud Shell session; no license required for this step).
4. Run **Deploy SMA2 web to Cloud Run** on `main` in GitHub Actions. Defaults: one CPU, 1 GiB, zero minimum instances, two maximum instances, one concurrent request per instance, 180-second request timeout. Registry storage, Secret Manager and usage may incur charges. The service is publicly invokable; cold starts are possible.
5. Verify the returned `run.app` URL and run a real-data backtest.

### optim.nmfin.tech

After verifying ownership of `nmfin.tech` in Google Search Console, map the hostname in Cloud Run's domain-mapping UI in `us-central1`, selecting service `sma2-web`. Apply **the exact DNS records Google returns** at your DNS provider. Do not guess an IP address or overwrite other domain records. Wait for managed TLS and mapping readiness, then check HTTPS and the live backtest. Direct Cloud Run domain mappings have preview/production limitations; use an external Application Load Balancer if production-grade domain features are required.

Sources: [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets), [GitHub OIDC authentication](https://github.com/google-github-actions/auth), [custom domains](https://docs.cloud.google.com/run/docs/mapping-custom-domains).

## Comprehensive library performance report

The detailed web report represents all **52 concrete PerformanceMeasure implementations** in AlgoQuant's performance-measure packages, plus the standalone HistoricalVaR, ConditionalVaR and InformationRatio algorithms. Configurable implementations are evaluated with explicit defaults and useful variants; arbitrary parameter combinations are not enumerated.

Coverage includes balances/P&L, commissions and transaction fees, drawdowns and long/short/total exposures, daily/monthly/yearly return statistics, Sharpe/Sortino/Calmar/Treynor, CAPM alpha/beta/R-squared and benchmark level correlation, information/Omega/Sharpe-Omega ratios, 1- and 3-year rolling-return summaries, 95%/99% historical/normal/conditional VaR, and every built-in PerTradeValue.Common × StatOnDistribution.Common combination.

All four PeriodByPeriodMeasure implementations are provided in calendar-daily, monthly and yearly tables. Page previews cap each table at 200 rows; all rows are retained in the full report JSON and combined measures CSV. The original NAV CSV is still available. Interface/abstract classes, factories, fee schemes, lookup interfaces and utility classes are supporting API components rather than additional statistics. RiskMeasures is a convenience collection of already-covered measures. Omega algorithms are shown as explicitly labeled variants of the Omega measures.

**Conventions:** USD 10,000 account; zero fees and zero risk-free/target/loss-threshold returns. CAPM and excess-return measures use SPY adjusted prices with monthly CAPM sampling. BenchmarkCorrelation is a price/NAV **level** correlation. The existing chart benchmark remains one share of the selected stock. The report clearly labels a failed SPY download without substituting the selected stock. Synthetic verification labels the SPY series synthetic too.

Library calendar sampling differs from trading-session sampling. Partial edge periods are flat-padded under library conventions, without using future prices outside the selected period. Annual bucket compound returns are explicitly labeled as library annual averages, not exact elapsed-time CAGR. The six headline cards retain their original daily account-return conventions.

VaR/CVaR output is the library's **signed lower-tail return** at alpha 0.05 or 0.01, displayed as a percentage return. It is not relabeled as a positive USD loss. Drawdown/trade durations use their stated elapsed/calendar units. SMA inputs continue to mean trading days.

Undefined or infinite values retain their status and explanatory text; JSON numeric fields are finite or absent, while display fields preserve Infinity/N/A. A failed individual calculation is shown as unavailable and does not discard the rest of the backtest. No zero is substituted for an unavailable result.

Not-applicable rows are retained for DividendAdjustedProfitLoss (separate dividend cash flows are not supplied with adjusted-price input), PerTradePairReturn (two traded legs required), InformationRatioForZeroInvestment (this is a cash-funded strategy), PartialPerformanceMeasure (custom execution/tag filter required), and LinearlyCombinedPerformanceMeasure (custom coefficients required).

The library rolling annual-return implementation was corrected to append results to its output list, handle insufficient history and include an exact anniversary endpoint. Source/obfuscated tests validate that fix before the updated public SNAPSHOT is used.

Tests enforce representation of every concrete measure class, consistency of library P&L/balances/execution counts with the simulation, safe JSON for short/undefined samples, and unchanged trade timing/NAV. This is an educational report, not a claim of realistic execution costs or statistically significant strategy performance.

## Saved report pages

Every successful POST /api/backtest writes an immutable JSON snapshot and returns reportId and reportPath. The browser moves to /reports/<32-hex-id>. Refreshing or sharing that URL fetches the saved JSON from GET /api/reports/<id>, without market-data downloads or simulation. New runs always get independent IDs, even with identical inputs. Use Copy report link, Open saved report, or New backtest. Anyone with the URL can read a report; report IDs are random and there is no public listing.

The snapshot retains input parameters, generation time, application revision, schema version, prices, NAV, executions, all scalar measures and all period rows. Rendering uses the current page assets; the underlying saved numbers are not recomputed.

Cloud Run uses the private bucket <project>-sma2-reports through its attached runtime identity. Run web/setup-report-storage.sh once in Cloud Shell with GCP_PROJECT_ID set before deploying this feature. The runtime receives bucket-scoped objectCreator and objectViewer only. No service-account key or public bucket access is needed; there is no automatic report-expiry policy. Storage/read costs follow Cloud Storage usage. Cloud Run refuses to use local disk when SMA2_REPORT_BUCKET is missing.

Local runs use reports/ (gitignored); SMA2_REPORT_DIRECTORY can choose another persistent local directory. Container smoke tests use /tmp/sma2-reports explicitly; that is testing storage, not production persistence. Tests verify snapshot identity after store/server recreation, independent IDs, invalid/missing IDs, GCS create-only upload and authenticated retrieval, and write-failure handling.

The application implementation is [SMA2App.java](../src/main/java/tech/nmfin/algoquant/examples/web/SMA2App.java).

## Interactive charts

Both charts share a date window. Hover/tap shows exact session values with a crosshair; use zoom buttons or Ctrl/Command+wheel, drag horizontally to pan, and Reset view or double-click to return to full history. Focus a chart and use +/−, left/right arrows or Home. Click legend buttons to hide/show individual series. Y axes rescale to visible data; chart navigation changes only the view, never the saved results or exports.
