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
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.chart.plotter.StrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.SimulatorBuilder;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateUtils;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.Elliott2005Strategy;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.MeanReversionAroundLongTermMeanConstantCoefficients;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates another (simpler) way of coding
 * {@linkplain Elliott2005StrategyDemo1} using a template.
 */
@Demo
public class Elliott2005StrategyDemo2 {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) {
        Elliott2005StrategyDemo2 demo = new Elliott2005StrategyDemo2();
        demo.run();
    }

    public void run() {
        // the simulation parameters
        Stock stock1 = USDStock.newInstance("XOM", Exchange.NEW_YORK);
        Stock stock2 = USDStock.newInstance("CVX", Exchange.NEW_YORK);

        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);

        // the simulation specifications
        SimTemplateYahooEOD spec = new SimTemplateYahooEOD() {

            @Override
            public LocalDateTimeInterval interval() {
                return LocalDateTimeInterval.of(
                    LocalDateTime.of(2004, 11, 1, 0, 0),
                    LocalDateTime.of(2011, 9, 1, 0, 0)
                );
            }

            @Override
            public List<Stock> products() {
                List<Stock> products = Arrays.asList(stock1, stock2);
                return products;
            }

            @Override
            public Strategy newStrategy() {
                /*
                 * uncomment the customerization that you want to simulate
                 */
//                Elliott2005Strategy.Customization customization
//                    = new MeanReversionAroundPosterioriStateEstimateCointegratedCoefficients(0.1, 2,// beta range
//                                                                                             0.2,// cointegration confidence
//                                                                                             0.02, 0.01); // entry/exit signals
//                Elliott2005Strategy.Customization customization
//                    = new MeanReversionAroundPosterioriStateEstimateConstantCoefficients(new DenseVector(1.0, -1.0),// the spread coefficients
//                                                                                         0.03, 0.01);// entry/exit signals
                Elliott2005Strategy.Customization customization
                    = new MeanReversionAroundLongTermMeanConstantCoefficients(
                        new DenseVector(1.0, -1.0), // the spread coefficients
                        0.4,
                        0.25 // entry/exit signals
                    );

                int calibrationWindow = 50;

                List<Product> stocks = new ArrayList<>(2);
                stocks.add(stock1);
                stocks.add(stock2);

                Elliott2005Strategy strategy
                    = new Elliott2005Strategy(
                        stocks,
                        customization,
                        calibrationWindow
                    );

                return strategy;
            }

            @Override
            public List<KeyedMeasure> performanceMeasures() {
                return Arrays.asList(measures(
                    new ProfitLoss(),
                    new InformationRatioForZeroInvestment(
                        Period.ofYears(1),
                        0.
                    ),
                    new OmegaForPeriods(
                        Period.ofYears(1),
                        ReturnsCalculators.ABSOLUTE,
                        0
                    ),
                    new ExecutionCount()
                ));
            }

            @Override
            public ExchangeRateTable exchangeRateTable() {
                return rates;
            }

            @Override
            public SimulatorBuilder newSimulatorBuilder() {
                SimpleSimulatorBuilder builder
                    = ((SimpleSimulatorBuilder) super.newSimulatorBuilder())
                        .useStrategyPlotter(newStrategyPlotter());
                return builder;
            }

            private StrategyPlotter newStrategyPlotter() {
                StrategyPlotter plotter
                    = new SimpleStrategyPlotter(
                        String.format(
                            "Strategy: Elliott's 2005 Pairs Trading: %s vs. %s",
                            stock1.symbol(),
                            stock2.symbol()
                        )
                    );
                return plotter;
            }

            @Override
            public LocalDateTimeInterval simulationInterval() {
                return interval();
            }
        };

        /*
         * running the simulation
         */
        PerformanceReport report = SimTemplateUtils.run(spec);
        LOGGER.info(report.toString());
    }
}
