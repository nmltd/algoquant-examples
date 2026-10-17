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
 * prices and the CMF, we long while there is a positive CMF and a downtrend in
 * prices and short in the opposite case.
 *
 * @see
 * <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow">
 * Chaikin Money Flow</a>
 */
@Demo
public class ChaikinMoneyFlowDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaikinMoneyFlowDemo.class);

    public static void main(String[] args) throws Exception {
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

        int lag = 20;
        Strategy strategy = new SimpleCMFStrategy(stock, lag);

        SequentialCache<StockEOD> cacheEOD
            = DemoSupport.eod(stock, interval);

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            .withOrderBookUpdates(orderBookCaches)
            .withNonOrderBookUpdates(cacheEOD)
            .useStrategyPlotter(
                DemoSupport.plotter(
                    String.format("Simple CMF Strategy on %s", stock)
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
            if (previousPrice != null) {
                LocalDateTime now = ctx.now();
                marginalPriceMA.update(now, price - previousPrice);
                cmf.update(now, eod);
                ctx.broker().sendOrder(computeOrders(ctx.tradeBlotter()));
            }
            previousPrice = price;
        }

        private Collection<Order> computeOrders(TradeBlotter blotter) {
            double qty;
            if (marginalPriceMA.value() > 0.0 && cmf.value() < 0.0) {
                qty = scale - blotter.position(product);
            } else if (marginalPriceMA.value() < 0.0 && cmf.value() > 0.0) {
                qty = -scale - blotter.position(product);
            } else {
                qty = -blotter.position(product);
            }

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
