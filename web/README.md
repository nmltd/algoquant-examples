# SMA2 web application

A separate web application in this examples project. The six technical demos are unchanged.

Choose one of twenty US stocks, inclusive start/end dates, and fast/slow SMA observation counts. The server runs the AlgoQuant moving-average crossover signal and simulator with full-equity sizing and returns adjusted-price/SMA charts, NAV versus fully invested buy-and-hold, monetary P&L, account return, maximum drawdown, annualized volatility, a zero-risk-free-rate Sharpe ratio, executions and NAV CSV download.

## Run locally

Use the root README's Maven authentication and a valid external NM license:

```bash
mvn clean verify
java -Djava.awt.headless=true -Dlogback.configurationFile=web/logback.xml -cp 'target/classes:target/lib/*' tech.nmfin.algoquant.web.Sma2Web
```

Open http://localhost:8080 for the tools directory, or http://localhost:8080/sma2 for SMA2. `PORT` overrides the port and `NM_LICENSE_FILE` overrides `license/nm.lic`. Production defaults to Yahoo downloads. `SMA2_DATA_MODE=synthetic` is explicitly for offline verification and labels every report SYNTHETIC. There is no automatic synthetic fallback when Yahoo fails.

`python3 web/smoke.py` tests offline HTTP behavior; `python3 web/smoke.py --live` tests the Yahoo path. Both write JSON reports under `target/web-reports/`. Unit tests check invalid input, invariance to future data, and NAV against an independent execution ledger.

## Simulation conventions

- USD 10,000 initial equity. At the first selected close enter the previous session's prevailing SMA direction; then reverse on crossovers. At entry/reversals use target shares = direction × current equity / simulated fill price, allowing fractional shares. Hold share quantities between signals; no daily rebalancing. Short-sale cash is offset by the short liability and is not extra equity. If equity becomes non-positive, close the position and stop trading.
- The signal sees the previous session's adjusted close and trades at the current session's adjusted close. Warm-up history is loaded before the selected start and does not trade.
- Dates are inclusive; incomplete daily bars are excluded by rejecting end dates on/after today in New York. Intervals are capped at roughly ten years and slow SMA at 250 observations.
- Zero commissions, spread, slippage, financing and short-borrow costs. No forced terminal liquidation. Adjusted prices are used without separate dividend cash payments.
- NAV is marked to market; P&L includes unrealized changes. Account returns use initial $10,000. Daily sample standard deviation and mean/std Sharpe use sqrt(252); undefined Sharpe is shown as N/A.
- Buy-and-hold invests all initial capital at the first selected adjusted close and holds fractional shares. Benchmark NAV = initial capital × adjusted close / first adjusted close, so its return equals the stock's adjusted-price return.
- Yahoo availability, revisions and adjustments affect results. Cached responses are bounded to 32 intervals per process, volatile across restarts, and not shared across instances. Market-data caching is separate from the durable saved-report store.
- At most one simulation runs at a time per process; a busy service responds 429. The stock allowlist and request/date/lag limits bound public work. For heavier use, add a queue, shared cache and edge rate limits.

## Cloud Run deployment

Live application: **https://tools.nmfin.tech**.

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

### tools.nmfin.tech

After verifying ownership of `nmfin.tech` in Google Search Console, map the hostname in Cloud Run's domain-mapping UI in `us-central1`, selecting service `sma2-web`. Apply **the exact DNS records Google returns** at your DNS provider. Do not guess an IP address or overwrite other domain records. Wait for managed TLS and mapping readiness, then check HTTPS and the live backtest. Direct Cloud Run domain mappings have preview/production limitations; use an external Application Load Balancer if production-grade domain features are required.

