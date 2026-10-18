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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HKEXStock;
import tech.nmfin.algoquant.execution.performance.measure.PerformanceMeasure;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.batch.bootstrap.BootstrapSimulation;
import tech.nmfin.algoquant.execution.simulation.batch.bootstrap.BootstrapSingleCacheFactory;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * Demonstrates how to compute the P/L distribution of a strategy using a
 * bootstrap method. That is, we run the strategy on many fake or bootstrapped
 * prices that share similar statistical properties as the original price
 * series. Each run gives one particular P/L for that hypothetical price series.
 * Together, they give a distribution of the P/L.
 *
 * <p><b>Using this example.</b>
 * Resamples an observed Yahoo price cache with BootstrapSingleCacheFactory and runs
 * independent SMA2 instances on the resamples. The resulting reports describe a hypothetical
 * P/L distribution, not multiple observations of the original realized backtest. SMA lags
 * count observations and position sizing remains fixed units.
 * </p>
 */
@Demo
public class BootstrapSimulationSMA2Demo {

    public static void main(String[] args) throws Exception {
        new BootstrapSimulationSMA2Demo().run();
    }

    private void run() throws Exception {
        int nSamples = 1000;

        Stock product = HKEXStock.newInstance("^HSI"); // ^HSI
        int fastLeg = 1;
        int slowLeg = 2;

        // set the denomination currency
        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.HKD);

        // trading horizon
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2003, 1, 1, 0, 0),
                LocalDateTime.of(2014, 1, 1, 0, 0)
            );
        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );
        SequentialCache<OrderBook> cache = yahoo.newInstance(product, interval);
        BootstrapSingleCacheFactory cacheFactory
            = new BootstrapSingleCacheFactory(cache);

        // performance measures
        PerformanceMeasure profitLoss = new ProfitLoss();

        BootstrapSimulation.Template bootstrapSimTemplate
            = new BootstrapSimulation.Template() {
            private final List<KeyedMeasure> measures
                = Arrays.asList(measure(profitLoss));

            @Override
            public Simulator newSimulator(OrderBookCaches depthCaches) {
                Simulator simulator = new SimpleSimulatorBuilder()
                    // Supply product-keyed, chronological price events to the simulator.
                    .withOrderBookUpdates(depthCaches)
                    .useExchangeRates(rates)
                    // Explicit measures replace the default measure selection; keep these instances per run.
                    .withMeasures(measures)
                    .build();

                return simulator;
            }

            @Override
            public Strategy newStrategy(OrderBookCaches depthCaches) {
                SMA2 strategy = new SMA2(product, fastLeg, slowLeg);
                return strategy;
            }
        };

        BootstrapSimulation bootstrapSimulation
            = new BootstrapSimulation(
                cacheFactory,
                bootstrapSimTemplate,
                interval
            );
        PerformanceReport[] reports = bootstrapSimulation.run(nSamples);
        double[] pnl
            = BootstrapSimulation.getPerformances(
                reports,
                measure(profitLoss)
            );

        System.out.printf("P/L: %s%n", Arrays.toString(pnl));

        showDistribution(pnl);
    }

    private void showDistribution(double[] pnl) {
        ChartUtils.plotHistogram("P/L distribution", "P/L", "freq.", 20, pnl)
            .show();
    }

}
