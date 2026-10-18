# SMA2DynamicDemo

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2DynamicDemo.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Quick mode recalibrates monthly using a trailing three-month data window, starting with CNY 3300 cash. Each parameter combination receives a fresh simulator and independent measure accumulators. Full mode covers 2010-01-01 to 2017-03-01 and a 4-by-4 grid of rebalance periods (1 week, 2 weeks, 1 month, 3 months) and data windows (1, 2, 3, 6 months). The published run is quick mode, not the full search.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | 000001.SS |
| Requested interval (end excluded) | 2016-01-01 to 2016-04-01 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 65 |
| Executions | 1 |
| P&L | -1.667327 |
| InformationRatioForTrades | not reported |
| OmegaForTrades | NaN |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2DynamicDemo -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].

## Calibration trace and exported measures

| Recalibration time (exchange local) | Selected fast lag | Selected slow lag |
|---|---:|---:|
| 2016-01-04 15:00 | 7 | 20 |
| 2016-02-04 15:00 | 9 | 17 |
| 2016-03-04 15:00 | 3 | 13 |

Captured total and product P&L: `-1.6673265308268128`. Maximum drawdown: `14.99955558412421`; maximum drawdown fraction: `0.004527190212157116` (approximately 0.452719%). Maximum drawdown duration output is `36.0` for the total row and `36.375` for the product row, with a one-day duration unit configured. These are the exporter values as produced.

Information ratios, Omega and win/loss ratios are `NaN` in this short run. There was only one execution, so these values must not be presented as meaningful risk-adjusted estimates or replaced with zero.

The CSV exports contain performance measures, executions and trades. Permanent copies are under [GitHub Release assets](https://github.com/nmltd/algoquant-examples/releases/tag/demo-results-20261017-run-38997285630); the original [simulation-reports artifact](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630/artifacts/12087263668) includes logs but is subject to GitHub retention.