Sources: [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets), [GitHub OIDC authentication](https://github.com/google-github-actions/auth), [custom domains](https://docs.cloud.google.com/run/docs/mapping-custom-domains).

## Comprehensive library performance report

The detailed web report represents all **52 concrete PerformanceMeasure implementations** in AlgoQuant's performance-measure packages, plus the standalone HistoricalVaR, ConditionalVaR and InformationRatio algorithms. Configurable implementations are evaluated with explicit defaults and useful variants; arbitrary parameter combinations are not enumerated.

Coverage includes balances/P&L, commissions and transaction fees, drawdowns and long/short/total exposures, daily/monthly/yearly return statistics, Sharpe/Sortino/Calmar/Treynor, CAPM alpha/beta/R-squared and benchmark level correlation, information/Omega/Sharpe-Omega ratios, 1- and 3-year rolling-return summaries, 95%/99% historical/normal/conditional VaR, and every built-in PerTradeValue.Common × StatOnDistribution.Common combination.

Long calendar-daily/monthly/yearly period tables are omitted from new reports and the webpage. Return histories are still used internally to calculate summary statistics. The original NAV CSV is still available. Interface/abstract classes, factories, fee schemes, lookup interfaces and utility classes are supporting API components rather than additional statistics. RiskMeasures is a convenience collection of already-covered measures. Omega algorithms are shown as explicitly labeled variants of the Omega measures.

**Conventions:** USD 10,000 account; zero fees and zero risk-free/target/loss-threshold returns. CAPM and excess-return measures use SPY adjusted prices with monthly CAPM sampling. BenchmarkCorrelation is a price/NAV **level** correlation. The chart benchmark is fully invested buy-and-hold of the selected stock. The report clearly labels a failed SPY download without substituting the selected stock. Synthetic verification labels the SPY series synthetic too.

Library calendar sampling differs from trading-session sampling. Partial edge periods are flat-padded under library conventions, without using future prices outside the selected period. Annual bucket compound returns are explicitly labeled as library annual averages, not exact elapsed-time CAGR. The six headline cards retain their original daily account-return conventions.

VaR/CVaR output is the library's **signed lower-tail return** at alpha 0.05 or 0.01, displayed as a percentage return. It is not relabeled as a positive USD loss. Drawdown/trade durations use their stated elapsed/calendar units. SMA inputs continue to mean trading days.

Undefined or infinite values retain their status and explanatory text; JSON numeric fields are finite or absent, while display fields preserve Infinity/N/A. A failed individual calculation is shown as unavailable and does not discard the rest of the backtest. No zero is substituted for an unavailable result.

Not-applicable rows are retained for DividendAdjustedProfitLoss (separate dividend cash flows are not supplied with adjusted-price input), PerTradePairReturn (two traded legs required), InformationRatioForZeroInvestment (this is a cash-funded strategy), PartialPerformanceMeasure (custom execution/tag filter required), and LinearlyCombinedPerformanceMeasure (custom coefficients required).

The library rolling annual-return implementation was corrected to append results to its output list, handle insufficient history and include an exact anniversary endpoint. Source/obfuscated tests validate that fix before the updated public SNAPSHOT is used.

Tests enforce representation of every concrete measure class, consistency of library P&L/balances/execution counts with the simulation, safe JSON for short/undefined samples, and unchanged trade timing/NAV. This is an educational report, not a claim of realistic execution costs or statistically significant strategy performance.

## Saved report pages

Every successful POST /sma2/api/backtest writes an immutable JSON snapshot and returns reportId and reportPath. The browser moves to /sma2/reports/<32-hex-id>. Refreshing or sharing that URL fetches the saved JSON from GET /sma2/api/sma2/reports/<id>, without market-data downloads or simulation. New runs always get independent IDs, even with identical inputs. Use Copy report link, Open saved report, or New backtest. Anyone with the URL can read a report; report IDs are random and there is no public listing.

The snapshot retains input parameters, generation time, application revision, schema version, prices, NAV, executions, both portfolios' scalar measures and summary statistics. Rendering uses the current page assets; the underlying saved numbers are not recomputed.

Cloud Run uses the private bucket <project>-sma2-reports through its attached runtime identity. Run web/setup-report-storage.sh once in Cloud Shell with GCP_PROJECT_ID set before deploying this feature. The runtime receives bucket-scoped objectCreator and objectViewer only. No service-account key or public bucket access is needed; there is no automatic report-expiry policy. Storage/read costs follow Cloud Storage usage. Cloud Run refuses to use local disk when SMA2_REPORT_BUCKET is missing.

Local runs use reports/ (gitignored); SMA2_REPORT_DIRECTORY can choose another persistent local directory. Container smoke tests use /tmp/sma2-reports explicitly; that is testing storage, not production persistence. Tests verify snapshot identity after store/server recreation, independent IDs, invalid/missing IDs, GCS create-only upload and authenticated retrieval, and write-failure handling.

The application implementation is [SMA2App.java](../src/main/java/tech/nmfin/algoquant/web/SMA2App.java).

## Interactive charts

Both charts share a date window. Hover/tap shows exact session values with a crosshair; use zoom buttons or Ctrl/Command+wheel, drag horizontally to pan, and Reset view or double-click to return to full history. Focus a chart and use +/−, left/right arrows or Home. Click legend buttons to hide/show individual series. Y axes rescale to visible data; chart navigation changes only the view, never the saved results or exports.

## Position-sizing version

New reports use reportSchemaVersion 2 and sizingModel FULL_EQUITY_V1. The app uses the library moving-average signal and simulator with the app's full-equity allocation, replacing the library demo's fixed ±1-share allocation. Order quantities are determined at the simulated fill close; this idealized fractional-share model has no costs or borrow/margin constraints. There is no claim that these fills can be achieved live. Legacy stored snapshots without sizingModel remain byte-for-byte unchanged and the page labels them as one-share results; rerun to obtain a new full-equity report. New snapshots include benchmarkTotalReturn and a bankrupt flag.

## Tools directory and application routes

The root / lists available applications; SMA2 lives at /sma2 (redirects to /sma2/). Assets and app APIs are under /sma2/, and saved pages use /sma2/reports/<id>. New tools can add their own route and directory entry. Existing /reports/<id> links redirect to /sma2/reports/<id>; old stored JSON remains unchanged and the browser builds the new canonical link from reportId. Root /api aliases remain available for compatibility and /api/health for infrastructure probes. The storage bucket is unchanged.

To add the hostname, create a Cloud Run mapping for tools.nmfin.tech to the existing sma2-web service in servers-227017/us-central1, apply the DNS records returned by Google and wait for TLS. Keep optim.nmfin.tech mapped if old shared links should continue to work.

## Side-by-side portfolio comparison

Schema 3 reports run fully funded buy-and-hold as a separate portfolio in the same AlgoQuant simulator, using the same stock, dates, $10,000 capital, adjusted prices and zero-cost assumptions. It buys fractional shares once at the first close and does not force a final sale. Both portfolios are evaluated through the same AllPerformanceMeasures catalog and SPY benchmark. Buy-and-hold's closed-trade measures are N/A when there are no round trips.

The report begins with paired summary cards and four comparison charts: growth/return, risk, risk-adjusted ratios, and portfolio value/P&L. Bars support hover/focus and legend toggles. Full summary-measure groups show SMA2, buy-and-hold, and SMA2-minus-buy-and-hold differences (percentage points for percentages). Higher is not automatically better, so differences are not ranked as wins. Display precision remains two decimal places; CSV/JSON retain raw precision.

CAGR is based on elapsed calendar time between first and last selected closes (365.2425 days per year); daily volatility and Sharpe use sample returns and sqrt(252). Library Sortino/Calmar have separately labelled monthly/annual conventions. New reports omit long period tables; price/NAV history is available in an optional collapsed section. Comparison CSV contains scalar rows for both strategies. Older snapshots are not recomputed: the UI asks for a new run if buy-and-hold summary statistics were not saved.
