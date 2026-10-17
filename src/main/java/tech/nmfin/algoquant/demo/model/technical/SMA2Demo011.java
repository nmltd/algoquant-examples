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
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.annotation.Demo;

@Demo
public class SMA2Demo011 {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMA2Demo011.class);

    public static void main(String[] args) throws Exception {
        DemoSupport.initialize(args);
        Stock product = USDStock.newInstance("META", Exchange.NASDAQ);
        SMA2Demo011 demo = new SMA2Demo011();
        demo.run(product);
    }

    public void run(Stock product) throws Exception {
        // setup data source
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2012, 1, 1, 0, 0),
                LocalDateTime.of(2015, 3, 31, 0, 0)
            );
        tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory<Stock> yahoo = DemoSupport.orderBooks();
        SequentialCache<OrderBook> cache = yahoo.newInstance(product, interval);

        // create the strategy
        final int fastLag = 1;
        final int slowLag = 5;
        SMA2 strategy = new SMA2(product, fastLag, slowLag);

        LOGGER.info(strategy.toString());

        // create source of market order book
        OrderBookCaches orderBookCaches = new OrderBookCaches(product, cache);
        final ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        rates.setExchangeRate(Currencies.HKD, 1 / 7.8);

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            .withOrderBookUpdates(orderBookCaches)
            .useExchangeRates(rates)
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
        DemoSupport.verify(report);
        LOGGER.info(report.toString());
    }
}
