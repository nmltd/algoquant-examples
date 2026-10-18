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
import dev.nm.number.DoubleUtils;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODCacheFactory;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.component.tradeblotter.TradeBlotter;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.order.MarketOrder;
import tech.nmfin.algoquant.execution.datatype.order.Order;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.signal.technical.movingaverage.AMA;
import tech.nmfin.algoquant.model.signal.technical.volume.ChaikinMoneyFlow;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * A simple strategy based on the Chaikin Money Flow. Using moving averages on
 * price changes and the CMF, the implemented rule targets +1 unit when price
 * changes are positive and CMF is negative, and -1 unit for the reverse signs.
 * It targets zero otherwise.
 *
 * Prices are synthetic by default; use {@code --live} for Yahoo history and
 * {@code --charts} for a desktop indicator chart. The report goes to the console.
 *
 *
 * <p><b>Using this example.</b>
 * Uses an OHLCV indicator to choose a target position and submits the difference from the
 * filled position. Quantity is in signed product units, not dollars. DemoSupport controls the
 * default synthetic inputs and optional live data and charts.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * No single original research publication is claimed for this library/example policy.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Chaikin money flow compares accumulated money-flow volume with total volume over a rolling
 * window. The demo combines indicator and price-change signs to choose unit targets. See the
 * implemented rule above rather than assuming a textbook divergence interpretation.
 * </p>
 * @see
 * <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow">
 * Chaikin Money Flow</a>
 */
@Demo
public class ChaikinMoneyFlowDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaikinMoneyFlowDemo.class);

    public static void main(String[] args) throws Exception {
        // Load the license before creating library objects; default to offline, headless inputs.
        DemoSupport.initialize(args);
        // setup data source
        Stock stock = USDStock.newInstance("FCX", Exchange.NEW_YORK);
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2010, 2, 1, 0, 0),
                LocalDateTime.of(2010, 12, 22, 0, 0)
            );
        tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory<Stock> yahoo = DemoSupport.orderBooks();

        // create source of market order book
        SequentialCache<OrderBook> cache = yahoo.newInstance(stock, interval);
        OrderBookCaches orderBookCaches = new OrderBookCaches(stock, cache);

        // Use twenty observations for both price-change smoothing and the CMF window.
        int lag = 20;
        Strategy strategy = new SimpleCMFStrategy(stock, lag);

        SequentialCache<StockEOD> cacheEOD
            = DemoSupport.eod(stock, interval);

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            // Supply product-keyed, chronological price events to the simulator.
            .withOrderBookUpdates(orderBookCaches)
            // OHLCV events supply the high, low, close and volume required by the indicator.
            .withNonOrderBookUpdates(cacheEOD)
            .useStrategyPlotter(
                DemoSupport.plotter(
                    String.format("Simple CMF Strategy on %s", stock)
                )
            )
            // Explicit measures replace the default measure selection; keep these instances per run.
            .withMeasures(KeyedMeasure.measures(
                new ProfitLoss(),
                // This statistic samples completed trades, unlike calendar-period return statistics.
                new InformationRatioForTrades(),
                new OmegaForTrades()
            ))
            .build();
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, interval);
        // Check that simulation produced finite NAV values, then print its performance report.
        DemoSupport.verify(report);
        LOGGER.info(report.toString());
    }

    private ChaikinMoneyFlowDemo() {
    }

    public static class SimpleCMFStrategy implements Strategy {

        private final AMA marginalPriceMA;
        private final Product product;
        private final ChaikinMoneyFlow cmf;
        private Double previousPrice = null;
        private final double scale = 1;

        public SimpleCMFStrategy(Product product, int lag) {
            this.marginalPriceMA = new AMA(lag);
            this.cmf = new ChaikinMoneyFlow(lag);
            this.product = product;
        }

        @Override
        public void onEvent(TraderContext ctx, Event evt) {
            if (evt instanceof StockEOD) {
                onEOD(ctx, (StockEOD) evt);
                return;
            }
            plotSignals(ctx);
        }

        private void onEOD(TraderContext ctx, StockEOD eod) {
            double price = eod.close();
            // The first bar only establishes a reference price for the next daily change.
            if (previousPrice != null) {
                LocalDateTime now = ctx.now();
                marginalPriceMA.update(now, price - previousPrice);
                cmf.update(now, eod);
                ctx.broker().sendOrder(computeOrders(ctx.tradeBlotter()));
            }
            previousPrice = price;
        }

        private Collection<Order> computeOrders(TradeBlotter blotter) {
            // Orders change the current position to the target; they are not fixed-size additions.
            double qty;
            if (marginalPriceMA.value() > 0.0 && cmf.value() < 0.0) {
                qty = scale - blotter.position(product);
            } else if (marginalPriceMA.value() < 0.0 && cmf.value() > 0.0) {
                qty = -scale - blotter.position(product);
            } else {
                qty = -blotter.position(product);
            }

            // Suppress negligible orders when the target position has already been reached.
            return (DoubleUtils.compare(qty, 0., 1e-5) == 0)
                ? Collections.<Order>emptyList()
                : Collections.<Order>singletonList(new MarketOrder(product, qty));
        }

        private void plotSignals(TraderContext ctx) {
            if (previousPrice == null || Double.isNaN(cmf.value())) {
                return;
            }
            ctx.plot().add("Cumulative Money Flow", cmf.value());
        }
    }
}
