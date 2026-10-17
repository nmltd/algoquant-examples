#!/usr/bin/env bash
# Run after building web/build and authenticating gcloud. No credentials are stored in the image.
set -euo pipefail
: "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
: "${GCP_RUNTIME_SERVICE_ACCOUNT:?Set GCP_RUNTIME_SERVICE_ACCOUNT}"
region="${GCP_REGION:-us-central1}"
image="$region-docker.pkg.dev/$GCP_PROJECT_ID/sma2/sma2-web:${GITHUB_SHA:-manual}"
gcloud auth configure-docker "$region-docker.pkg.dev" --quiet
docker build -t "$image" web
docker push "$image"
gcloud run deploy sma2-web --project="$GCP_PROJECT_ID" --region="$region" --image="$image" \
  --service-account="$GCP_RUNTIME_SERVICE_ACCOUNT" --allow-unauthenticated \
  --port=8080 --cpu=1 --memory=1Gi --concurrency=1 --min-instances=0 --max-instances=2 \
  --timeout=180 --set-env-vars=SMA2_DATA_MODE=live --set-secrets=/secrets/nm.lic=sma2-nm-license:latest --quiet
url="$(gcloud run services describe sma2-web --project="$GCP_PROJECT_ID" --region="$region" --format='value(status.url)')"
printf 'Service URL: %s\n' "$url"
curl --fail --show-error --max-time 30 --retry 8 --retry-all-errors --retry-delay 5 "$url/api/health"
printf '\nDeployed: %s\n' "$url"

# Verify the deployed service with the runtime license and real Yahoo data, not just process health.
report="$(mktemp)"
trap 'rm -f "$report"' EXIT
curl --fail --show-error --max-time 150 --retry 2 --retry-delay 2 \
  -X POST -d 'symbol=AAPL&start=2020-01-01&end=2020-12-31&fast=5&slow=20' \
  "$url/api/backtest" -o "$report"
python3 - "$report" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
assert r["source"] == "Yahoo Finance daily adjusted close"
assert len(r["series"]) > 200 and len(r["executions"]) > 0
assert abs(r["finalNav"] - 10000 - r["pnl"]) < 1e-7
print("LIVE DEPLOYMENT VERIFIED:", r["symbol"], len(r["series"]), "NAV observations;", len(r["executions"]), "executions")
PY
