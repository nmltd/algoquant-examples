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
import tech.nmfin.algoquant.data.cache.BufferCache;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.cache.processor.sampler.EquiTimeSampler;
import static tech.nmfin.algoquant.demo.model.factormodel.FactorModelUtils.byPeriodMeasures;
import tech.nmfin.algoquant.execution.component.simulator.*;
import tech.nmfin.algoquant.execution.datatype.fxrate.NoRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.market.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.market.db.MarketDB_HK_Compustat;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.byperiod.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.transactionfee.PercentageTransactionFeeScheme;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.composite.*;
import tech.nmfin.algoquant.model.factormodel.factor.Factor;
import tech.nmfin.algoquant.model.factormodel.factor.definition.company.FactorSize;
import tech.nmfin.algoquant.model.factormodel.factor.definition.company.accounting.FactorGrowth;
import tech.nmfin.algoquant.model.factormodel.qepm.estimation.FundamentalFactorModel;
import tech.nmfin.algoquant.model.factormodel.qepm.strategy.FactorStrategy;
import tech.nmfin.algoquant.model.factormodel.qepm.strategy.FactorStrategyParam;
import tech.nmfin.algoquant.model.factormodel.qepm.strategy.Results;
import tech.nmfin.algoquant.model.strategy.rebalance.RebalancePortfolio;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.algoquant.util.config.AQConfig;
import tech.nmfin.algoquant.util.config.ConfigProperties;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * A demo on running a fundamental factor model trading strategy.
 *
 *
 * <p><b>Using this example.</b>
 * Builds a fundamental-factor portfolio using configured accounting, return and benchmark
 * datasets. Relative and configured paths must resolve to compatible Compustat data; these
 * inputs are distinct from public price downloads. Rebalance frequency and trailing estimation
 * windows control when the model can trade.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Factor models separate asset characteristics/exposures from returns attributable to common
 * drivers. Fama–French is related factor literature, not the source of every characteristic or
 * estimation choice used here.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * The active FundamentalFactorModel uses FactorSize and FactorGrowth with SIMPLE returns. This
 * differs from EconomicModelStrategyDemo’s market/SMB/HML factor-return specification.
 * FactorStrategyParam supplies rebalance, risk and data-window settings, while the configured
 * accounting dataset supplies characteristics; avoid treating a publication date or revised
 * statement as information available earlier.
 * </p>
 * @author Haksun Li
 * @see <a href="https://doi.org/10.1016/0304-405X(93)90023-5">E. F. Fama and K. R. French (1993), Common risk factors in the returns on stocks and bonds, Journal of Financial Economics 33(1), 3–56</a>
 */
@Demo
public class FundamentalModelStrategyDemo {

    private static final Logger LOGGER = getClassLogger();

    // set up the data sources
    private static final String DATA_FOLDER
        = AQConfig.defaultConfig().getString(ConfigProperties.DATA_FOLDER.propertyName());
    private static final String DATA_COMPUSTAT_HK
        = AQConfig.defaultConfig().getString(ConfigProperties.DATA_COMPUSTAT_HK.propertyName());
    private static final MarketDB_HK_Compustat hk
        = new MarketDB_HK_Compustat(
            String.format("%s/company.csv.zip", DATA_COMPUSTAT_HK),
            String.format("%s/monthlyAccounting.1995.2010.csv.zip", DATA_COMPUSTAT_HK),
            String.format("%s/dailyReturn.1995.2011.csv.zip", DATA_COMPUSTAT_HK),
            String.format("%s/HSI.csv.zip", DATA_FOLDER),
            String.format("%s/HKMA/T060301.xls", DATA_FOLDER)
        );

