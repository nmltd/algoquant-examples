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
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanReversionStrategy;
import tech.nmfin.algoquant.model.meanreversion.volarb.MRModelFixedGrid;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanEstimatorConstant;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collections;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.gaincapital.GainCapitalFXCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.basket.Basket;
import tech.nmfin.algoquant.execution.datatype.product.basket.BasketUtils;
import tech.nmfin.algoquant.execution.datatype.product.fx.*;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import static tech.nmfin.algoquant.util.AQUtils.tickSize;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 *
 *
 * <p><b>Using this example.</b>
 * Builds a mean-reversion grid on local GainCapital FX history. The grid threshold is a
 * price/spread distance, not a number of days. Basket coefficients, strategy scale and lot
 * settings determine product exposure; inspect those before interpreting P/L.
 * </p>
 */
@Demo
public class MRModelFixedGridDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        String dataFolder = "./test-data/gaincapital";
        MRModelFixedGridDemo demo = new MRModelFixedGridDemo();
        demo.run(dataFolder);
    }

    public void run(String dataFolder) {
        // setup data source
        FXProduct fxProduct
            = new SimpleFXProduct(Currencies.NZD, Currencies.CHF, 0.0001);

        // simulation period
        LocalDateTime begin = LocalDateTime.of(2010, 8, 2, 0, 0);
        LocalDateTime end = begin.plus(Period.ofDays(1));
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.NZD);
        rates.setExchangeRate(Currencies.CHF, 1.1);

        // create the strategy
        Basket<Product> basket = BasketUtils.newSingleAssetBasket(fxProduct);
        Strategy strategy
            = new MeanReversionStrategy(
                basket,
                new MRModelFixedGrid(
                    tickSize(fxProduct, 50),
                    new MeanEstimatorConstant()
                ),
                1, // scale
                1 // lot numberOfProducts
            );

        // create source of market order book
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(
                new GainCapitalFXCacheFactory(dataFolder),
                Collections.singletonList(fxProduct),
                interval
            );

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            // Supply product-keyed, chronological price events to the simulator.
            .withOrderBookUpdates(orderBookCaches)
            .useExchangeRates(rates)
            .useStrategyPlotter(
                new SimpleStrategyPlotter("Fixed-Grid Mean Reversion on " + fxProduct)
            ) // new NoOpPlotter() // turn off plotting for faster performance
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
        LOGGER.info("report = {}", report);
    }
}
