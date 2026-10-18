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
package tech.nmfin.algoquant.demo.model.tutorial;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.cache.processor.sampler.EquiTimeSampler;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates how to set up the simulator to simulate the tutorial strategy,
 * {@linkplain TutorialStrategy}.
 *
 * <p><b>Using this example.</b>
 * End-to-end wiring for TutorialStrategy: Yahoo history is sampled monthly, wrapped in
 * OrderBookCaches and passed to SimpleSimulatorBuilder. Initial capital is set to the first
 * sampled price, while the strategy still targets one unit. Registered yearly measures use
 * annual return periods even though the strategy receives monthly observations.
 * </p>
 */
@Demo
public class TutorialStrategyDemo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) throws Exception {
        TutorialStrategyDemo demo = new TutorialStrategyDemo();
        demo.run();
    }

    /**
     * Downloads and samples HSI history, runs one fresh strategy and logs its measures.
     * Requires Yahoo access and a graphical environment for the configured plotter.
     *
     * @throws Exception if market-data loading or simulation fails
     */
    public void run() throws Exception {
        // set up the product
        Stock stock = HSI.getInstance();

        // specify the simulation period
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                Period.ofYears(12)
            );

        // set up the data source; we download data from Yahoo! Finance here.
        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );
        SequentialCache<OrderBook> data
            = yahoo.newInstance(stock, interval)
                .process(new EquiTimeSampler<>(Period.ofMonths(1))); // clean the data using filters; we simulate using only monthly data.

        // set up the data source to feed into the simulator
        OrderBookCaches depthCaches = new OrderBookCaches(stock, data);

        // construct an instance of the strategy to simulate
        Strategy strategy = new TutorialStrategy(stock);

        // set up a simulator to host the strategy
        // The example assumes a nonempty sampled cache. Validate this before first() /
        // iterator().next() in your application, especially for unavailable Yahoo symbols.
        double initialCapital = data.iterator().next().data().mid();
        Simulator simulator = new SimpleSimulatorBuilder()
            // Initial cash funds the account; order quantities are determined by the strategy.
            .withInitialCash(initialCapital)
            // Supply product-keyed, chronological price events to the simulator.
            .withOrderBookUpdates(depthCaches)
            .useStrategyPlotter(
                new SimpleStrategyPlotter("tutorial on " + stock.symbol())
            )
            // Explicit measures replace the default measure selection; keep these instances per run.
            .withMeasures(measures())
            .build();
        // here is where the actual simulation happens
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, interval);

        // print out the report
        LOGGER.info("report = {}", report);
    }

    private List<KeyedMeasure> measures() {
        double benchmarkPeriodReturn = 0.0;
        double lossThreshold = 0.0;
        return Arrays.asList(KeyedMeasure.measures(
            new ProfitLoss(), // pnl
            // The period is a calendar return bucket; the constant benchmark is a return per bucket.
            new InformationRatioForPeriods( // information/Sharpe ratio
                Period.ofYears(1),
                ReturnsCalculators.SIMPLE,
                benchmarkPeriodReturn
            ),
            // The threshold is expressed as a return per selected period, not an annual cash rate.
            new OmegaForPeriods( // Omega
                Period.ofYears(1),
                ReturnsCalculators.SIMPLE,
                lossThreshold
            )
        ));
    }
}
