#!/usr/bin/env bash
# Run once in Cloud Shell as the project owner, before deploying durable report support.
set -euo pipefail
: "${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
bucket="${GCP_PROJECT_ID}-sma2-reports"
runtime="sma2-runtime@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
gcloud storage buckets describe "gs://$bucket" --project="$GCP_PROJECT_ID" >/dev/null 2>&1 || \
  gcloud storage buckets create "gs://$bucket" --project="$GCP_PROJECT_ID" --location=us-central1 --uniform-bucket-level-access --public-access-prevention
for role in roles/storage.objectCreator roles/storage.objectViewer; do
  gcloud storage buckets add-iam-policy-binding "gs://$bucket" --member="serviceAccount:$runtime" --role="$role" >/dev/null
done
echo "Durable report storage ready: gs://$bucket"
echo 'Now run the Deploy SMA2 web to Cloud Run workflow on main.'
