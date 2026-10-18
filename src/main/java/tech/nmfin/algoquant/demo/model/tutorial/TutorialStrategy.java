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

import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.singletonList;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.datatype.order.Order;
import static tech.nmfin.algoquant.execution.datatype.order.OrderUtils.newMarketOrder;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.strategy.Strategy;

/**
 * This is a simple example that accompanies the
 * <a href= "http://www.numericalmethod.com/trac/numericalmethod/wiki/AlgoQuant/Tutorial/Strategy">
 * tutorial</a> to show how a strategy is coded up in AlgoQuant. This strategy
 * buys on a up-tick and sells on a down-tick.
 *
 */
public class TutorialStrategy implements Strategy {

    private final double scale = 1;
    private final Product product;
    private Double previousPrice = null;

    public TutorialStrategy(Product product) {
        this.product = product;
    }

    @Override
    public void onEvent(TraderContext ctx, Event evt) {
        if (evt instanceof OrderBook) {
            onOrderBookUpdate(ctx);
        }
    }

    private void onOrderBookUpdate(TraderContext ctx) {
        double currentPrice = ctx.marketCondition().orderBook(product).mid();
        if (previousPrice != null) {
            Collection<Order> orders
                = computeOrders(
                    currentPrice,
                    ctx.tradeBlotter().position(product)
                );
            ctx.broker().sendOrder(orders);
        }
        previousPrice = currentPrice;
    }

    private Collection<Order> computeOrders(
        double currentPrice,
        double position
    ) {
        double qty = 0.;
        if (currentPrice >= previousPrice) {
            qty = scale - position;
        } else if (currentPrice < previousPrice) {
            qty = -scale - position;
        }

        if (qty == 0.) {
            return Collections.emptySet();
        }

        return singletonList(newMarketOrder(product, qty));
    }
}
