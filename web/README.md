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
- Yahoo availability, revisions and adjustments affect results. Cached responses are bounded to 32 intervals per process, volatile across restarts, and not shared across instances. No database is required.
- At most one simulation runs at a time per process; a busy service responds 429. The stock allowlist and request/date/lag limits bound public work. For heavier use, add a queue, shared cache and edge rate limits.

## Cloud Run deployment

Deployment target: **optim.nmfin.tech**. This hostname is not live until cloud authorization, deployment and DNS mapping complete.

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

3. Run **Deploy SMA2 web to Cloud Run** on `main` in GitHub Actions. Defaults: one CPU, 1 GiB, zero minimum instances, two maximum instances, one concurrent request per instance, 180-second request timeout. Registry storage, Secret Manager and usage may incur charges. The service is publicly invokable; cold starts are possible.
4. Verify the returned `run.app` URL and run a real-data backtest.

### optim.nmfin.tech

After verifying ownership of `nmfin.tech` in Google Search Console, map the hostname in Cloud Run's domain-mapping UI in `us-central1`, selecting service `sma2-web`. Apply **the exact DNS records Google returns** at your DNS provider. Do not guess an IP address or overwrite other domain records. Wait for managed TLS and mapping readiness, then check HTTPS and the live backtest. Direct Cloud Run domain mappings have preview/production limitations; use an external Application Load Balancer if production-grade domain features are required.

Sources: [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets), [GitHub OIDC authentication](https://github.com/google-github-actions/auth), [custom domains](https://docs.cloud.google.com/run/docs/mapping-custom-domains).
