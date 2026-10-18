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
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.algebra.linear.vector.doubles.operation.VectorFactory;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import dev.nm.number.doublearray.DoubleArrayMath;
import dev.nm.nmutils.plot.api.Chart;
import dev.nm.nmutils.plot.api.data.HistogramData;
import dev.nm.nmutils.plot.binder.ChartFactoryBinder;
import dev.nm.stat.descriptive.moment.Mean;
import dev.nm.stat.descriptive.moment.Variance;
import java.time.LocalDateTime;
import java.time.Period;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLossRealized;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.SensitivityAnalysisReport;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.SensitivityAnalyzer;
import tech.nmfin.algoquant.execution.simulation.setting.SimSetting;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.execution.strategy.StrategyFactory;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.MapOfExceptions;
import tech.nmfin.algoquant.util.ProgressListener;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * This example looks at the sensitivity of the {@linkplain SMA2} strategy to
 * changes in the lag numberOfProducts.
 *
 * <p><b>Using this example.</b>
 * Perturbs the two SMA lag coordinates around a reference vector and evaluates the resulting
 * realized P/L with SensitivityAnalyzer. NM vectors use one-based indexing; the lag values are
 * converted to integer observation counts. Yahoo access and the histogram display are runtime
 * dependencies.
 * </p>
 */
@Demo
public class SensitivityAnalyzerDemo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) throws MapOfExceptions, Exception {
        SensitivityAnalyzerDemo instance = new SensitivityAnalyzerDemo();
        instance.run();
    }

    // simulation parameters
    private final Stock STOCK = HSI.getInstance();
    private final LocalDateTimeInterval INTERVAL
        = LocalDateTimeInterval.of(
            LocalDateTime.of(2008, 1, 1, 0, 0),
            Period.ofYears(1)
        );
    private final StrategyFactory<Strategy, Vector> STRATEGY_FACTORY
        = new StrategyFactory<Strategy, Vector>() {
        @Override
        public Strategy newInstance(Vector lags) {
            return new SMA2(STOCK, (int) lags.get(1), (int) lags.get(2));
        }
    };

    public void run() throws MapOfExceptions, Exception {
        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );
        OrderBookCaches depthCaches
            = new OrderBookCaches(
                STOCK,
                yahoo.newInstance(
                    STOCK,
                    INTERVAL
                )
            );
        ExchangeRateTable rates
            = new SimpleExchangeRateTable(STOCK.currency());
        KeyedMeasure pnl = KeyedMeasure.measure(new ProfitLossRealized());
        Simulator simulator
            = new SimpleSimulatorBuilder()
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(depthCaches)
                .useExchangeRates(rates)
                // Explicit measures replace the default measure selection; keep these instances per run.
                .withMeasures(pnl)
                .build();

        Vector refParam = new DenseVector(2.0, 25.0);
        Vector dFastLag = new DenseVector(1.0, 0.0);
        Vector dSlowLag = new DenseVector(0.0, 1.0);

        SimSetting simSetting
            = new SimSetting(simulator, INTERVAL, depthCaches, rates);
        SensitivityAnalyzer<Vector> sensitivityAnalyzer
            = new SensitivityAnalyzer<>(
                simSetting,
                STRATEGY_FACTORY
            );
        SensitivityAnalysisReport<Vector> fastLagSensitivity
            = sensitivityAnalyzer.run(
                VectorFactory.getOffsetVectors(refParam, dFastLag, -1, 9),
                new ProgressListener.NoOpProgressListener()
            );
        SensitivityAnalysisReport<Vector> slowLagSensitivity
            = sensitivityAnalyzer.run(
                VectorFactory.getOffsetVectors(refParam, dSlowLag, -10, 10),
                new ProgressListener.NoOpProgressListener()
            );

        double[] fastPnls = fastLagSensitivity.performances(pnl.key());
        LOGGER.info(
            "sensitivity to changes in fast lag: mean = {}, s.d. = {}, min = {}, max = {}",
            new Mean(fastPnls).value(),
            new Variance(fastPnls).standardDeviation(),
            DoubleArrayMath.min(fastPnls),
            DoubleArrayMath.max(fastPnls)
        );

        double[] slowPnls = slowLagSensitivity.performances(pnl.key());
        LOGGER.info(
            "sensitivity to changes in slow lag: mean = {}, s.d. = {}, min = {}, max = {}",
            new Mean(slowPnls).value(),
            new Variance(slowPnls).standardDeviation(),
            DoubleArrayMath.min(slowPnls),
            DoubleArrayMath.max(slowPnls)
        );

        Chart chart
            = ChartFactoryBinder.getInstance()
                .getChartFactory()
                .createHistogramBuilder()
                .chartTitle("SensitivityAnalyzerDemo")
                .valueAxisLabel("P&L")
                .addData("slow lag", new HistogramData(slowPnls, 10))
                .addData("fast lag", new HistogramData(fastPnls, 10))
                .build();
        chart.show();
    }
}
