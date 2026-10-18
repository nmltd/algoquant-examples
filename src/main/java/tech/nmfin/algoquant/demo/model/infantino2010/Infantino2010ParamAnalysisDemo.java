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
package tech.nmfin.algoquant.demo.model.infantino2010;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.GICSSector;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.SensitivityAnalysisReport;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.SensitivityAnalyzer;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.plotter.GenericParamPlotter;
import tech.nmfin.algoquant.execution.simulation.batch.sensitivity.plotter.SensitivityPlotterUtils;
import tech.nmfin.algoquant.execution.simulation.setting.SimSetting;
import tech.nmfin.algoquant.model.signal.infantino2010.strategy.Infantino2010Strategy;
import tech.nmfin.algoquant.model.signal.infantino2010.strategy.Infantino2010Strategy.Param;
import tech.nmfin.algoquant.model.signal.infantino2010.strategy.order.Infantino2010LongShortConstantValue;
import tech.nmfin.algoquant.model.signal.infantino2010.strategy.order.Infantino2010Orders;
import static tech.nmfin.algoquant.util.AQUtils.removeNewProducts;
import tech.nmfin.algoquant.util.MapOfExceptions;
import tech.nmfin.algoquant.util.ProgressListener.NoOpProgressListener;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * <p><b>Using this example.</b>
 * Uses SensitivityAnalyzer to evaluate Infantino strategy parameter sets on Yahoo history. The
 * stock universe is filtered for data availability before simulation. Model fitting windows,
 * forecast horizons and retained components have separate roles; parameter-search results are
 * in-sample comparisons unless you add a holdout period.
 * </p>
 */
@Demo
public class Infantino2010ParamAnalysisDemo {

    private static final OrderBookCacheFactory<Stock> CACHE_FACTORY
        = new YahooOrderBookCacheFactory<>("./log/Yahoo");

    public static void main(String[] args) throws MapOfExceptions {
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2007, 1, 3, 16, 0),
                LocalDateTime.of(2014, 12, 31, 16, 0)
            );

        List<Stock> stocks
            = new ArrayList<>(
                SP500.sectorStocks(GICSSector.INFORMATION_TECHNOLOGY)
            );
        stocks = removeNewProducts(stocks, interval, CACHE_FACTORY);

        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);

        Infantino2010ParamAnalysisDemo demo
            = new Infantino2010ParamAnalysisDemo(stocks, interval, rates);
        demo.run(newParamList(stocks));
    }

    /**
     * For pretty printing the sensitivity analysis report.
     */
    private static class ParamH extends Infantino2010Strategy.Param {

        private final int H;

        public ParamH(
            List<? extends Product> products,
            int T,
            int H,
            int k,
            boolean useRegimeSwitching,
            Infantino2010Orders infantinoOrders
        ) {
            super(products, T, H, k, useRegimeSwitching, infantinoOrders);
            this.H = H;
        }

        @Override
        public String toString() {
            return String.format("%d", H);
        }
    }

    private static List<Param> newParamList(List<Stock> stocks) {
        List<Param> params = new ArrayList<>();

        /*
         * fixed parameters
         */
        int T = 500;
        int k = 2;

        int step = 20;
        for (int H = 1; H <= 201; H += step) { // test performance for various H
            params.add(
                new ParamH(
                    stocks,
                    T,
                    H,
                    k,
                    false,
                    new Infantino2010LongShortConstantValue(stocks)
                )
            );
        }

        return params;
    }

    private final ExchangeRateTable rates;
    private final LocalDateTimeInterval interval;
    private final OrderBookCaches caches;
    private final Simulator simulator;

    public Infantino2010ParamAnalysisDemo(
        List<Stock> stocks,
        LocalDateTimeInterval interval,
        ExchangeRateTable rates
    ) {
        this.rates = rates;
        this.interval = interval;
        this.caches = new OrderBookCaches(CACHE_FACTORY, stocks, interval);
        this.simulator
            = new SimpleSimulatorBuilder()
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(caches)
                .useExchangeRates(rates)
                .synchronizeOrderBookUpdates()
                // Explicit measures replace the default measure selection; keep these instances per run.
                .withMeasures(newMeasures(interval))
                .build();
    }

    private List<KeyedMeasure> newMeasures(LocalDateTimeInterval interval) {
        double commissionRate = 0.005;
        return Arrays.asList(KeyedMeasure.measures(
            new ProfitLoss(),
            new Commission(commissionRate),
            new InformationRatioForZeroInvestment(Period.ofYears(1), 0.),
            new MaxDrawdownPercentage(),
            new ExecutionCount()
        ));
    }

    public SensitivityAnalysisReport<Param> run(
        List<Param> params
    ) throws MapOfExceptions {
        SimSetting simSetting
            = new SimSetting(simulator, interval, caches, rates);
        SensitivityAnalyzer<Param> analyzer
            = new SensitivityAnalyzer<>(
                simSetting,
                (Param param) -> new Infantino2010Strategy(param)
            );
        SensitivityAnalysisReport<Param> report
            = analyzer.run(
                params,
                new NoOpProgressListener()
            );

        SensitivityPlotterUtils.<Param>plotAllMeasures(
            new GenericParamPlotter<>(true),
            report
        );
        printReport(report);

        return report;
    }

    public static void printReport(SensitivityAnalysisReport<Param> report) {
        for (Param param : report.values()) {
            System.out.printf(
                "%s:%n%s",
                param.toString(),
                report.performanceReport(param).toString()
            );
        }
    }
}
