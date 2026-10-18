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
package tech.nmfin.algoquant.demo.model.januaryeffect;

import static dev.nm.misc.ArgumentAssertion.assertTrue;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.MonthDay;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.broker.Broker;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.component.simulator.event.timer.TimerEvent;
import tech.nmfin.algoquant.execution.component.tradeblotter.TradeBlotter;
import tech.nmfin.algoquant.execution.datatype.order.Order;
import static tech.nmfin.algoquant.execution.datatype.order.OrderUtils.newMarketOrder;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.strategy.Strategy;

/**
 * A simple strategy to demonstrate the use of timer updates. This strategy buys
 * at the first timing update after a given date and squares the position at the
 * first timing update after another given date.
 *
 * We use this to exploit the January effect, which is the well-known
 * observation that stock prices tend to increase in early January.
 *
 * <p><b>Using this example.</b>
 * Demonstrates timer-driven trading across a year boundary. initialize subscribes to the
 * product and schedules a timer; each timer schedules the next day, buys one unit after
 * buyTime and closes that unit after sellTime. A calendar timer is not itself a tradable
 * market observation, and sendOrder does not guarantee a fill.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * No single original research publication is claimed for this library/example policy.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * This is a calendar-rule illustration: schedule daily callbacks, open one unit near year-end
 * and flatten after the configured new-year date. The code does not estimate a seasonal premium
 * or implement a causal explanation of the January effect. It is not presented as a replication
 * of a specific empirical publication.
 * </p>
 */
public class JanuaryEffectStrategy implements Strategy {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    private final double scale = 1;
    private final Product product;
    private final MonthDay buyTime;
    private final MonthDay sellTime;

    /**
     * Constructs a new instance of the strategy.
     *
     * @param product  the product to trade
     * @param buyTime  the date after which we buy at the end of the year
     * @param sellTime the date after which we sell at the beginning of the year
     */
    public JanuaryEffectStrategy(
        Product product,
        MonthDay buyTime,
        MonthDay sellTime
    ) {
        assertTrue(
            buyTime.isAfter(sellTime),
            "buyTime must be later in the year than sellTime!"
        );
        this.product = product;
        this.buyTime = buyTime;
        this.sellTime = sellTime;
    }

    @Override
    public void initialize(TraderContext ctx) {
        LOGGER.info("initialize at {}", ctx.now());
        // Register a timer explicitly: price updates alone do not create TimerEvents.
        ctx.scheduler().schedule("my-timer", ctx.now());

        ctx.subscribePrice(product);
    }

    @Override
    public void onEvent(TraderContext ctx, Event evt) {
        if (evt instanceof TimerEvent) {
            LocalDateTime now = ctx.now();
            MonthDay nowMonthDay
                = MonthDay.of(now.getMonthValue(), now.getDayOfMonth());
            if (!nowMonthDay.isBefore(buyTime)
                && ctx.tradeBlotter().position(product) == 0.0) {
                buy(ctx.broker());
            } else if (!nowMonthDay.isBefore(sellTime)
                && nowMonthDay.isBefore(buyTime)
                && ctx.tradeBlotter().position(product) > 0.0) {
                sell(ctx.tradeBlotter(), ctx.broker());
            }

            // Timers are one-shot; reschedule to continue receiving daily callbacks.
            ctx.scheduler().schedule("my-timer", ctx.now().plusDays(1));
        }
    }

    private void buy(Broker broker) {
        Order order = newMarketOrder(product, scale);
        broker.sendOrder(order);
    }

    private void sell(TradeBlotter blotter, Broker broker) {
        if (blotter.position(product) != scale) {
            throw new RuntimeException(
                String.format(
                    "sell attempted even though position is not %f!",
                    scale
                )
            );
        } else {
            Order order = newMarketOrder(product, -scale);
            broker.sendOrder(order);
        }
    }
}
