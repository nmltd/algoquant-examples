# AlgoQuant demo results

These results demonstrate the public obfuscated AlgoQuant library and its example APIs. **All prices and volumes in this report are deterministic synthetic data, not historical observations.** Stock symbols label the example configurations. These numbers do not establish real-market profitability.

Verified run: [GitHub Actions run 38997285630](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630), executed **2026-10-17 03:03 UTC** (October 16, 23:03 in New York). Source commit: `b2d3241bb9de428f1e43ab9054d44479449c2bad`; the later comment-only commit `b55a1e3` preserves executable tokens. Environment: Ubuntu, Temurin Java 8, Maven 3.9.9, `tech.nmfin:algoquant-dist:3.3.0-SNAPSHOT`.

## Results overview

P&L is the library's raw monetary result, **not a percentage return**. Amounts across different currency configurations are not directly comparable. Values below are rounded; individual pages retain captured output.

| Demo | Label | NAV observations | Executions | P&L | Trade information ratio | Trade Omega |
|---|---|---:|---:|---:|---:|---:|
| [[SMA2Demo010]] | ^HSCE | 1580 | 65 | 1015.941925 | 4.671747 | Infinity |
| [[SMA2Demo011]] | META | 846 | 35 | 531.903015 | 5.120161 | Infinity |
| [[SMA2Demo020]] | XOM | 263 | 11 | 168.880699 | 4.956692 | Infinity |
| [[AccumulationDistributionLineDemo]] | AAPL | 1304 | 103 | 320.358462 | 1.018946 | 20.224689 |
| [[ChaikinMoneyFlowDemo]] | FCX | 232 | 16 | -12.479865 | -0.328490 | -0.562450 |
| [[SMA2DynamicDemo]] | 000001.SS | 65 | 1 | -1.667327 | not reported | NaN |

## Read the results

- [[Data-and-methodology]] explains price generation, event inputs and metric limitations.
- [[Reproduce-the-results]] provides setup and run commands.
- [[DemoSupport]] explains the shared helper (it is not a seventh trading strategy).

`Infinity` and `NaN` are preserved from the library output. They are not finite performance scores. In particular, the reported trade-level information ratio is not labeled as an annualized portfolio Sharpe ratio. A successful run checks execution and finite NAV, not statistical validity of every metric.

These pages record one verified run. They do not claim to update automatically after subsequent code or dependency changes.
