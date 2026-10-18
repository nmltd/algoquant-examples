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
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.Period;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.broker.Broker;
import tech.nmfin.algoquant.execution.component.broker.otc.*;
import tech.nmfin.algoquant.execution.component.cash.CashTracker;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.component.simulator.event.timer.TimerEvent;
import tech.nmfin.algoquant.execution.component.simulator.event.timer.TimerEventCache;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import static tech.nmfin.algoquant.execution.datatype.order.BasicOrderDescription.Side.BUY;
import static tech.nmfin.algoquant.execution.datatype.order.BasicOrderDescription.Side.SELL;
import static tech.nmfin.algoquant.execution.datatype.order.OrderUtils.newMarketOrder;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.orderbook.marketcondition.MarketCondition;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HKEXStock;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;

/**
 * Demonstrates how to use OTC broker to trade OTC bonds.
 *
 * <p><b>Using this example.</b>
 * Combines Yahoo stock order books with scheduled events that expose a simulated OTC bond
 * broker and trigger bond purchases. The nested strategy consumes those events through
 * TraderContext; distinguish the simulated broker from an external trading connection.
 * </p>
 */
public class OtcBrokerSimDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) throws Exception {
        OtcBrokerSimDemo demo = new OtcBrokerSimDemo();
        demo.run();
    }

    public void run() throws Exception {
        LocalDateTime start = LocalDateTime.of(2015, 1, 1, 0, 0);
        Period period = Period.ofYears(1);
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(start, period);

        Stock stock = HKEXStock.newInstance("0005.HK"); // HSBC

        OrderBookCaches depthCaches = new OrderBookCaches();
        depthCaches.addCache(
            stock,
            new YahooOrderBookCacheFactory<>().newInstance(stock, interval)
        );

        Simulator simulator
            = new SimpleSimulatorBuilder()
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(depthCaches)
                .withNonOrderBookUpdates( // access OTC broker at start
                    OtcBrokerInit.scheduleOnce(
                        start,
                        new SimpleOtcBondBroker.Factory()
                    )
                )
                .withNonOrderBookUpdates( // buy bond timer
                    TimerEventCache.atOneTime(
                        DemoStrategy.SWITCH_TO_BOND_SIGNAL,
                        start.plusMonths(6)
                    )
                )
                .withNonOrderBookUpdates(
                    TimerEventCache.atOneTime(
                        DemoStrategy.SELL_ALL_BOND,
                        interval.end().minusNanos(1)
                    )
                )
                .build();

        double initialCash = 1_000_000.;
        PerformanceReport report
            = simulator.run(
                new DemoStrategy(stock, initialCash),
                interval
            );

        LOGGER.info("report : {}", report);
    }

    private static class DemoStrategy
        implements Strategy {

        public static final String SWITCH_TO_BOND_SIGNAL = "bond";
        private static String SELL_ALL_BOND = "sell-bond";

        private final CashTracker cashTracker = new CashTracker();
        private final Stock stock;
        private OtcBroker otcBroker;
        private boolean boughtStock = false;
        private boolean soldStock = false;
        private SimpleOtcBond bond;

        DemoStrategy(Stock stock, double initialCapital) {
            this.stock = stock;
            this.cashTracker.deposit(initialCapital);
            LOGGER.info("initial cash: {}", cashTracker.amount());
        }

        @Override
        public void onEvent(TraderContext ctx, Event evt) {
            if (evt instanceof OtcBrokerInit) {
                onOtcBrokerInit(ctx, (OtcBrokerInit) evt);
                return;
            }
            if (evt instanceof OrderBook) {
                onOrderBook(ctx, (OrderBook) evt);
                return;
            }
            if (evt instanceof TimerEvent) {
                onTimer(ctx, (TimerEvent) evt);
                return;
            }
            if (evt instanceof Execution) {
                onExecution(ctx, (Execution) evt);
                return;
            }
        }

        private void onOtcBrokerInit(
            TraderContext ctx,
            OtcBrokerInit otcBrokerInit
        ) {
            this.otcBroker = otcBrokerInit.newOtcBroker(ctx);
        }

        private void onOrderBook(TraderContext ctx, OrderBook orderBook) {
            if (boughtStock) {
                return;
            }
            buyStock(ctx.now(), ctx.broker(), ctx.marketCondition());
        }

        private void onTimer(TraderContext ctx, TimerEvent timer) {
            if (SWITCH_TO_BOND_SIGNAL.equals(timer.id())) {
                LOGGER.info("[{}]: switch to bond...", ctx.now());
                sellStock(
                    ctx.now(),
                    ctx.broker(),
                    ctx.tradeBlotter().position(stock)
                );
                return;
            }

            if (SELL_ALL_BOND.equals(timer.id())) {
                LOGGER.info("[{}]: sell all bond...", ctx.now());
                sellBond(ctx.now(), ctx.tradeBlotter().position(bond));
                return;
            }
        }

        private void onExecution(TraderContext ctx, Execution execution) {
            LOGGER.info("execution: {}", execution);
            cashTracker.updateForExecution(execution);

            if (soldStock && bond == null) {
                buyBond(ctx.now());
            }
        }

        private void buyStock(LocalDateTime now, Broker broker, MarketCondition mc) {
            double cash = cashTracker.amount();
            double px = mc.orderBook(stock).ask(1);
            double qty = (int) Math.floor(cash / px); // all in
            LOGGER.info(
                "[{}]: buy stock ({}) x {} @ {} (cash={})",
                now, stock.symbol(), qty, px, cash
            );
            broker.sendOrder(newMarketOrder(stock, qty));
            boughtStock = true;
        }

        private void sellStock(LocalDateTime now, Broker broker, double position) {
            LOGGER.info(
                "[{}]: sell stock ({}) x {} @ market (cash={})",
                now, stock.symbol(), position, cashTracker.amount()
            );
            broker.sendOrder(newMarketOrder(stock, -position));
            soldStock = true;
        }

        private void buyBond(LocalDateTime now) {
            bond = new SimpleOtcBond("hk-bond", Currencies.HKD, 0.05, now.toLocalDate());
            double cash = cashTracker.amount();
            double px = bond.value(now.toLocalDate());
            double qty = (int) Math.floor(cash / px);
            LOGGER.info(
                "[{}]: buy bond ({}) x {} @ {} (cash={})",
                now, bond.symbol(), qty, px, cash
            );
            Execution execution1 = otcBroker.order(now, bond, BUY, qty);
            cashTracker.updateForExecution(execution1);
        }

        private void sellBond(LocalDateTime now, double qty) {
            LOGGER.info(
                "[{}]: sell bond ({}) x {} @ {} (cash={})",
                now, bond.symbol(), qty, bond.value(now.toLocalDate()), cashTracker.amount()
            );
            Execution execution = otcBroker.order(now, bond, SELL, qty);
            cashTracker.updateForExecution(execution);
        }
    }
}
