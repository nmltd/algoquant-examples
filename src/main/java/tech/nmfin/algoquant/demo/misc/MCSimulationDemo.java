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
package tech.nmfin.algoquant.demo.misc;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.misc.datastructure.time.LocalDateTimeUtils;
import dev.nm.nmutils.NMUtils;
import dev.nm.stat.distribution.univariate.EmpiricalDistribution;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Map;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.execution.component.simulator.*;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.component.simulator.market.limitorder.AlwaysFillModel;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.returns.ReturnsFromRNG;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.returns.ReturnsOrderBookCache;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.datatype.trade.calculator.SimpleTradeCalculator;
import tech.nmfin.algoquant.execution.performance.distribution.PerformanceDistribution;
import tech.nmfin.algoquant.execution.performance.measure.pertradestats.*;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measure;
import tech.nmfin.algoquant.execution.simulation.batch.montecarlo.MCSimulation;
import tech.nmfin.algoquant.execution.simulation.batch.montecarlo.MCSimulation.OrderBookCachesFactory;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.execution.strategy.StrategyFactory;
import tech.nmfin.algoquant.model.trend.kst1995.GMA12;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.MapOfExceptions;
import tech.nmfin.algoquant.util.ProgressListener;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;
import tech.nmfin.trend.kst1995.KnightSatchellTran1995;

/**
 * This example, using Monte Carlo simulation, runs the {@linkplain GMA12}
 * strategy on returns generated from a {@linkplain KnightSatchellTran1995}
 * model. The results can then be compared to the analytical results.
 */
@Demo
public final class MCSimulationDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    // Runs N_RUNS simulations. Each simulation runs for N_DAYS.
    private static final int N_DAYS = 250;
    private static final int N_RUNS = 1000;

    // KST-model parameters
    private static final double MU = 0.0;
    private static final double Q = 0.48;
    private static final double ALPHA0 = 0.7499;
    private static final double RATE0 = 99.074;
    private static final double P = 0.48;
    private static final double ALPHA1 = 1.3775;
    private static final double RATE1 = 168.36;

    // execution parameters
    private static final Product PRODUCT = HSI.getInstance();
    private static final ExchangeRateTable RATES
        = new SimpleExchangeRateTable(PRODUCT.currency());
    private static final String MEASURE_LABEL = "Return";
    private static final StrategyFactory<Strategy, Void> STRATEGY_FACTORY
        = new StrategyFactory<Strategy, Void>() {
        @Override
        public Strategy newInstance(Void no_param) {
            return new SMA2(PRODUCT, 1, 2);
        }
    };
    private static final OrderBookCachesFactory ORDER_BOOK_CACHES_FACTORY
        = new OrderBookCachesFactory() {

        @Override
        public OrderBookCaches newInstance() {
            KnightSatchellTran1995 kst = newKSTInstance();
            ReturnsOrderBookCache cache = new ReturnsOrderBookCache(
                PRODUCT,
                1.0,
                LocalDateTimeUtils.periodicInstants(
                    LocalDateTime.now(),
                    Period.ofDays(1),
                    N_DAYS - 1
                ),
                new ReturnsFromRNG(kst),
                ReturnsCalculators.LOG
            );
            return new OrderBookCaches(PRODUCT, cache);
        }
    };
//    private static final KeyedMeasure MEASURE = new ProfitLossRealized();
    private static final KeyedMeasure MEASURE
        = measure(
            "Average Return",
            new PerTradeStat(
                "Mean Return",
                new SimpleTradeCalculator(), // TODO: the trade definition does not match that in KST
                PerTradeValue.Common.RETURN,
                StatOnDistribution.Common.MEAN
            )
        );
    public static final SimulatorBuilderFactory SIMULATOR_BUILDER_FACTORY
        = new SimulatorBuilderFactory() {

        @Override
        public SimulatorBuilder newSimulatorBuilder() {
            return new SimulatorBuilder() {
                SimpleSimulatorBuilder builder = new SimpleSimulatorBuilder();

                @Override
                public SimulatorBuilder withOrderBookUpdates(
                    OrderBookCaches orderBookCaches
                ) {
                    builder.withOrderBookUpdates(orderBookCaches);
                    return this;
                }

                @Override
                public SimulatorBuilder withNonOrderBookUpdates(
                    SequentialCache<? extends Event> updates
                ) {
                    builder.withNonOrderBookUpdates(updates);
                    return this;
                }

                public SimulatorBuilder useExchangeRates(ExchangeRateTable rates) {
                    builder.useExchangeRates(rates);
                    return this;
                }

                @Override
                public Simulator build() {
                    SimpleSimulator simulator
                        = builder
                            .withMeasures(MEASURE)
                            .useLimitOrderExecutionModel(new AlwaysFillModel())
                            .build();
                    return simulator;
                }

            };
        }
    };

    public static void main(String[] args) throws MapOfExceptions {
        MCSimulation simulation
            = new MCSimulation(
                STRATEGY_FACTORY,
                ORDER_BOOK_CACHES_FACTORY,
                LocalDateTimeInterval.of(LocalDateTime.now(), Period.ofDays(N_DAYS)),
                RATES,
                SIMULATOR_BUILDER_FACTORY
            );
        Map<KeyedMeasure, PerformanceDistribution> dists
            = simulation.run(N_RUNS, new ProgressListener.NoOpProgressListener());
        EmpiricalDistribution dist = dists.get(MEASURE);
        double mean = dist.mean();
        double stdev = Math.sqrt(dist.variance() / dist.nSamples());

        KnightSatchellTran1995 model = newKSTInstance();
        GMA12 gma12 = new GMA12(model);
        double expectedReturn = gma12.ERR();

        LOGGER.info(String.format("mu: %f, standard error: %f", mean, stdev));
        LOGGER.info(String.format("E(RR) per trade: %f", expectedReturn));

        int nBins = 30;
        ChartUtils.plotHistogram(
            "MCSimulationDemo",
            MEASURE_LABEL,
            "Freq.",
            nBins,
            dist.toArray()
        ).show();
    }

    private static KnightSatchellTran1995 newKSTInstance() {
        return new KnightSatchellTran1995(MU, Q, ALPHA0, RATE0, P, ALPHA1, RATE1);
    }

    private MCSimulationDemo() {
    }
}
