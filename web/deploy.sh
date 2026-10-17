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
curl --fail --retry 5 --retry-delay 3 "$url/healthz"
printf '\nDeployed: %s\n' "$url"
