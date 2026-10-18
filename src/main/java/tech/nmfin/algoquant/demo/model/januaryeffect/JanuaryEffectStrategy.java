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
