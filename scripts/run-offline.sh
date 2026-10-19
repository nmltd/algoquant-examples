#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for example in SMA2Demo010 SMA2Demo011 SMA2Demo020 AccumulationDistributionLineDemo ChaikinMoneyFlowDemo SMA2DynamicDemo; do
  echo "Running $example"
  timeout 300 java -Djava.awt.headless=true -Dnm.license="${NM_LICENSE_FILE:-license/nm.lic}" \
    -cp 'target/classes:target/lib/nmdev-dist-1.2.7.jar:target/lib/*' "tech.nmfin.algoquant.demo.model.technical.$example" --quick
done
