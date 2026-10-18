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

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulator;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates how to set up the simulator to simulate the tutorial strategy,
 * {@linkplain JanuaryEffectStrategy}.
 */
@Demo
public class JanuaryEffectStrategyDemo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) throws Exception {
        JanuaryEffectStrategyDemo demo = new JanuaryEffectStrategyDemo();
        demo.run();
    }

    private final Stock STOCK = HSI.getInstance();
    private final LocalDateTimeInterval INTERVAL
        = LocalDateTimeInterval.of(
            LocalDateTime.of(2000, 1, 1, 0, 0),
            LocalDateTime.of(2012, 2, 1, 0, 0)
        );
    private final MonthDay BUY_DATE = MonthDay.of(12, 29);
    private final MonthDay SELL_DATE = MonthDay.of(1, 6);

    public void run() throws Exception {
        // construct an instance of the strategy to simulate
        Strategy strategy
            = new JanuaryEffectStrategy(STOCK, BUY_DATE, SELL_DATE);

        // set up a simulator to host the strategy
        SimpleSimulator simulator = newSimulator();

        // run the simulation to obtain the strategy's performance
        PerformanceReport report = simulator.run(strategy, INTERVAL);

        // print out performance report
        LOGGER.info("report = {}", report);
    }

    private SimpleSimulator newSimulator() {
        double initialCapital = 1e6;
        return new SimpleSimulatorBuilder()
            .useExchangeRates(new SimpleExchangeRateTable(STOCK.currency()))
            .withInitialCash(initialCapital)
            .useStrategyPlotter(
                new SimpleStrategyPlotter(
                    "Simple January Effect Strategy on " + STOCK.symbol()
                )
            )
            .withMeasures(measures())
            .build();
    }

    private List<KeyedMeasure> measures() {
        return Arrays.asList(KeyedMeasure.measures(
            new ProfitLoss(), // P/L
            new ExecutionCount(), // no. of executions
            new InformationRatioForPeriods( // information ratio
                Period.ofYears(1), // return period
                ReturnsCalculators.SIMPLE, // return type
                0. // benchmark return
            ),
            new OmegaForPeriods( // omega
                Period.ofYears(1), // return period
                ReturnsCalculators.SIMPLE, // return type
                0. // loss threshold
            )
        ));
    }
}
