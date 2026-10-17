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
package tech.nmfin.algoquant.demo.model.technical;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * Runs a fixed two-simple-moving-average crossover strategy using the simulator builder.
 * The fast and slow lags count price observations (daily bars in this example).
 * Prices are synthetic by default; pass {@code --live} for Yahoo history and
 * {@code --charts} for a desktop chart. The report is written to the console.
 */
@Demo
public class SMA2Demo010 {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMA2Demo010.class);

    public static void main(String[] args) throws Exception {
        // Load the license before creating library objects; default to offline, headless inputs.
        DemoSupport.initialize(args);
        Stock product = new SimpleStock("^HSCE", Currencies.HKD, Exchange.HKEX);
        SMA2Demo010 demo = new SMA2Demo010();
        demo.run(product);
    }

    public void run(Stock product) throws Exception {
        // Choose the backtest window. The stock symbol labels synthetic data unless --live is used.
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(
            LocalDateTime.of(2010, 1, 1, 0, 0),
            LocalDateTime.of(2016, 1, 22, 0, 0)
        );
        tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory<Stock> yahoo = DemoSupport.orderBooks();
        SequentialCache<OrderBook> cache = yahoo.newInstance(product, interval);

        // A shorter fast lag responds sooner; the slow lag supplies the crossover reference.
        final int fastLag = 1;
        final int slowLag = 2;
        SMA2 strategy = new SMA2(product, fastLag, slowLag);

        LOGGER.info("simulating strategy {}...", strategy);

        // Feed daily prices to the event-driven strategy through order-book updates.
        OrderBookCaches orderBookCaches = new OrderBookCaches(product, cache);

        // run simulation
        Simulator simulator
            = new SimpleSimulatorBuilder()
                .withOrderBookUpdates(orderBookCaches)
                .useStrategyPlotter(
                    DemoSupport.plotter(
                        String.format(
                            "2 Simple Moving Averages Crossover on %s",
                            product
                        )
                    )
                )
                .withMeasures(KeyedMeasure.measures(
                    new ProfitLoss(),
                    new InformationRatioForTrades(),
                    new OmegaForTrades()
                ))
                .build();
        PerformanceReport report = simulator.run(strategy, interval);
        // Check that simulation produced finite NAV values, then print its performance report.
        DemoSupport.verify(report);
        LOGGER.info("{}", report);
    }
}
