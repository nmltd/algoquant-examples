/*
 * Copyright (c) Numerical Method Inc.
 * http://www.numericalmethod.com/
 *
 * THIS SOFTWARE IS LICENSED, NOT SOLD.
 *
 * YOU MAY USE THIS SOFTWARE ONLY AS DESCRIBED IN THE LICENSE.
 * IF YOU ARE NOT AWARE OF AND/OR DO NOT AGREE TO THE TERMS OF THE LICENSE,
 * DO NOT USE THIS SOFTWARE.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITH NO WARRANTY WHATSOEVER,
 * EITHER EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION,
 * ANY WARRANTIES OF ACCURACY, ACCESSIBILITY, COMPLETENESS,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABILITY, NON-INFRINGEMENT,
 * TITLE AND USEFULNESS.
 *
 * IN NO EVENT AND UNDER NO LEGAL THEORY,
 * WHETHER IN ACTION, CONTRACT, NEGLIGENCE, TORT, OR OTHERWISE,
 * SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIMS, DAMAGES OR OTHER LIABILITIES,
 * ARISING AS A RESULT OF USING OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tech.nmfin.algoquant.demo.model.factormodel;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.*;
import static tech.nmfin.algoquant.data.cache.SequentialCache.subCache;
import tech.nmfin.algoquant.data.cache.processor.sampler.EquiTimeSampler;
import tech.nmfin.algoquant.data.historicaldata.compustat.sql.CompustatSQLSP500;
import static tech.nmfin.algoquant.demo.model.factormodel.FactorModelUtils.byPeriodMeasures;
import tech.nmfin.algoquant.execution.component.simulator.*;
import tech.nmfin.algoquant.execution.datatype.fxrate.NoRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.market.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.market.db.*;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.byperiod.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.transactionfee.PercentageTransactionFeeScheme;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.composite.*;
import tech.nmfin.algoquant.model.factormodel.factor.Factor;
import tech.nmfin.algoquant.model.factormodel.factor.definition.company.FactorSMB;
import tech.nmfin.algoquant.model.factormodel.factor.definition.company.accounting.FactorHML;
import tech.nmfin.algoquant.model.factormodel.factor.definition.market.FactorExcessBenchmark;
import tech.nmfin.algoquant.model.factormodel.qepm.estimation.EconomicFactorModel;
import tech.nmfin.algoquant.model.factormodel.qepm.strategy.*;
import static tech.nmfin.algoquant.model.factormodel.qepm.strategy.FactoryStrategySimParam.DataSource.HK_23_QUANT;
import static tech.nmfin.algoquant.model.factormodel.qepm.strategy.FactoryStrategySimParam.DataSource.USA_23_QUANT;
import tech.nmfin.algoquant.model.portfoliooptimization.corvalan2005.Corvalan2005Diversifier;
import tech.nmfin.algoquant.model.portfoliooptimization.MarkowitzOptimizationAlgorithm;
import tech.nmfin.algoquant.model.strategy.rebalance.RebalancePortfolio;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.algoquant.util.config.AQConfig;
import tech.nmfin.algoquant.util.config.ConfigProperties;
import tech.nmfin.algoquant.util.sql.SQLParam;
import tech.nmfin.portfoliooptimization.corvalan2005.Corvalan2005;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * A demo on running a fundamental factor model trading strategy.
 *
 *
 * <p><b>Using this example.</b>
 * Builds a FactorStrategy using economic factors and a rolling data window, then schedules
 * rebalancing and registers account measures. This example needs the configured
 * Compustat/filesystem or SQL data source; public Yahoo prices alone do not supply its factor
 * data. Keep calibration data earlier than the trade decisions when changing windows.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Fama–French is background for the market/size/value factor vocabulary. The linked author
 * documentation defines SMB and HML; this application is not a reproduction of the original
 * stock-and-bond empirical study.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * FactorExcessBenchmark, FactorSMB and FactorHML feed EconomicFactorModel, which estimates
 * exposures/premia over the configured trailing window. FactorStrategy then uses a chosen
 * optimizer and diversification layer to rebalance. Factor measurement, database construction
 * and reporting dates must be consistent; financial statement availability dates matter. The
 * code’s factor implementations, universe and optimizer choices define this experiment.
 * </p>
 * @author Haksun Li
 * @see <a href="https://doi.org/10.1016/0304-405X(93)90023-5">E. F. Fama and K. R. French (1993), Common risk factors in the returns on stocks and bonds, Journal of Financial Economics 33(1), 3–56</a>
 */
