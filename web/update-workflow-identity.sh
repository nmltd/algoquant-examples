#!/usr/bin/env bash
# Run once in authenticated Cloud Shell after renaming the deployment workflow.
# Preserve every existing restriction; replace only the exact workflow reference.
set -euo pipefail
project="${GCP_PROJECT_ID:-servers-227017}"
old='nmltd/algoquant-examples/.github/workflows/sma2-cloud-run.yml@refs/heads/main'
new='nmltd/algoquant-examples/.github/workflows/tools-cloud-run.yaml@refs/heads/main'
condition="$(gcloud iam workload-identity-pools providers describe github --workload-identity-pool=sma2-github --location=global --project="$project" --format='value(attributeCondition)')"
if [[ "$condition" == *"$old"* ]]; then
  condition="${condition//$old/$new}"
elif [[ "$condition" != *"$new"* ]]; then
  echo 'Unexpected provider condition; no changes made.' >&2
  exit 1
fi
gcloud iam workload-identity-pools providers update-oidc github --workload-identity-pool=sma2-github --location=global --project="$project" --attribute-condition="$condition"
actual="$(gcloud iam workload-identity-pools providers describe github --workload-identity-pool=sma2-github --location=global --project="$project" --format='value(attributeCondition)')"
[[ "$actual" == "$condition" ]]
echo 'Deployment workflow identity restriction updated and verified.'
