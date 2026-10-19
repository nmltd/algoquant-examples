#!/usr/bin/env bash
# One-time Cloud Shell setup, using the user's interactive Google and GitHub sessions.
set -euo pipefail
: "${GCP_PROJECT_ID:?Choose your Google Cloud project and export GCP_PROJECT_ID}"
: "${NM_LICENSE_FILE:?Set NM_LICENSE_FILE to the license uploaded into Cloud Shell}"
test -f "$NM_LICENSE_FILE"
region=us-central1
repo=nmltd/algoquant-examples
number="$(gcloud projects describe "$GCP_PROJECT_ID" --format='value(projectNumber)')"
runtime="sma2-runtime@$GCP_PROJECT_ID.iam.gserviceaccount.com"
deployer="sma2-deploy@$GCP_PROJECT_ID.iam.gserviceaccount.com"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --project="$GCP_PROJECT_ID"
for account in sma2-runtime sma2-deploy; do
  gcloud iam service-accounts describe "$account@$GCP_PROJECT_ID.iam.gserviceaccount.com" --project="$GCP_PROJECT_ID" >/dev/null 2>&1 || gcloud iam service-accounts create "$account" --project="$GCP_PROJECT_ID"
done
gcloud artifacts repositories describe sma2 --location="$region" --project="$GCP_PROJECT_ID" >/dev/null 2>&1 || gcloud artifacts repositories create sma2 --repository-format=docker --location="$region" --project="$GCP_PROJECT_ID"
if ! gcloud secrets describe sma2-nm-license --project="$GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud secrets create sma2-nm-license --replication-policy=automatic --project="$GCP_PROJECT_ID"
fi
gcloud secrets versions add sma2-nm-license --data-file="$NM_LICENSE_FILE" --project="$GCP_PROJECT_ID"
gcloud secrets add-iam-policy-binding sma2-nm-license --member="serviceAccount:$runtime" --role=roles/secretmanager.secretAccessor --project="$GCP_PROJECT_ID" >/dev/null
for role in roles/run.admin roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" --member="serviceAccount:$deployer" --role="$role" --condition=None >/dev/null
done
gcloud iam service-accounts add-iam-policy-binding "$runtime" --member="serviceAccount:$deployer" --role=roles/iam.serviceAccountUser --project="$GCP_PROJECT_ID" >/dev/null
gcloud iam workload-identity-pools describe sma2-github --location=global --project="$GCP_PROJECT_ID" >/dev/null 2>&1 || gcloud iam workload-identity-pools create sma2-github --location=global --project="$GCP_PROJECT_ID"
repo_id="$(gh api "repos/$repo" --jq '.id')"
owner_id="$(gh api "repos/$repo" --jq '.owner.id')"
provider="projects/$number/locations/global/workloadIdentityPools/sma2-github/providers/github"
if ! gcloud iam workload-identity-pools providers describe github --workload-identity-pool=sma2-github --location=global --project="$GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc github --workload-identity-pool=sma2-github --location=global --project="$GCP_PROJECT_ID" \
    --issuer-uri=https://token.actions.githubusercontent.com \
    --attribute-mapping='google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id' \
    --attribute-condition="assertion.repository_id == '$repo_id' && assertion.repository_owner_id == '$owner_id' && assertion.ref == 'refs/heads/main' && assertion.workflow_ref == '$repo/.github/workflows/tools-cloud-run.yaml@refs/heads/main'"
else
  # Existing providers must follow the renamed workflow as well.
  gcloud iam workload-identity-pools providers update-oidc github --workload-identity-pool=sma2-github --location=global --project="$GCP_PROJECT_ID" \
    --attribute-condition="assertion.repository_id == '$repo_id' && assertion.repository_owner_id == '$owner_id' && assertion.ref == 'refs/heads/main' && assertion.workflow_ref == '$repo/.github/workflows/tools-cloud-run.yaml@refs/heads/main'"
fi
gcloud iam service-accounts add-iam-policy-binding "$deployer" --role=roles/iam.workloadIdentityUser --member="principalSet://iam.googleapis.com/projects/$number/locations/global/workloadIdentityPools/sma2-github/attribute.repository_id/$repo_id" --project="$GCP_PROJECT_ID" >/dev/null
gh variable set GCP_PROJECT_ID --repo "$repo" --body "$GCP_PROJECT_ID"
gh variable set GCP_RUNTIME_SERVICE_ACCOUNT --repo "$repo" --body "$runtime"
gh variable set GCP_DEPLOY_SERVICE_ACCOUNT --repo "$repo" --body "$deployer"
gh variable set GCP_WIF_PROVIDER --repo "$repo" --body "$provider"
echo 'Cloud access configured. Run the Deploy AlgoQuant tools to Cloud Run workflow on main.'
