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
package tech.nmfin.algoquant.demo.model.volarb;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.gaincapital.GainCapitalFXCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.basket.Basket;
import tech.nmfin.algoquant.execution.datatype.product.basket.BasketUtils;
import tech.nmfin.algoquant.execution.datatype.product.fx.*;
import tech.nmfin.algoquant.execution.performance.measure.ExecutionCount;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanReversionStrategy;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.meanreversion.volarb.MRModelRanged;

/**
 *
 *
 * <p><b>Using this example.</b>
 * Builds an AUD/USD and NZD/USD pair basket with coefficient -1.5 and MRModelRanged threshold
 * 0.0025. It uses local GainCapital tick files, a desktop plotter and trade-based measures.
 * The scale and lot inputs are quantities, not percentages of initial capital.
 * </p>
 */
@Demo
public class MRModelRanged2Demo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) {
        // simulation period
        LocalDateTime begin
            = LocalDateTime.of(2011, 3, 4, 0, 0);
        LocalDateTime end = begin.plus(Period.ofDays(1));
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        // setup data source
        OrderBookCacheFactory<FXProduct> gc
            = new GainCapitalFXCacheFactory("./test-data/gaincapital");
        FXProduct fxProduct1
            = new SimpleFXProduct(Currencies.AUD, Currencies.USD, 0.0001);
        FXProduct fxProduct2
            = new SimpleFXProduct(Currencies.NZD, Currencies.USD, 0.0001);

        // construct price source mapping
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(
                gc,
                Arrays.asList(fxProduct1, fxProduct2),
                interval
            );

        // create a basket to trade
        Basket<Product> basket
            = BasketUtils.newPairBasket(fxProduct1, fxProduct2, -1.5);

        // create the strategy
        Strategy strategy
            = new MeanReversionStrategy(
                basket,
                new MRModelRanged(0.0025),
                1, // scale
                1 // lot numberOfProducts
            );

        // run simulation
        Simulator simulator
            = new SimpleSimulatorBuilder()
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(orderBookCaches)
                .useStrategyPlotter(
                    new SimpleStrategyPlotter("Ranged Mean Reversion on " + basket)
                )
                // Explicit measures replace the default measure selection; keep these instances per run.
                .withMeasures(KeyedMeasure.measures(
                    new ExecutionCount(),
                    new ProfitLoss(),
                    // This statistic samples completed trades, unlike calendar-period return statistics.
                    new InformationRatioForTrades(),
                    new OmegaForTrades()
                ))
                .build();
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, interval);
        LOGGER.info(report.toString());
    }
}