    public static void main(String[] args) throws Exception {
        FundamentalModelStrategyDemo demo = new FundamentalModelStrategyDemo();

        final ReturnsCalculators returnCalc = ReturnsCalculators.SIMPLE;
        // the factors used for the regression
        final List<Factor> factors
            = Arrays.asList(
                new FactorSize(),
                new FactorGrowth());
        final Period dataWindow = Period.ofYears(3);
        final double riskParameter = 0.5;
        final Period rebalanceFreq = Period.ofMonths(1);
        final double initialCapital = 1_000_000.;
        final double transactionFeeRate = 0.0025;

        FactorStrategyParam param
            = new FactorStrategyParam(
                factors,
                dataWindow,
                riskParameter,
                new FundamentalFactorModel(factors, returnCalc)
            );
        param.setRebalanceFreq(rebalanceFreq);
        param.setInitialCapital(initialCapital);
        param.setTransactionFeeRate(transactionFeeRate);
        param.setReturnsCalculator(returnCalc);

        final LocalDateTimeInterval simInterval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2005, 1, 1, 16, 0),
                LocalDateTime.of(2011, 1, 1, 16, 0)
            );

        demo.run(param, simInterval);
    }

    public Results run(
        FactorStrategyParam param,
        LocalDateTimeInterval interval
    ) throws Exception {
        final LocalDateTimeInterval dataInterval
            = LocalDateTimeInterval.of(
                interval.start().minus(param.dataWindow())
                    .minus(param.rebalanceFreq().multipliedBy(2)),
                interval.end()
            );

        MarketHistory marketHistory0
            = hk.getMarketHistory(dataInterval, null);

        MarketHistory marketHistory
            = MarketHistoryProcessor.process(marketHistory0)
                .by(new EquiTimeSampler<>(dataInterval.start(), param.rebalanceFreq()))
                .save();

        int count = 0;
        for (TimedEntry<MarketSnapshot> market : marketHistory) {
//            LOGGER.info(market.toString());
            ++count;
        }
        LOGGER.info("number of days = {}", count);

        FactorStrategy strategy
            = new FactorStrategy(param);
        // Deliver calibration observations without allowing any pre-period trades.
        strategy.warmUp(marketHistory, interval.start());

        OrderBookCaches orderBookCaches
            = MarketSnapshotUtils.getOrderBookCaches(
                marketHistory
            );

        // set up a simulator to host the strategy   
        final double initialCapital = param.initialCapital();
        final double transactionFeeRate = param.transactionFeeRate();

        BenchmarkLookup benchmarkLookup = FactorModelUtils.newBenchmark("^HSI", interval);
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
                    new AverageAnnualRateOfReturn(interval),
                    new CommissionProfitRatio(transactionFeeRate),
                    new MaxDrawdown(),
                    new MaxDrawdownPercentage(),
                    new MaxDrawdownDuration(Duration.ofDays(1)),
                    new BetaCAPM(interval, Period.ofYears(1), benchmarkLookup, ReturnsCalculators.SIMPLE),
                    new AlphaCAPM(interval, Period.ofYears(1), benchmarkLookup, ReturnsCalculators.SIMPLE),
                    // The period is a calendar return bucket; the constant benchmark is a return per bucket.
                    new InformationRatioForPeriods(Period.ofYears(1), ReturnsCalculators.SIMPLE, 0.),
                    // The threshold is expressed as a return per selected period, not an annual cash rate.
                    new OmegaForPeriods(Period.ofYears(1), ReturnsCalculators.SIMPLE, 0.),
                    new SortinoRatio(0., interval, Period.ofYears(1)),
                    new CalmarRatio(interval),
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
                param.rebalanceFreq()
            );

        ChainSimulatorBuilder chainMarketSnapshots
            = new ChainMarketSnapshots(marketHistory);

        SimulatorBuilder builder1 = chainPeriodicTimer.chain(builder0);
        SimulatorBuilder builder2 = chainMarketSnapshots.chain(builder1);
        Simulator simulator = builder2.build();

        // run the strategy
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, interval);

        Map<PeriodByPeriodMeasure, List<IntervalValue>> byPeriodMeasures
            = byPeriodMeasures(
                report.tradeBlotter().allExecutions(),
                orderBookCaches,
                interval,
                Period.ofYears(1),
                Arrays.asList(
                    new PeriodByPeriodReturns(initialCapital, ReturnsCalculators.SIMPLE),
                    new PeriodByPeriodExcessReturns(initialCapital, benchmarkLookup, ReturnsCalculators.SIMPLE),
                    new PeriodByPeriodInformationRatio(initialCapital, Period.ofMonths(1), ReturnsCalculators.SIMPLE, 0.),
                    new PeriodByPeriodTradingVolume()
                )
            );

        BufferCache<Double> profitHistory
            = MeasureUtils.computeProfitCache(
                report.tradeBlotter().allExecutions(),
                orderBookCaches,
                new NoRateTable()
            );

        Results results
            = new Results(
                report,
                byPeriodMeasures,
                strategy.exposuresHistory(),
                strategy.premiumsHistory(),
                strategy.weightsHistory(),
                strategy.positionsHistory(),
                strategy.snapshotHistory(),
                profitHistory
            );

        LOGGER.info("results:{}", results);
        return results;
    }

}
