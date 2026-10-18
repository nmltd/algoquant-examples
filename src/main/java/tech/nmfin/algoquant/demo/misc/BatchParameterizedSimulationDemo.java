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

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import dev.nm.number.DoubleUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.VFINX;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.report.SimulationResultsExporter;
import tech.nmfin.algoquant.execution.simulation.batch.BatchParameterizedSimulation;
import tech.nmfin.algoquant.execution.simulation.setting.SimSetting;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.StrategyFactory;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2.Param;
import tech.nmfin.algoquant.util.MapOfExceptions;
import tech.nmfin.algoquant.util.ProgressListener;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * Demonstrates how to use {@linkplain BatchParameterizedSimulation}.
 */
@Demo
public class BatchParameterizedSimulationDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        BatchParameterizedSimulationDemo demo
            = new BatchParameterizedSimulationDemo();
        demo.run();
    }

    private final String outputDir = "./log/demo";
    private final Stock product = VFINX.getInstance();
    private final LocalDateTimeInterval interval
        = LocalDateTimeInterval.of(
            LocalDateTime.of(2008, 1, 1, 0, 0),
            Period.ofYears(3)
        );
    private final ExchangeRateTable rates
        = new SimpleExchangeRateTable(product.currency());

    public void run() {
        OrderBookCaches depthCaches = getDepthCaches();
        Simulator simulator
            = new SimpleSimulatorBuilder()
                .withOrderBookUpdates(depthCaches)
                .useExchangeRates(rates)
                .withMeasures(getPerformanceMeasures())
                .build();

        SimSetting simSetting
            = new SimSetting(simulator, interval, depthCaches, rates);
        BatchParameterizedSimulation simulation
            = new BatchParameterizedSimulation(simSetting);

        try {
            Map<SMA2.Param, PerformanceReport> reports
                = simulation.run(
                    new StrategyFactory<SMA2, SMA2.Param>() {

                    @Override
                    public SMA2 newInstance(Param param) {
                        return new SMA2(
                            param.product(),
                            param.fastLag(),
                            param.slowLag()
                        );
                    }
                },
                    getParamList(),
                    new ProgressListener.NoOpProgressListener()
                );

            saveResults(reports);
        } catch (MapOfExceptions ex) {
            LOGGER.error("some simulation runs failed", ex);
        }
    }

    private List<SMA2.Param> getParamList() {
        int[] lags = DoubleUtils.seq(1, 10);

        int nParams = lags.length * (lags.length - 1) / 2;
        List<SMA2.Param> params = new ArrayList<>(nParams);
        for (int fast = 0; fast < lags.length; ++fast) {
            for (int slow = fast + 1; slow < lags.length; ++slow) {

                SMA2.Param param
                    = new SMA2.Param(product, lags[fast], lags[slow]);

                params.add(param);
            }
        }

        return params;
    }

    private OrderBookCaches getDepthCaches() {
        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );
        return new OrderBookCaches(
            yahoo,
            Collections.singletonList(product),
            interval
        );
    }

    private List<KeyedMeasure> getPerformanceMeasures() {
        return Arrays.asList(measures(
            new ProfitLoss(),
            new InformationRatioForTrades(),
            new OmegaForTrades()
        ));
    }

    private void saveResults(
        Map<SMA2.Param, PerformanceReport> reports
    ) {
        LOGGER.info("saving simulation results...");
        try {
            SimulationResultsExporter exporter
                = new SimulationResultsExporter();
            reports.forEach((p, report) -> {
                exporter.add(
                    new NamedParam()
                        .set("product", p.product().symbol())
                        .set("slow-lag", p.slowLag())
                        .set("fast-lag", p.fastLag()),
                    report
                );
            });

            exporter.writeMeasuresAsCsv(outputDir + "/report.csv");

        } catch (IOException ex) {
            LOGGER.error("error when saving performance reports", ex);
        }
    }
}
