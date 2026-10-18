# Learning AlgoQuant from the examples

Read a class's Javadoc before running its `main` method. It explains the inputs,
important library calls, outputs and assumptions. Run commands from the repository
root so `config/`, `test-data/` and `log/` paths resolve correctly.

For research-based models, see the [model literature guide](model-literature.md) for original publications, access limits and implementation differences.

## Suggested reading order

1. [SMA2Demo010](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo010.java):
   product → data cache → strategy → simulator builder → performance report.
2. [SMA2Demo011](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo011.java):
   compare its currency conversion and measure configuration with the first example.
3. [SMA2Demo020](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo020.java):
   express simulation setup as a reusable template.
4. [TutorialStrategy](../src/main/java/tech/nmfin/algoquant/demo/model/tutorial/TutorialStrategy.java):
   implement `Strategy.onEvent`, read `TraderContext`, compute target minus filled
   position, and submit signed-unit orders. Its companion demo uses Yahoo data and charts.
5. [BatchParameterizedSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/BatchParameterizedSimulationDemo.java)
   and [SMA2DynamicDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2DynamicDemo.java):
   factories, parameter searches, trailing calibration windows and result exports.
6. [PortfolioOptimizationAlgorithmDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimizationAlgorithmDemo.java):
   construct an optimizer and map its weight vector back to products.
7. [SMA2App](../src/main/java/tech/nmfin/algoquant/web/sma2/SMA2App.java)
   and [PerformanceAnalyzer](../src/main/java/tech/nmfin/algoquant/web/performance/PerformanceAnalyzer.java):
   funded comparisons and reusable report services in complete applications.

## Build and run one lesson

