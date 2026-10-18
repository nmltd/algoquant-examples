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
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.SimulatorBuilder;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500;
import tech.nmfin.algoquant.execution.performance.measure.ExecutionCount;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.simulation.template.*;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.Elliott2005Strategy;
import tech.nmfin.algoquant.model.meanreversion.elliott2005.strategy.MeanReversionBasedOnSpreadQuantile;
import static tech.nmfin.algoquant.util.AQUtils.formatExecutionsInTabularForm;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Runs a Elliott mean reversion strategy, coded up using a template.
 *
 * @author Haksun Li
 */
@Demo
public class MeanReversionBasedOnSpreadQuantileDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        MeanReversionBasedOnSpreadQuantileDemo demo
            = new MeanReversionBasedOnSpreadQuantileDemo();
        demo.run();
    }

    private final Stock stock1 = SP500.stock("XOM");
    private final Stock stock2 = SP500.stock("CVX");

    public void run() {
        // the simulation specifications
        SimTemplate template = new SimTemplateYahooEOD() {

            @Override
            public LocalDateTimeInterval interval() {
                return LocalDateTimeInterval.of(
                    LocalDateTime.of(2012, 1, 1, 0, 0),
                    LocalDateTime.of(2014, 10, 1, 0, 0)
                );
            }

            @Override
            public List<Stock> products() {
                List<Stock> stocks = Arrays.asList(stock1, stock2);
                return stocks;
            }

            @Override
            public Strategy newStrategy() {
                Elliott2005Strategy.Customization customization
                    = new MeanReversionBasedOnSpreadQuantile(
                        new DenseVector(1.0, -1.0), // the spread coefficients
                        0.2, // entry quantile
                        0.2 // exit quantile
                    );
                int calibrationWindow = 100;
                Elliott2005Strategy strategy
                    = new Elliott2005Strategy(
                        products(),
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
                return new SimpleExchangeRateTable(Currencies.USD);
            }

            @Override
            public SimulatorBuilder newSimulatorBuilder() {
                SimulatorBuilder builder = super.newSimulatorBuilder();
                ((SimpleSimulatorBuilder) builder)
                    .useStrategyPlotter(
                        new SimpleStrategyPlotter(
                            String.format(
                                "Elliott's 2005 Pairs Trading: %s vs. %s",
                                stock1.symbol(),
                                stock2.symbol()
                            )
                        )
                    );
                return builder;
            }

            @Override
            public LocalDateTimeInterval simulationInterval() {
                return interval();
            }
        };

        /*
         * running the simulation
         */
        PerformanceReport report = SimTemplateUtils.run(template);
        LOGGER.info(
            "Executions: {}",
            formatExecutionsInTabularForm(
                report.tradeBlotter().allExecutions()
            )
        );
        LOGGER.info(report.toString());
    }

}
