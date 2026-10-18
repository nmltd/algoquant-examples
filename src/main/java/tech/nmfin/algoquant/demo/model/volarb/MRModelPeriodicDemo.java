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
import tech.nmfin.algoquant.model.meanreversion.volarb.MRModelPeriodic;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanEstimatorConstant;
import dev.nm.nmutils.NMUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
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
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 *
 */
@Demo
public class MRModelPeriodicDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        String dataFolder = "./test-data/gaincapital";
        MRModelPeriodicDemo demo = new MRModelPeriodicDemo();
        demo.run(dataFolder);
    }

    public void run(String dataFolder) {
        // setup data source
        FXProduct fxProduct
            = new SimpleFXProduct(Currencies.NZD, Currencies.CHF, 0.0001);

        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        rates.setExchangeRate(Currencies.CHF, 1.1);

        // create the strategy
        Basket<Product> basket = BasketUtils.newSingleAssetBasket(fxProduct);
        Strategy strategy
            = new MeanReversionStrategy(
                basket,
                new MRModelPeriodic(
                    Duration.ofMinutes(45),
                    new MeanEstimatorConstant()
                ),
                1e6, // scale
                1 // lot numberOfProducts
            );

        // simulation period
        LocalDateTime begin = LocalDateTime.of(2010, 8, 2, 0, 0);
        LocalDateTime end = begin.plus(Period.ofDays(1));
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        // create source of market order book
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(
                fxProduct,
                new GainCapitalFXCacheFactory(dataFolder)
                    .newInstance(fxProduct, interval)
            );

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            .withOrderBookUpdates(orderBookCaches)
            .useStrategyPlotter(
                new SimpleStrategyPlotter("Periodic Mean Reversion on " + fxProduct)
            ) // new NoOpPlotter()); // turn off plotting for faster performance
            .withMeasures(KeyedMeasure.measures(
                new ProfitLoss(),
                new InformationRatioForTrades(),
                new OmegaForTrades()
            ))
            .build();
        PerformanceReport report = simulator.run(strategy, interval);
        LOGGER.info(report.toString());
    }
}