Follow the root [setup instructions](../README.md#setup) for JDK 8, Maven 3.9.9,
GitHub Packages credentials and a valid license. Compilation alone does not run
strategies or validate market-data availability.

```bash
mvn -U -Dmaven.test.skip=true clean package
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo010
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2DynamicDemo -Dexec.args="--quick"
```

The six technical examples default to synthetic weekday data and headless plotting.
Their `DemoSupport.initialize(args)` loads `license/nm.lic`, or the file specified by
`-Dnm.license=/absolute/path/nm.lic`. The `--live` and `--charts` flags are specific
to these examples; they are not universal options for the imported legacy demos.
Legacy examples may rely on the library's default license lookup; check its license
configuration before running them. The web host uses `NM_LICENSE_FILE` instead.
Do not use `RunAllDemo` as a quick offline check: it discovers a mixed set of demos.

## Conventions to learn before changing parameters

| Concept | Meaning in these examples |
| --- | --- |
| Price observations | SMA lags count observations: daily inputs mean trading sessions, not calendar days. Monthly sampling changes that interpretation. |
| Account cash | `withInitialCash` sets funding. It does not resize an arbitrary strategy's orders. |
| Orders | Positive quantity buys; negative quantity sells. Quantities are product units, not cash amounts. Target minus current position avoids accumulating the target on every event. |
| Fills | `sendOrder` submits an order. Executions and filled positions depend on the broker or simulation execution model. |
| State | Construct a fresh strategy and performance measures per independent run; rolling indicators and previous prices are mutable. |
| Optimization | Return matrices generally have observations in rows and assets in columns. Keep products in the same column order. NM vectors and matrices use one-based indices. |
| Measures | `withMeasures` replaces the default selection. Register what you need and distinguish account, trade and period statistics. Undefined ratios are not zero returns. |
| Periods | Calendar return buckets are separate from indicator lags. Constant benchmark/threshold inputs must match the measure's return-period convention. |
| Prices | Adjusted stock closes and the S&P 500 price index have different dividend conventions. Do not add dividend cash flows again to already-adjusted inputs. |
| Historical data | Availability filters and static constituent lists can introduce selection bias. Estimation data must precede decisions in an out-of-sample backtest. |

The fixed-unit library `SMA2` targets long or short unit holdings. The web `SMA2App`
uses a custom full-equity strategy. Their returns are not directly interchangeable.

## Demo source catalog

The requirements column lists dependencies referenced by the source; it does not
claim that every path is exercised on every run or that external services are available.
Review local paths and settings inside the selected example.

| Source | Referenced inputs or runtime needs |
| --- | --- |
| [RunAllDemo](../src/main/java/tech/nmfin/algoquant/demo/RunAllDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [CandleStickChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/CandleStickChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [MultiChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/MultiChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [OHLCChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/OHLCChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [RangeBarsChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/RangeBarsChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [TimeSeriesChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/TimeSeriesChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [XYSeriesChartDemo](../src/main/java/tech/nmfin/algoquant/demo/chart/XYSeriesChartDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [SGXTickDataCacheFactoryDemo](../src/main/java/tech/nmfin/algoquant/demo/data/SGXTickDataCacheFactoryDemo.java) | Local datasets |
| [IBDemoStrategy](../src/main/java/tech/nmfin/algoquant/demo/ib/IBDemoStrategy.java) | Broker integration |
| [IBStrategyServerDemo](../src/main/java/tech/nmfin/algoquant/demo/ib/IBStrategyServerDemo.java) | Broker integration; Google Sheets integration |
| [BatchParameterizedSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/BatchParameterizedSimulationDemo.java) | Yahoo/cached history |
| [BootstrapSimulationSMA2Demo](../src/main/java/tech/nmfin/algoquant/demo/misc/BootstrapSimulationSMA2Demo.java) | Yahoo/cached history |
| [MCSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/MCSimulationDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [MCSimulationTaskRunnerDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/MCSimulationTaskRunnerDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [OtcBrokerSimDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/OtcBrokerSimDemo.java) | Yahoo/cached history |
| [SensitivityAnalyzerDemo](../src/main/java/tech/nmfin/algoquant/demo/misc/SensitivityAnalyzerDemo.java) | Yahoo/cached history |
| [BootstrapSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/dai2011/BootstrapSimulationDemo.java) | Yahoo/cached history; Desktop plotting |
| [Dai2011LongOnlyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/dai2011/Dai2011LongOnlyDemo.java) | Yahoo/cached history; Desktop plotting |
| [KagiMRBasketSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/KagiMRBasketSimulationDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [MRSimParamSP500](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/MRSimParamSP500.java) | Yahoo/cached history |
| [MultiPeriodKagiMRStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/MultiPeriodKagiMRStrategyDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [Elliott2005StrategyDemo1](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/Elliott2005StrategyDemo1.java) | Yahoo/cached history; Desktop plotting |
| [Elliott2005StrategyDemo2](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/Elliott2005StrategyDemo2.java) | Yahoo/cached history; Desktop plotting |
| [MeanReversionBasedOnSpreadQuantileDemo](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/MeanReversionBasedOnSpreadQuantileDemo.java) | Yahoo/cached history; Desktop plotting |
| [EconomicModelStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/EconomicModelStrategyDemo.java) | Yahoo/cached history; Database configuration; Compustat data/configuration |
| [FactorModelUtils](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/FactorModelUtils.java) | Yahoo/cached history |
| [FundamentalModelStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/FundamentalModelStrategyDemo.java) | Compustat data/configuration |
| [Infantino2010ParamAnalysisDemo](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/Infantino2010ParamAnalysisDemo.java) | Yahoo/cached history |
| [Infantino2010PredictionAnalysisDemo](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/Infantino2010PredictionAnalysisDemo.java) | Yahoo/cached history |
| [TestData](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/TestData.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [JanuaryEffectStrategy](../src/main/java/tech/nmfin/algoquant/demo/model/januaryeffect/JanuaryEffectStrategy.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [JanuaryEffectStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/januaryeffect/JanuaryEffectStrategyDemo.java) | Desktop plotting |
| [OULogSpreadCRRASignalDemo](../src/main/java/tech/nmfin/algoquant/demo/model/jurek2007/OULogSpreadCRRASignalDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [GMA12Demo](../src/main/java/tech/nmfin/algoquant/demo/model/kst1995/GMA12Demo.java) | Yahoo/cached history |
| [KnightSatchellTran1995MLEDemo](../src/main/java/tech/nmfin/algoquant/demo/model/kst1995/KnightSatchellTran1995MLEDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [Corvalan2005Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/Corvalan2005Demo.java) | Yahoo/cached history |
| [CovarianceComparisonDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/CovarianceComparisonDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [Lai2010NPEBModelDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/Lai2010NPEBModelDemo.java) | Yahoo/cached history |
| [MarkowitzBigDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/MarkowitzBigDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [MarkowitzDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/MarkowitzDemo.java) | Yahoo/cached history |
| [PerformanceOptimizedYahooEODPortfolioDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PerformanceOptimizedYahooEODPortfolioDemo.java) | Yahoo/cached history |
| [PortfolioOptimComparisons1Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimComparisons1Demo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [PortfolioOptimComparisons2Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimComparisons2Demo.java) | Yahoo/cached history; Local datasets; Compustat data/configuration |
| [PortfolioOptimizationAlgorithmDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimizationAlgorithmDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [PortfolioOptimizationBacktestDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimizationBacktestDemo.java) | Yahoo/cached history |
| [RandomReturns](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/RandomReturns.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [SOCPMarkowitzComparisonDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/SOCPMarkowitzComparisonDemo.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [CoinBacktestDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/coin/CoinBacktestDemo.java) | Local datasets |
| [CSI300SimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/csi300/CSI300SimulationDemo.java) | Local datasets |
| [AccumulationDistributionLineDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/AccumulationDistributionLineDemo.java) | Synthetic by default; license; optional Yahoo/charts |
| [ChaikinMoneyFlowDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/ChaikinMoneyFlowDemo.java) | Synthetic by default; license; optional Yahoo/charts |
| [DemoSupport](../src/main/java/tech/nmfin/algoquant/demo/model/technical/DemoSupport.java) | Synthetic by default; license; optional Yahoo/charts |
| [SMA2Demo010](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo010.java) | Synthetic by default; license; optional Yahoo/charts |
| [SMA2Demo011](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo011.java) | Synthetic by default; license; optional Yahoo/charts |
| [SMA2Demo020](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo020.java) | Synthetic by default; license; optional Yahoo/charts |
| [SMA2DynamicDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2DynamicDemo.java) | Synthetic by default; license; optional Yahoo/charts |
| [TutorialStrategy](../src/main/java/tech/nmfin/algoquant/demo/model/tutorial/TutorialStrategy.java) | Generated/embedded inputs or caller-supplied configuration; see class Javadoc |
| [TutorialStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/tutorial/TutorialStrategyDemo.java) | Yahoo/cached history; Desktop plotting |
| [MRModelFixedGridDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelFixedGridDemo.java) | Local datasets; Desktop plotting |
| [MRModelFixedGridOnSGXDataDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelFixedGridOnSGXDataDemo.java) | Local datasets; Desktop plotting |
| [MRModelPeriodicDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelPeriodicDemo.java) | Local datasets; Desktop plotting |
| [MRModelRanged1Demo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelRanged1Demo.java) | Local datasets; Desktop plotting |
| [MRModelRanged2Demo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelRanged2Demo.java) | Local datasets; Desktop plotting |
| [MeanReversionPortfolioDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MeanReversionPortfolioDemo.java) | Local datasets; Desktop plotting |

## Follow the shared application components

| Source | Library lesson |
| --- | --- |
| [BuyAndHoldBenchmark](../src/main/java/tech/nmfin/algoquant/web/BuyAndHoldBenchmark.java) | Independent S&P 500 account and date-keyed benchmark data |
| [AllPerformanceMeasures](../src/main/java/tech/nmfin/algoquant/web/AllPerformanceMeasures.java) | Explicit library measure catalog, inputs, units and availability |
| [NavSeriesAnalysis](../src/main/java/tech/nmfin/algoquant/web/NavSeriesAnalysis.java) | Compute NAV-supported measures without inventing trades |
| [PerformanceCsv](../src/main/java/tech/nmfin/algoquant/web/PerformanceCsv.java) | Adapt saved scalar values to PerformanceReportCsvExporter |
| [SpreadsheetWorkbook](../src/main/java/tech/nmfin/algoquant/web/SpreadsheetWorkbook.java) | Package scalar statistics and daily data into separate sheets |
| [ReportStore](../src/main/java/tech/nmfin/algoquant/web/ReportStore.java) | Save results once and export/reload without rerunning |

See the [AlgoQuant API Javadoc](https://nmltd.github.io/algoquant/) for complete
library contracts, the [web guide](../web/README.md) for hosting and configuration,
and the [audit notes](tutorial-audit.md) for existing example limitations.