@Demo
public class EconomicModelStrategyDemo {

    private static final Logger LOGGER = getClassLogger();

    // The default HK example uses files. Optional USA data require a separately
    // configured database; credentials must come from the caller's environment.
    private static SQLParam sqlParam() {
        return new SQLParam(
            System.getenv("AQ_DATABASE_HOST"),
            System.getenv("AQ_DATABASE_NAME"),
            System.getenv("AQ_DATABASE_USER"),
            System.getenv("AQ_DATABASE_PASSWORD")
        );
    }

    public static void main(String[] args) throws Exception {
        /**
         * strategy parameters
         */
        final ReturnsCalculators returnCalc = ReturnsCalculators.SIMPLE;
        final Period dataWindow = Period.ofYears(3);
        final Period rebalanceFreq = Period.ofMonths(3);
        final double riskParameter = 0.5;
        final double initialCapital = 1_000_000.;
        final double transactionFeeRate = 0.0025;
        final List<Factor> factors // the factors used for the regression
            = Arrays.asList(
                new FactorExcessBenchmark(rebalanceFreq),
                new FactorSMB(),
                new FactorHML());
        FactorStrategyParam strategyParam
            = new FactorStrategyParam(
                factors,
                dataWindow,
                riskParameter,
                new EconomicFactorModel(factors, returnCalc, dataWindow, rebalanceFreq)
            );
        strategyParam.setRebalanceFreq(rebalanceFreq);
        strategyParam.setInitialCapital(initialCapital);
        strategyParam.setTransactionFeeRate(transactionFeeRate);
        strategyParam.setReturnsCalculator(returnCalc);
        strategyParam.setOptimizerFactory((int dim)
            -> new Corvalan2005Diversifier(
                new MarkowitzOptimizationAlgorithm(riskParameter),
                new Corvalan2005(0.2, 0.2)
            )
        );

        /**
         * simulation parameters
         */
        final LocalDateTimeInterval simInterval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2006, 1, 1, 16, 0),
                //                new DateTime(2017, 1, 1, 16, 0)
                LocalDateTime.of(2009, 6, 1, 16, 0)
            );

//        // big cap + liquid
        FactoryStrategySimParam simParam
            = new FactoryStrategySimParam(
                HK_23_QUANT,
                simInterval,
                0., 1e12,
                1e10, 1e12,
                1e6, 1e12
            );
        // mid+big cap + liquid
//        FactoryStrategySimParam simParam
//            = new FactoryStrategySimParam(
//                USA_23_QUANT,
//                simInterval,
//                0., 1e12,
//                2e9, 1e12,
//                1e6, 1e12
//            );
        // mid+big cap + liquid + blue chip
//        FactoryStrategySimParam simParam
//            = new FactoryStrategySimParam(
//                USA_23_QUANT,
//                simInterval,
//                25., 1e12,
//                2e9, 1e12,
//                1e6, 1e12
//                );
        // all
