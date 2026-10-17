# DemoSupport

This is the seventh Java file, a shared helper rather than an executable strategy.

- Validates `--live`, `--charts` and `--quick` options.
- Loads the external license before licensed library objects are constructed.
- Selects deterministic offline bars or cached Yahoo downloads.
- Adapts adjusted closes to order-book caches.
- Selects a no-op plotter by default for headless runs.
- Checks nonempty finite NAV values and prints the execution count.

`--quick` affects the dynamic demo's date range and parameter grid. Options are process-wide; the offline runner uses a separate JVM for each demo. The helper does not check that every derived statistic is finite and does not require a trade.

See [[Data-and-methodology]] for generation details and [[Reproduce-the-results]] for usage.
