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
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanReversionPortfolio;
import tech.nmfin.algoquant.model.meanreversion.volarb.MRModelFixedGrid;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanEstimatorConstant;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.gaincapital.GainCapitalFXCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.basket.Basket;
import tech.nmfin.algoquant.execution.datatype.product.basket.BasketUtils;
import tech.nmfin.algoquant.execution.datatype.product.fx.*;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanReversionPortfolio.SubStrategyParam;
import tech.nmfin.algoquant.util.AQUtils;
import static tech.nmfin.algoquant.util.AQUtils.formatExecutionsInTabularForm;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.meanreversion.volarb.MRModel;

/**
 * Demonstrates how to create a {@link MeanReversionPortfolio} with different
 * grid sizes.
 *
 * <p><b>Using this example.</b>
 * Combines mean-reversion components with different grid sizes on local GainCapital data.
 * Follow each basket and scale into the combined portfolio; adding components changes total
 * exposure and does not automatically normalize the allocation.
 * </p>
 */
@Demo
public class MeanReversionPortfolioDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final double SCALE = 1.0;
    private static final double LOT_SIZE = 1.0;
    private static final Currency CCY1 = Currencies.NZD;
    private static final Currency CCY2 = Currencies.CHF;
    private static final double RATE = 1.1;
    private static final FXProduct PRODUCT = new SimpleFXProduct(Currencies.NZD, Currencies.CHF, 0.0001);
    private static final LocalDateTime BEGIN = LocalDateTime.of(2010, 8, 2, 0, 0);
    private static final LocalDateTime END = BEGIN.plusDays(1);
    private static final LocalDateTimeInterval INTERVAL = LocalDateTimeInterval.of(BEGIN, END);

    public static void main(String[] args) {
        String dataFolder = "./test-data/gaincapital";
        MeanReversionPortfolioDemo demo = new MeanReversionPortfolioDemo();
        demo.run(dataFolder);
    }

    public void run(String dataFolder) {
        ExchangeRateTable rates = new SimpleExchangeRateTable(CCY1);
        rates.setExchangeRate(CCY2, RATE);

        // create the strategy
        Basket<Product> basket = BasketUtils.newSingleAssetBasket(PRODUCT);
        List<SubStrategyParam> subStrategyParams
            = Arrays.asList(new SubStrategyParam[]{
            newSubStrategyParam(AQUtils.tickSize(PRODUCT, 50), SCALE * 2.),
            newSubStrategyParam(AQUtils.tickSize(PRODUCT, 25), SCALE)
        });
        Strategy strategy
            = new MeanReversionPortfolio(
                basket,
                LOT_SIZE,
                subStrategyParams,
                true // parallelized
            );

        // create source of market order book
        SequentialCache<? extends OrderBook> orderBookCache
            = new GainCapitalFXCacheFactory(dataFolder)
                .newInstance(PRODUCT, INTERVAL);
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(PRODUCT, orderBookCache);

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            // Supply product-keyed, chronological price events to the simulator.
            .withOrderBookUpdates(orderBookCaches)
            .useExchangeRates(rates)
            .useStrategyPlotter(
                new SimpleStrategyPlotter("Fixed-Grid Mean Reversion on " + PRODUCT)
            ) // new NoOpPlotter()); // turn off plotting for faster performance
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
        PerformanceReport report = simulator.run(strategy, INTERVAL);
        List<Execution> executions = report.tradeBlotter().executions(PRODUCT);

        LOGGER.info("executions: {}", formatExecutionsInTabularForm(executions));

        LOGGER.info(report.toString());
    }

    private SubStrategyParam newSubStrategyParam(double gridSize, double scale) {
        MRModel model = new MRModelFixedGrid(gridSize, new MeanEstimatorConstant());
        String id = String.format("grid size: %f", gridSize);
        return new SubStrategyParam(id, model, scale);
    }
}
