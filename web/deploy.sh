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
gcloud run deploy algoquant-tools --project="$GCP_PROJECT_ID" --region="$region" --image="$image" \
  --service-account="$GCP_RUNTIME_SERVICE_ACCOUNT" --allow-unauthenticated \
  --port=8080 --cpu=1 --memory=1Gi --concurrency=1 --min-instances=0 --max-instances=2 \
  --timeout=180 --set-env-vars=SMA2_DATA_MODE=live,SMA2_REPORT_BUCKET=$GCP_PROJECT_ID-sma2-reports,SMA2_APP_REVISION=${GITHUB_SHA:-manual} --set-secrets=/secrets/nm.lic=sma2-nm-license:latest --quiet
url="$(gcloud run services describe algoquant-tools --project="$GCP_PROJECT_ID" --region="$region" --format='value(status.url)')"
printf 'Service URL: %s\n' "$url"
curl --fail --show-error --max-time 30 --retry 8 --retry-all-errors --retry-delay 5 "$url/api/health"
printf '\nDeployed: %s\n' "$url"

# Verify the deployed service with the runtime license and real Yahoo data, not just process health.
report="$(mktemp)"
trap 'rm -f "$report"' EXIT
curl --fail --show-error --max-time 150 --retry 2 --retry-delay 2 \
  -X POST -d 'symbol=AAPL&start=2016-01-01&end=2020-12-31&fast=5&slow=20' \
  "$url/sma2/api/backtest" -o "$report"
python3 - "$report" "$url" <<'PY'
import json, sys, urllib.request, zipfile, io, xml.etree.ElementTree as ET
r = json.load(open(sys.argv[1]))
assert r["reportPath"].startswith("/sma2/reports/")
assert r["source"] == "Yahoo Finance daily adjusted close"
assert r["sizingModel"] == "FULL_EQUITY_V1"
first=r["series"][0]["close"]
for point in r["series"]:
    assert abs(point["benchmark"]/r["initialCapital"] - point["close"]/first) < 1e-10
assert abs(r["benchmarkTotalReturn"] - (r["series"][-1]["close"]/first-1)) < 1e-10
assert abs(r["executions"][0]["quantity"]*r["executions"][0]["price"]-r["initialCapital"]) < 1e-7
assert len(r["series"]) > 1000 and len(r["executions"]) > 0
assert abs(r["finalNav"] - 10000 - r["pnl"]) < 1e-7
measures = r["performanceMeasures"]
assert len(measures) > 150 and len(r["periodMeasures"]) == 0
for m in measures:
    assert m["status"] != "unavailable", (m["label"], m["detail"])
    if m["sourceClass"].startswith("RollingAnnualReturn") or m["sourceClass"] == "TreynorRatio":
        assert m["status"] == "ok", (m["label"], m["detail"])
with urllib.request.urlopen(sys.argv[2]+"/sma2/api/reports/"+r["reportId"],timeout=30) as saved:
    assert json.load(saved) == r
with urllib.request.urlopen(sys.argv[2]+r["reportPath"],timeout=30) as page:
    assert page.status == 200
with urllib.request.urlopen(sys.argv[2]+"/sma2/api/reports/"+r["reportId"]+"/report.xlsx",timeout=60) as excel:
    with zipfile.ZipFile(io.BytesIO(excel.read())) as workbook:
        ns={"s":"http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        names=[s.attrib["name"] for s in ET.fromstring(workbook.read("xl/workbook.xml")).findall("s:sheets/s:sheet",ns)]
        assert names==["SMA2 statistics","Buy and hold statistics","S&P 500 statistics","Daily data"]
sp=r["sp500"]
assert sp["symbol"]=="^GSPC" and "Yahoo Finance ^GSPC" in sp["source"]
assert sp["start"]==r["start"] and sp["end"]==r["end"]
assert len(sp["executions"])==1 and len(sp["performanceMeasures"])==len(r["performanceMeasures"])
assert abs(sp["executions"][0]["quantity"]*sp["executions"][0]["price"]-r["initialCapital"])<1e-7
for point in sp["series"]:
    assert abs(point["nav"]/r["initialCapital"]-point["close"]/sp["series"][0]["close"])<1e-10
assert abs(sp["statistics"]["totalReturn"]-(sp["series"][-1]["close"]/sp["series"][0]["close"]-1))<1e-10
print("S&P 500 INDEPENDENT SIMULATION VERIFIED:",sp["statistics"]["totalReturn"])
print("LIVE EXCEL WORKBOOK VERIFIED: four tabs")
print("SAVED REPORT VERIFIED:", sys.argv[2]+r["reportPath"])
assert len(r["buyHoldPerformanceMeasures"])==len(measures)
assert len(r["buyHoldExecutions"])==1
assert abs(r["buyHoldStatistics"]["totalReturn"]-r["benchmarkTotalReturn"])<1e-10
print("BUY-AND-HOLD COMPARISON VERIFIED:", len(r["buyHoldPerformanceMeasures"]), "summary measures")
print("LIBRARY MEASURES VERIFIED:", len(measures), "scalar results;", len(r["periodMeasures"]), "period tables")
print("LIVE DEPLOYMENT VERIFIED:", r["symbol"], len(r["series"]), "NAV observations;", len(r["executions"]), "executions")
PY

# Verify the user-supplied public Google Sheet end to end, including benchmark reuse and durable exports.
performance_report="$(mktemp)"
curl --fail --show-error --max-time 160 --retry 1 \
  -X POST --data-urlencode 'url=https://docs.google.com/spreadsheets/d/1pODA-aiWNfXLEGmscO8MB6zkKSrjmnXq0dAtJITXrDs/edit?gid=0#gid=0' \
  "$url/performance/api/analyze" -o "$performance_report"
python3 - "$performance_report" "$url" <<'PY'
import json,sys,urllib.request,zipfile,io
r=json.load(open(sys.argv[1]))
assert r["app"]=="performance" and not r["benchmarkAdded"]
assert [a["name"] for a in r["accounts"]]==["SDC","S&P 500"]
assert r["duplicateRows"]>=3 and len(r["accounts"][0]["series"])>900
for account in r["accounts"]:
    assert abs(account["series"][0]["nav"]-10000)<1e-7
    assert len(account["performanceMeasures"])==187
    assert any(m["sourceClass"]=="ExecutionCount" and m["status"]=="not_applicable" for m in account["performanceMeasures"])
with urllib.request.urlopen(sys.argv[2]+"/performance/api/reports/"+r["reportId"],timeout=30) as saved:assert json.load(saved)==r
with urllib.request.urlopen(sys.argv[2]+"/performance/api/reports/"+r["reportId"]+"/report.xlsx",timeout=60) as exported:
    with zipfile.ZipFile(io.BytesIO(exported.read())) as workbook:assert "xl/workbook.xml" in workbook.namelist()
print("LIVE PERFORMANCE ANALYZER VERIFIED:",sys.argv[2]+r["reportPath"],len(r["accounts"][0]["series"]),"common observations")
PY
rm -f "$performance_report"
