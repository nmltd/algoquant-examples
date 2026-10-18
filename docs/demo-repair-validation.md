# Demo repair validation — 18 October 2026

The maintained [execution collection](https://github.com/nmltd/algoquant-examples/wiki/Execution-records) covers 41 runnable examples: **40 completed mains and one CSI300 data blocker**. Twelve affected demos were rerun after repairs; 28 earlier completed records are retained with their original source and run IDs. This is not a claim that all 41 were rerun together. Tutorial outputs teach API usage; they do not rank quantitative strategies.

## Repairs and checks

- Parse date-only crypto observations at midnight and accept one through nine fractional-second digits in Gain Capital timestamps.
- Preserve nonempty Yahoo price matrices when selecting the earliest available date; this repairs Kagi basket calibration.
- Match legacy exclusion symbols against the available universe without looking up retired constituents.
- Separate estimation sampling from rebalancing, defer incomplete calibration, and build constraints for the actual asset universe after filtering.
- Preserve portfolio positions when calibration returns no result, and require a current price before constructing an order.
- Support SOCP problems without a linear inequality block without changing their feasible set.
- Keep nonlinear covariance estimation on its original QuEST objective and bounds when the primary numerical search fails. Preserve diagnostic causes.
- Advance immutable date cursors when counting periods, and avoid an extra Kagi period at an exact endpoint.
- Seed factor calibration before the trading interval, account for lagged accounting data, fit fixed effects across a trailing window, and select complete price histories before covariance estimation. Missing prices are not filled with zero.
- Reconstruct each product's NAV once for all NAV measures, preserving its original executions, prices, fees, FX and full quote timeline.
- Run the crypto example's 15 distinct rebalance/window combinations instead of repeating each nine times for unused risk/shrinkage parameters. Preload the union of assets that can pass its point-in-time filter; the rebalance chooser still makes each trading decision independently.
- Use the supplied local HSI benchmark archive for the historical factor tutorials, consistently with their Compustat market database.

The repaired obfuscated distribution passed seven covariance regression checks, two existing performance-analyzer tests, analytical unconstrained/equality-only SOCP checks, timestamp precision checks, period-count checks, distribution/API checks, tutorial compilation and Javadoc generation. Five additional focused calendar regression tests passed locally. **The full unit/integration suite was not run.**

The [targeted run](https://github.com/nmltd/algoquant/actions/runs/39122217560) completed all twelve repaired numerical examples. Each downloadable record identifies its public tutorial source, private-library source revisions and runtime SHA-256. Library source repositories remain private.

## CSI300 prerequisite

Yahoo returned no historical observations for **2010–2017** under either `000300.SS` or `399300.SZ` when checked on 18 October 2026. The repository's `indices/hs300.xlsx` contains constituent membership, not official index prices.

CSI300SimulationDemo now validates the index history before its large research sweep. An empty or unavailable benchmark produces a clear data-prerequisite error, rather than endless retries, a null-pointer exception or an apparently successful flat index account. A genuine index history covering the calibration/simulation period is still required; an equal-weight constituent portfolio is not a substitute for that index.

Generated CSVs, logs, charts and plotted-data downloads belong in versioned Releases and the Wiki, not in the source tree. Older releases remain available for audit and reproduction.
