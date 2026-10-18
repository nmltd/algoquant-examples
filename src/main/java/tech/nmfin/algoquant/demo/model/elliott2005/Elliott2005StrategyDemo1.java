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
package tech.nmfin.algoquant.demo.model.elliott2005;

import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.Elliott2005Strategy;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.MeanReversionAroundLongTermMeanConstantCoefficients;
import static tech.nmfin.algoquant.util.AQUtils.formatExecutionsInTabularForm;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates how to use {@linkplain Elliott2005Strategy} and customize the
 * strategy.
 */
@Demo
public class Elliott2005StrategyDemo1 {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) {
        Elliott2005StrategyDemo1 demo = new Elliott2005StrategyDemo1();
        demo.run();
    }

    // parameters
    private final LocalDateTimeInterval interval
        = LocalDateTimeInterval.of(
            LocalDateTime.of(2005, 1, 1, 0, 0),
            LocalDateTime.of(2011, 9, 1, 0, 0)
        );
    private final int calibrationWindow = 50;
    private final Stock stock1 = USDStock.newInstance("XOM", Exchange.NEW_YORK);
    private final Stock stock2 = USDStock.newInstance("CVX", Exchange.NEW_YORK);
    private final List<Product> stocks = Arrays.asList(stock1, stock2);

    public void run() {
        // set up the data sources
        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );

        // create a source of market order book
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(
                yahoo,
                Arrays.asList(stock1, stock2),
                interval
            );

        /*
         * uncomment the customerization that you want to simulate
         */
//        Elliott2005Strategy.Customization customization
//            = new MeanReversionAroundPosterioriStateEstimateCointegratedCoefficients(0.1, 2,// beta range
//                                                                                     0.2,// cointegration confidence
//                                                                                     0.02, 0.01); // entry/exit signals
//        Elliott2005Strategy.Customization customization
//            = new MeanReversionAroundPosterioriStateEstimateConstantCoefficients(new DenseVector(1.0, -1.0),// the spread coefficients
//                                                                                 0.03, 0.01);// entry/exit signals
        Elliott2005Strategy.Customization customization
            = new MeanReversionAroundLongTermMeanConstantCoefficients(
                new DenseVector(1.0, -1.0), // the spread coefficients
                0.4,
                0.25 // entry/exit signals
            );

        // set up the strategy with the specifications and customerization
        Elliott2005Strategy strategy
            = new Elliott2005Strategy(stocks, customization, calibrationWindow);
        LOGGER.info(strategy.toString());

        // set up a simulator to host the strategy
        Simulator simulator
            = new SimpleSimulatorBuilder()
                .withOrderBookUpdates(orderBookCaches)
                .useStrategyPlotter(
                    new SimpleStrategyPlotter(
                        "Elliott's 2005 Pairs Trading: "
                        + stocks.get(0).symbol() + " vs. "
                        + stocks.get(1).symbol()
                    )
                )
                .withMeasures(measures(
                    new ProfitLoss(),
                    new InformationRatioForZeroInvestment(
                        Period.ofYears(1),
                        0.
                    ),
                    new OmegaForPeriods(
                        Period.ofYears(1),
                        ReturnsCalculators.ABSOLUTE,
                        0.
                    )
                ))
                .build();

        // run the strategy
        PerformanceReport report = simulator.run(strategy, interval);

        // execution summary
        List<Execution> executions = report.tradeBlotter().allExecutions();
        LOGGER.info(
            "executions: {}",
            formatExecutionsInTabularForm(executions)
        );

        LOGGER.info("performance report:%n{}", report);
    }

}
