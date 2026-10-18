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
package tech.nmfin.algoquant.demo.misc;

import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.misc.datastructure.time.LocalDateTimeUtils;
import dev.nm.number.DoubleUtils;
import dev.nm.stat.random.rng.univariate.normal.ConcurrentStandardNormalRNG;
import dev.nm.stat.random.rng.univariate.normal.RandomStandardNormalGenerator;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import static tech.nmfin.algoquant.demo.misc.MCSimulationDemo.SIMULATOR_BUILDER_FACTORY;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLossRealized;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.simulation.batch.montecarlo.task.*;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplate;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateSimple;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.MapOfExceptions;
import tech.nmfin.algoquant.util.ProgressListener;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * <p><b>Using this example.</b>
 * Uses MCSimContext and MCSimulationTaskRunner to compare several stochastic return generators
 * under one strategy template. Each task supplies generated market data; the template
 * intentionally rejects ordinary historical-data use. SMA2 parameters count observations and
 * do not imply portfolio weights.
 * </p>
 */
@Demo
public class MCSimulationTaskRunnerDemo {

    /**
     * Runs N_RUNS simulations for {@linkplain #RESOLUTION} parameters each.
     * Each simulation runs for N_DAYS.
     */
    private final int N_DAYS = 255;
    private final int N_RUNS = 100;
    private final int RESOLUTION = 50;

    private final Product PRODUCT = HSI.getInstance();
    private final Map<Product, Double> INITIAL_PRICES
        = Collections.singletonMap(PRODUCT, 20000.0);

    private final RandomStandardNormalGenerator rnorm
        = new ConcurrentStandardNormalRNG();
    private final double SIGMA = 0.02; // The standard deviation of daily returns used by all models.

    private final SimTemplate SIM_TEMPLATE = new SimTemplateSimple() {

        @Override
        public Strategy newStrategy() {
            return new SMA2(PRODUCT, 1, 2);
        }

        @Override
        public LocalDateTimeInterval interval() {
            LocalDateTime begin = LocalDateTime.of(2011, 1, 1, 16, 0);
            LocalDateTimeInterval interval
                = LocalDateTimeInterval.of(begin, begin.plusDays(N_DAYS));
            return interval;
        }

        @Override
        public LocalDateTimeInterval simulationInterval() {
            return interval();
        }

        @Override
        public List<Product> products() {
            return Collections.singletonList(PRODUCT);
        }

        @Override
        public List<KeyedMeasure> performanceMeasures() {
            return Arrays.asList(
                measures(
                    new ProfitLossRealized(),
                    // This statistic samples completed trades, unlike calendar-period return statistics.
                    new InformationRatioForTrades()
                )
            );
        }

        @Override
        public ExchangeRateTable exchangeRateTable() {
            return new SimpleExchangeRateTable(PRODUCT.currency());
        }

        @Override
        public OrderBookCaches newOrderBookCaches() {
            throw new UnsupportedOperationException("only intended for MC simulation!");
        }

    };

    private final List<LocalDateTime> TIMES
        = LocalDateTimeUtils.periodicInstants(
            SIM_TEMPLATE.interval().start(),
            Period.ofDays(1),
            N_DAYS
        );

    public void run() throws MapOfExceptions {
        MCSimulationTaskRunner mcSimulation
            = new MCSimulationTaskRunner(
                SIM_TEMPLATE,
                SIMULATOR_BUILDER_FACTORY,
                N_RUNS
            );
        MCSimContext<Product> context
            = new MCSimContext<>(
                Arrays.asList(PRODUCT),
                INITIAL_PRICES,
                TIMES
            );

        List<MCSimulationTask<Vector>> tasks = new ArrayList<>();
        tasks.add(
            new RandomWalkTask(
                context,
                DoubleUtils.seq(-0.03, 0.01, RESOLUTION),
                SIGMA,
                rnorm
            )
        );
        tasks.add(
            new AR1Task(
                context,
                DoubleUtils.seq(-0.02, 0.02, RESOLUTION),
                SIGMA,
                rnorm
            )
        );
        tasks.add(
            new ARMA11Task(
                context,
                DoubleUtils.seq(-0.03, 0.01, RESOLUTION),
                -0.1,
                0.0,
                SIGMA, rnorm
            )
        );
        tasks.add(
            new ARCH1Task(
                context,
                Math.pow(SIGMA, 2.0),
                DoubleUtils.seq(0.0, 0.01, RESOLUTION),
                rnorm
            )
        );
        tasks.add(
            new AR1GARCH11Task(
                context,
                DoubleUtils.seq(-0.2, 0.2, RESOLUTION),
                Math.pow(SIGMA, 2.0), 0.3, 0.0,
                rnorm
            )
        );

        mcSimulation.runTasks(
            tasks,
            new ProgressListener.NoOpProgressListener()
        );
    }

    public static void main(String[] args)
        throws MapOfExceptions, InterruptedException {
        MCSimulationTaskRunnerDemo instance = new MCSimulationTaskRunnerDemo();
        instance.run();
    }
}
