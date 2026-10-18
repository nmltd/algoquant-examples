# CSI300 simulation tutorial

`CSI300SimulationDemo` demonstrates AlgoQuant's historical-universe and simulation APIs. Its outputs teach the workflow; they are not evidence of investment performance.

## Run

Supply genuine CSI300 index observations in a UTF-8 CSV with exactly two columns:

```csv
date,close
2009-12-01,3500.00
2009-12-02,3510.00
```

Dates must be ascending and unique, prices positive and finite. Supply the whole calibration and simulation history, not just the two illustrative rows above. Constituent membership files (including `indices/hs300.xlsx`) are not index prices. The default simulation is January 2010 through January 2017; the baseline calibration requires three preceding weeks. The full optimizer grid needs one preceding year plus two weeks. Endpoint validation allows ten calendar days for holidays; benchmark lookups reject observations older than ten days.

```sh
mvn -Dexec.mainClass=tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300.CSI300SimulationDemo \
  -Dcsi300.indexCsv=/data/csi300-index.csv \
  -Dcsi300.constituents=/data/constituent_index_300.csv \
  -Dcsi300.prices=/data/adjust_prices compile exec:java
```

Constituent prices retain the existing per-stock ZIP format and filenames (for example `000001.SZ.csv.zip`). This provider never falls back to Yahoo. The report uses the supplied CSI300 index as its benchmark and explicitly assumes a zero risk-free rate.

By default, two simulations run: buy-and-hold index and monthly equal-weight constituents. Add `-Dcsi300.fullGrid=true` for the original 990 optimizer simulations plus the baselines. The grid runs sequentially to bound concurrent simulation memory and keep result ordering reproducible; it remains an expensive research run. Progress identifies each simulation as it starts and finishes.

CSV ZIP reports and the PNG chart are saved under `log/`. GUI windows are disabled by default; add `-Dcsi300.showCharts=true` to display the chart.

## Repair validation

Compiled both modified classes with Java 8 against AlgoQuant 3.3.0-SNAPSHOT. Seven regression tests passed, covering valid CSVs, malformed/empty data, duplicate dates, missing files, index product value equality, and past-only/stale benchmark lookup. An end-to-end run with explicitly synthetic fixture prices and two constituents completed both baseline simulations, exported CSV ZIPs and a PNG headlessly. Synthetic data is solely a software test, not a substitute historical benchmark.

The real 2010–2017 index history is not bundled. That historical run and the 990-optimizer grid have not been validated by this repair. Missing genuine history now produces an actionable error before loading constituents or starting simulations.