//        FactoryStrategySimParam simParam
//            = new FactoryStrategySimParam(
//                USA_23_QUANT,
//                simInterval,
//                0., 1e12,
//                0., 1e12,
//                0., 1e12
//            );

        EconomicModelStrategyDemo demo = new EconomicModelStrategyDemo();
        demo.run(simParam, strategyParam);
    }

    public Results run(
        FactoryStrategySimParam simParam,
        FactorStrategyParam strategyParam
    ) throws Exception {
        java.time.Instant start = java.time.Instant.now();

        final LocalDateTimeInterval simInterval = simParam.simInterval();
        final LocalDateTimeInterval dataInterval
            = LocalDateTimeInterval.of(
                // Include six-month accounting lags, factor holding periods and sampling boundaries.
                simInterval.start().minus(strategyParam.dataWindow())
                    .minus(Period.ofYears(1)).minus(strategyParam.rebalanceFreq()),
                simInterval.end()
            );

        // set up the data sources
        final MarketDB marketDB
            = getMarketDB(
                simParam,
                dataInterval,
                strategyParam.rebalanceFreq()
            );

        MarketHistory marketHistory0
            = marketDB.getMarketHistory(dataInterval, null);

        MarketHistory marketHistory
            = MarketHistoryProcessor.process(marketHistory0)
                .by(new EquiTimeSampler<>(dataInterval.start(), strategyParam.rebalanceFreq()))
                .save();

        int count = 0;
        for (TimedEntry<MarketSnapshot> market : marketHistory) {
//            LOGGER.info(market.toString());
            ++count;
        }
        LOGGER.info("number of days = {}", count);

        FactorStrategy strategy
            = new FactorStrategy(strategyParam);
        strategy.warmUp(marketHistory, simInterval.start());

        OrderBookCaches orderBookCaches
            = MarketSnapshotUtils.getOrderBookCaches(
                marketHistory
            );

        // set up a simulator to host the strategy        
        final double initialCapital = strategyParam.initialCapital();
        final double transactionFeeRate = strategyParam.transactionFeeRate();

        BenchmarkLookup benchmarkLookup
            = newBenchmarkLookup(simParam.dataSource(), dataInterval);
        SimulatorBuilder builder0
            = new SimpleSimulatorBuilder()
                // Initial cash funds the account; order quantities are determined by the strategy.
                .withInitialCash(initialCapital)
                .withTransactionFeeScheme(new PercentageTransactionFeeScheme(transactionFeeRate))
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(orderBookCaches)
                // Explicit measures replace the default measure selection; keep these instances per run.
                .withMeasures(KeyedMeasure.measures(
                    new ProfitLoss(),
                    new TransactionFeeByPercentage(transactionFeeRate),
                    new AverageAnnualRateOfReturn(simInterval),
                    new CommissionProfitRatio(transactionFeeRate),
                    new MaxDrawdown(),
                    new MaxDrawdownPercentage(),
                    new MaxDrawdownDuration(Duration.ofDays(1)),
                    new BetaCAPM(simInterval, Period.ofYears(1), benchmarkLookup, ReturnsCalculators.SIMPLE),
                    new AlphaCAPM(simInterval, Period.ofYears(1), benchmarkLookup, ReturnsCalculators.SIMPLE),
                    // The period is a calendar return bucket; the constant benchmark is a return per bucket.
                    new InformationRatioForPeriods(Period.ofYears(1), ReturnsCalculators.SIMPLE, 0.),
                    // The threshold is expressed as a return per selected period, not an annual cash rate.
                    new OmegaForPeriods(Period.ofYears(1), ReturnsCalculators.SIMPLE, 0.),
                    new SortinoRatio(0., simInterval, Period.ofYears(1)),
                    new CalmarRatio(simInterval),
                    new ExecutionCount()
                ));

        // need more data before simulation begin time to do analysis
        ChainSimulatorBuilder chainPeriodicTimer
            = new ChainPeriodicTimer(
                RebalancePortfolio.REBALANCE_TIMER_ID,
                LocalDateTimeInterval.of(
                    dataInterval.start().plusNanos(1),
                    dataInterval.end().plusNanos(1)
                ),
                strategyParam.rebalanceFreq()
            );

        ChainSimulatorBuilder chainMarketSnapshots
            = new ChainMarketSnapshots(marketHistory);

        SimulatorBuilder builder1 = chainPeriodicTimer.chain(builder0);
        SimulatorBuilder builder2 = chainMarketSnapshots.chain(builder1);
        Simulator simulator = builder2.build();

        // run the strategy
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, simInterval);

        Map<PeriodByPeriodMeasure, List<IntervalValue>> byPeriodMeasures
            = byPeriodMeasures(
                report.tradeBlotter().allExecutions(),
                orderBookCaches,
                simInterval,
                Period.ofYears(1),
                Arrays.asList(
                    new PeriodByPeriodReturns(initialCapital, ReturnsCalculators.SIMPLE),
                    new PeriodByPeriodExcessReturns(initialCapital, benchmarkLookup, ReturnsCalculators.SIMPLE),
                    new PeriodByPeriodInformationRatio(initialCapital, Period.ofMonths(1), ReturnsCalculators.SIMPLE, 0.),
                    new PeriodByPeriodTradingVolume()
                )
            );

        BufferCache<Double> profitHistory
            = BufferCache.fromSequentialCache(
                subCache(
                    MeasureUtils.computeProfitCache(
                        report.tradeBlotter().allExecutions(),
                        orderBookCaches,
                        new NoRateTable()
                    ),
                    simInterval
                )
            );

        Results results
            = new Results(
                report,
                byPeriodMeasures,
                subCache(strategy.exposuresHistory(), simInterval),
                subCache(strategy.premiumsHistory(), simInterval),
                subCache(strategy.weightsHistory(), simInterval),
                subCache(strategy.positionsHistory(), simInterval),
                strategy.snapshotHistory(),
                profitHistory
            );

        LOGGER.info("results:{}", results);

        java.time.Instant end = java.time.Instant.now();
        java.time.Duration duration = java.time.Duration.between(start, end);
        LOGGER.info("finished this simulation in {}", duration);

        return results;
    }

    private MarketDB getMarketDB(
        FactoryStrategySimParam simParam,
        LocalDateTimeInterval dataInterval,
        Period rebalanceFreq
    ) throws Exception {
        switch (simParam.dataSource()) {
            case HK_23_QUANT:
                final String DATA_FOLDER
                    = AQConfig.defaultConfig()
                        .getString(ConfigProperties.DATA_FOLDER.propertyName());
                final String DATA_COMPUSTAT_HK
                    = AQConfig.defaultConfig()
                        .getString(ConfigProperties.DATA_COMPUSTAT_HK.propertyName());

                return new MarketDB_HK_Compustat(
                    String.format("%s/company.csv.zip", DATA_COMPUSTAT_HK),
                    String.format("%s/monthlyAccounting.1995.2010.csv.zip", DATA_COMPUSTAT_HK),
                    String.format("%s/dailyReturn.1995.2011.csv.zip", DATA_COMPUSTAT_HK),
                    String.format("%s/HSI.csv.zip", DATA_FOLDER),
                    String.format("%s/HKMA/T060301.xls", DATA_FOLDER)
                );
            case USA_23_QUANT:
                return new MarketDB_US_Compustat(
                    sqlParam(),
                    dataInterval,
                    rebalanceFreq,
                    simParam.priceMin(),
                    simParam.priceMax(),
                    simParam.sizeMin(),
                    simParam.sizeMax(),
                    simParam.volMin(),
                    simParam.volMax(),
                    null
                );
            default:
                throw new IllegalArgumentException(
                    "unsupported data source:" + simParam.dataSource()
                );
        }
    }

    private BenchmarkLookup newBenchmarkLookup(
        FactoryStrategySimParam.DataSource dataSource,
        LocalDateTimeInterval interval
    ) throws Exception {
        switch (dataSource) {
            case USA_23_QUANT:
                RandomAccessCache<Double> marketValues
                    = new CompustatSQLSP500(sqlParam()).getMarketValues(interval);
                return new BenchmarkLookup() {
                    @Override
                    public double valueAt(LocalDateTime t) {
                        return marketValues.get(t).data();
                    }
                };
            case HK_23_QUANT:
                return FactorModelUtils.newHsiBenchmark(interval);
            default:
                throw new IllegalArgumentException(
                    "unsupported data source: " + dataSource
                );
        }
    }

}
