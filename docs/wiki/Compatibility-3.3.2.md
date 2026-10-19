# Examples compatibility with the shared EOD downloaders

AlgoQuant **3.3.2 is released**. The survey used a pre-release filename; the final release JAR is byte-for-byte identical. [Release](https://github.com/nmltd/algoquant/releases/tag/v3.3.2) · [Publication and source/checksum verification](https://github.com/nmltd/algoquant/actions/runs/39203290697). No test suites were repeated for promotion.

The survey compiles the entire examples project and exercises each public main entry point against the exact obfuscated customer JAR produced from library commit `1646f3420bff99206f49201c2c8482309f52924f`. This checks the artifact a customer uses, including its runtime linkage, rather than substituting library source classes.

- Library development version: `3.3.2-SNAPSHOT` (unpublished).
- Customer JAR SHA-256: `c120a7c5cdb0180a67319a210381a2eb5827d22794897d32d53d8b779d884f25`.
- Examples commit: `cbda3909407c549744151359aa05767f12ca8be4`.
- Full survey: https://github.com/nmltd/algoquant/actions/runs/39198266116
- Successful app checks: https://github.com/nmltd/algoquant/actions/runs/39198552618

## Compile and unit checks

All 84 main Java sources and eight test sources compile with Java 8 against the customer JAR. All 31 JUnit tests pass. These are direct `javac`/JUnit checks; they are not a claim that the examples Maven lifecycle resolved the unpublished snapshot from a public repository.

The survey exposed a test-order problem: `LocalCSI300DataProviderTest` could initialize library classes before the shared license was loaded. The examples PR now invokes its existing `TestLicense.load()` helper in `@BeforeClass`. The full suite passes with the CSI test first.

## Application checks

The synthetic HTTP test passes with 262 NAV observations and 14 executions; the live Yahoo-backed test passes with 253 observations and 11 executions. Both verify report persistence across server restart and exports. The SMA2 and Performance Analyzer Chrome suites pass, covering chart interaction, report navigation/refresh and downloads. The locally built container passes its health and synthetic backtest checks; no container was deployed or published.

The application runner must execute the example unit tests before the browser checks: `PerformanceAnalyzerTest` generates the JSON/XLSX fixture consumed by `performance-browser.cjs`.

## How to interpret the survey

Each demo runs in an isolated working directory with the repository datasets and configuration linked into it. Swing charts run under Xvfb and are captured as PNGs. Technical demos use their existing synthetic `--quick` option; other demos use their original main defaults. The CSI300 default runs its two baselines; its opt-in 990-optimizer grid is not part of this survey. Broker/database and other service ports are blocked before authentication or orders. The persistent web server is covered by the dedicated app checks.

Ordinary demos have a three-minute limit. The six explicitly expensive simulations have a 30-minute limit and larger heap; the aggregate `RunAllDemo` has a 90-second limit because its children are also exercised individually. A timeout is not a completed simulation. A successful main return checks execution and exports, not financial validity or the correctness of every numerical result.

The raw survey initially counted expected Yahoo disk-cache misses as missing required datasets. The corrected classifier removes only the specific INFO-level optional-cache-miss diagnostic before inspecting failures. Logs and successful web fallbacks remain available in the run artifacts. Runtime linkage errors fail the compatibility gate; other failures remain explicitly recorded for review.

## Observed issues

- `IBStrategyServerDemo` needs service credentials and is not an operational broker test.
- `MCSimulationDemo` hit `ArrayIndexOutOfBoundsException: 624` in the numerical Mersenne Twister RNG during the hosted parallel simulation. A local retry against the same refactored JAR completed the calculation, as did ten runs against 3.3.1, before reaching the unavailable local display. This is an unresolved intermittent failure, not a verified passing hosted run. The KST model, HMM, Markov-chain, multinomial and uniform RNG classes in the failure stack are byte-for-byte identical in the two JARs.
- `KagiMRBasketSimulationDemo` and `MultiPeriodKagiMRStrategyDemo` returned but logged Yahoo HTTP 400 responses for some stocks in its historical window, including ABNB, APP and COIN for 2003–2015. Its result must not be counted as a clean run.

Reports and per-demo stdout/stderr are attached to the survey run. Customer JARs, compiled classes, credentials and downloaded input caches are not uploaded as new survey artifacts.

- `Infantino2010ParamAnalysisDemo` returned after Yahoo rejected some requested 2007–2014 stock histories, including CRWD and DDOG.
- `CSI300SimulationDemo` completed both default baselines in approximately 17 minutes and exported its reports/charts. It also logged missing constituent histories that were skipped and failed CSI300 correlation calculations assigned NaN. This is a completed run with warnings, not a clean numerical result.
- `RunAllDemo` reached its 90-second aggregate budget. Its individual children were still exercised separately by the survey.

## Final outcomes

All 57 entry points are accounted for: **49 clean demo completions, four completions with logged errors/warnings, one failed demo, one credential-blocked demo, one aggregate timeout and one web app covered by successful dedicated checks.** No runtime API linkage errors were detected.

The table below uses the corrected classifier. The original artifact JSON retains the earlier false missing-data classifications for optional Yahoo cache misses; per-demo logs are unchanged. A main returning successfully is not a financial/numerical accuracy certification.

| Entry point | Outcome | Seconds |
| --- | --- | ---: |
| `RunAllDemo` | Timed out | 90.15 |
| `CandleStickChartDemo` | Completed | 3.79 |
| `MultiChartDemo` | Completed | 7.63 |
| `OHLCChartDemo` | Completed | 5.41 |
| `RangeBarsChartDemo` | Completed | 2.83 |
| `TimeSeriesChartDemo` | Completed | 3.7 |
| `XYSeriesChartDemo` | Completed | 0.92 |
| `SGXTickDataCacheFactoryDemo` | Completed | 8.57 |
| `IBStrategyServerDemo` | Credentials required | 4.14 |
| `BatchParameterizedSimulationDemo` | Completed | 4.56 |
| `BootstrapSimulationSMA2Demo` | Completed | 18.21 |
| `MCSimulationDemo` | Failed | 6.36 |
| `MCSimulationTaskRunnerDemo` | Completed | 31.12 |
| `OtcBrokerSimDemo` | Completed | 2.23 |
| `SensitivityAnalyzerDemo` | Completed | 3.84 |
| `BootstrapSimulationDemo` | Completed | 67.43 |
| `Dai2011LongOnlyDemo` | Completed | 10.22 |
| `KagiMRBasketSimulationDemo` | Completed with errors/warnings | 150.76 |
| `MultiPeriodKagiMRStrategyDemo` | Completed with errors/warnings | 213.35 |
| `Elliott2005StrategyDemo1` | Completed | 4.78 |
| `Elliott2005StrategyDemo2` | Completed | 3.85 |
| `MeanReversionBasedOnSpreadQuantileDemo` | Completed | 7.32 |
| `EconomicModelStrategyDemo` | Completed | 36.33 |
| `FundamentalModelStrategyDemo` | Completed | 155.84 |
| `Infantino2010ParamAnalysisDemo` | Completed with errors/warnings | 92.93 |
| `Infantino2010PredictionAnalysisDemo` | Completed | 4.42 |
| `JanuaryEffectStrategyDemo` | Completed | 1.98 |
| `OULogSpreadCRRASignalDemo` | Completed | 4.35 |
| `GMA12Demo` | Completed | 1.63 |
| `KnightSatchellTran1995MLEDemo` | Completed | 0.82 |
| `Corvalan2005Demo` | Completed | 1.93 |
| `CovarianceComparisonDemo` | Completed | 77.21 |
| `Lai2010NPEBModelDemo` | Completed | 7.41 |
| `MarkowitzBigDemo` | Completed | 43.54 |
| `MarkowitzDemo` | Completed | 8.16 |
| `PerformanceOptimizedYahooEODPortfolioDemo` | Completed | 10.29 |
| `PortfolioOptimComparisons1Demo` | Completed | 1.63 |
| `PortfolioOptimComparisons2Demo` | Completed | 23.62 |
| `PortfolioOptimizationAlgorithmDemo` | Completed | 0.92 |
| `PortfolioOptimizationBacktestDemo` | Completed | 4.07 |
| `SOCPMarkowitzComparisonDemo` | Completed | 0.62 |
| `CoinBacktestDemo` | Completed | 49.28 |
| `CSI300SimulationDemo` | Completed with errors/warnings | 1039.58 |
| `AccumulationDistributionLineDemo` | Completed | 0.97 |
| `ChaikinMoneyFlowDemo` | Completed | 0.62 |
| `SMA2Demo010` | Completed | 1.03 |
| `SMA2Demo011` | Completed | 0.67 |
| `SMA2Demo020` | Completed | 0.93 |
| `SMA2DynamicDemo` | Completed | 2.03 |
| `TutorialStrategyDemo` | Completed | 2.64 |
| `MRModelFixedGridDemo` | Completed | 1.48 |
| `MRModelFixedGridOnSGXDataDemo` | Completed | 51.32 |
| `MRModelPeriodicDemo` | Completed | 1.48 |
| `MRModelRanged1Demo` | Completed | 2.59 |
| `MRModelRanged2Demo` | Completed | 1.68 |
| `MeanReversionPortfolioDemo` | Completed | 3.09 |
| `ToolsWeb` | App checks passed | Dedicated job |
