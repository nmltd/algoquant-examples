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
package tech.nmfin.algoquant.demo.ib;

import dev.nm.nmutils.NMUtils;
import dev.nm.number.DoubleUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.order.BasicOrderDescription;
import tech.nmfin.algoquant.execution.datatype.order.LimitOrder;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.product.ListedProduct;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.derivative.ContractType;
import tech.nmfin.algoquant.execution.datatype.product.derivative.option.Option;
import tech.nmfin.algoquant.execution.datatype.product.derivative.option.VanillaOption;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.resumable.ResumableStrategy;

/**
 *
 * @author Ken Yiu
 */
public class IBDemoStrategy implements ResumableStrategy {

    private final Logger LOGGER = NMUtils.getClassLogger();

    private static final double TINY = 1e-6;
    private final ListedProduct underlying;
    private final ContractType type;
    private final DoubleFunction<Double> optionStrikeFunc;
    private final int size;

    private Option selected = null;
    private double bid;
    private double ask;
    private State state = null;

    public IBDemoStrategy(NamedParam param) {
        this.underlying = param.value("underlying");
        this.type = param.value("option-type");
        this.optionStrikeFunc = param.value("option-strike-function");
        this.size = param.value("size");
        LOGGER.info(
            "underlying: {}, option type: {}, size: {}",
            underlying.symbol(), type, size
        );
    }

    @Override
    public void initialize(TraderContext ctx) {
        LOGGER.info("initializing strategy...");
        LOGGER.info("subscribe prices for '{}'", underlying.symbol());
        ctx.subscribePrice(underlying);
        LOGGER.info("strategy initialized");
        changeState(State.INIT);
    }

    @Override
    public void onEvent(TraderContext ctx, Event evt) {
        if (selected != null) {
            ctx.plot().add(
                "position",
                ctx.tradeBlotter().position(selected)
            );
        }
        if (evt instanceof OrderBook) {
            onOrderBook(ctx, (OrderBook) evt);
            return;
        }

        if (evt instanceof Execution) {
            LOGGER.info("current profit: {}", ctx.profit());
            onExecution(ctx, (Execution) evt);
            return;
        }
    }

    private void onOrderBook(TraderContext ctx, OrderBook orderBook) {
        LOGGER.info(
            "orderbook update for '{}': {}|{}",
            orderBook.product().symbol(),
            orderBook.bid(1), orderBook.ask(1)
        );

        if (state == State.INIT
            && orderBook.product().equals(underlying)) {
            selectOption(ctx, orderBook.mid());
            changeState(State.ZERO);
            return;
        }

        if (selected != null && orderBook.product().equals(selected)) {
            handleOptionPrice(ctx, orderBook);
        }
    }

    private void selectOption(TraderContext ctx, double px) {
        LOGGER.info(
            "selecting option of '{}' (mid px = {})...",
            underlying.symbol(), px
        );
        List<Option> optionChain = ctx.data().optionChain(underlying);
        double targetStrike = optionStrikeFunc.apply(px);
        // select an option
        selected = optionChain.stream()
            .filter(o -> o.type() == type)
            .filter(o -> o.expiry().isAfter(ctx.now().toLocalDate().plusWeeks(1)))
            .min((o1, o2)
                -> {
                int pxCmp = Double.compare(
                    Math.abs(o1.strike() - targetStrike),
                    Math.abs(o2.strike() - targetStrike)
                );

                if (pxCmp != 0) {
                    return pxCmp; // strike closest to the target price
                }

                // same strike; select earliest expiry
                return o1.expiry().compareTo(o2.expiry());
            })
            .get();
        LOGGER.info("selected option: {}", selected);

        ctx.subscribePrice(selected);
    }

    private void handleOptionPrice(TraderContext ctx, OrderBook orderBook) {
        switch (state) {
            case ZERO:
                bid = orderBook.bid(1);
                ask = orderBook.ask(1);
                ctx.broker().sendOrder(
                    new LimitOrder(
                        selected,
                        BasicOrderDescription.Side.SELL,
                        size,
                        ask
                    )
                );
                changeState(State.SELLING);
                return;
            case SELLING:
                // no op
                return;
            case BUYING:
                // no op
                return;
        }
    }

    private void onExecution(TraderContext ctx, Execution execution) {
        LOGGER.info("execution: {}", execution);
        double pos = ctx.tradeBlotter().position(selected);
        onPositionChange(ctx, pos);
    }

    private void onPositionChange(TraderContext ctx, double pos) {
        switch (state) {
            case ZERO:
                // no op
                return;
            case SELLING: {
                LOGGER.info("position = {} (should be -ve)", pos);
                if (DoubleUtils.equal(-size, pos, TINY)) {
                    ctx.broker().sendOrder(
                        new LimitOrder(
                            selected,
                            BasicOrderDescription.Side.BUY,
                            size,
                            bid
                        )
                    );
                    changeState(State.BUYING);
                }
                return;
            }
            case BUYING: {
                LOGGER.info("position = {} (should be zero)", pos);
                if (DoubleUtils.equal(0., pos, TINY)) {
                    changeState(State.ZERO);
                }
                return;
            }
        }
    }

    private void changeState(State newState) {
        LOGGER.info("change state from '{}' to '{}'", state, newState);
        state = newState;
    }

    @Override
    public List<String> saveTradedProducts() {
        if (selected == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(
            String.format(
                "%s,%f,%s,%f",
                selected.symbol(),
                selected.strike(),
                selected.expiry().format(DateTimeFormatter.BASIC_ISO_DATE),
                selected.multiplier()
            )
        );
    }

    @Override
    public List<Product> resumeTradedProducts(List<String> savedProductInfo) {
        if (savedProductInfo.isEmpty()) {
            return Collections.emptyList();
        }

        String optionString = savedProductInfo.get(0);
        String[] optionInfo = optionString.split(",");
        String symbol = optionInfo[0];
        double strike = Double.parseDouble(optionInfo[1]);
        LocalDate expiry
            = LocalDate.parse(
                optionInfo[2],
                DateTimeFormatter.BASIC_ISO_DATE
            );
        double multiplier = Double.parseDouble(optionInfo[3]);
        selected = new VanillaOption(
            symbol,
            underlying,
            type,
            strike,
            expiry,
            multiplier
        );

        return Collections.singletonList(
            selected
        );
    }

    @Override
    public Map<String, String> saveStates() {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("bid", String.valueOf(bid));
        states.put("ask", String.valueOf(ask));
        states.put("state", state.name());
        return states;
    }

    @Override
    public void resumeStates(TraderContext ctx, Map<String, String> savedStates) {
        bid = Double.parseDouble(savedStates.getOrDefault("bid", "0"));
        ask = Double.parseDouble(savedStates.getOrDefault("ask", "0"));
        state = savedStates.containsKey("state")
            ? State.valueOf(savedStates.get("state"))
            : State.INIT;
        if (state != State.INIT) { // check if the position changes when offline
            onPositionChange(ctx, ctx.tradeBlotter().position(selected));
        }
    }

    private static enum State {
        INIT,
        ZERO,
        SELLING,
        BUYING;
    }
}
