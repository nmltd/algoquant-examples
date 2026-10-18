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
 *
 * <p><b>Model and publication.</b>
 * The thesis combines PCA dimension reduction with regressions of future accumulated log returns
 * on past accumulated principal-component returns; a separate regime decision can change how
 * deviations are traded. This is a daily-bar adaptation, not a reproduction of its one-second
 * quote experiment.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Here T=500 is the rolling log-return sample length, k=2 is the retained dimension, N is the
 * filtered IT-stock count, and H=1,21,...,201 is an accumulation/forecast horizon in
 * observations. Regime switching is disabled. SensitivityAnalyzer creates an
 * Infantino2010Strategy per parameter set; synchronized order books supply aligned prices.
 * Infantino2010LongShortConstantValue reads signal.dReturns(), uses fixed per-stock notional and
 * does not automatically invest simulator cash. The configured Commission measure records a
 * statistic; it does not by itself install a broker fee scheme. Search results on the same
 * interval are not holdout validation.
 * </p>
 * @see <a href="https://dspace.mit.edu/handle/1721.1/59122">L. R. Infantino and S. Itzhaki (2010), Developing high-frequency equities trading models, MIT MBA thesis</a>
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

    /**
     * Builds the horizon grid for one daily-data experiment. T and k stay fixed;
     * each H changes both the accumulation horizon and the prediction problem.
     * The grid is a sensitivity study, not an out-of-sample model-selection protocol.
     */
    private static List<Param> newParamList(List<Stock> stocks) {
        List<Param> params = new ArrayList<>();

        /*
         * fixed parameters
         */
        // T counts observed log-return rows. With daily Yahoo bars these are sessions,
        // whereas the thesis's one-second quotes gave T a very different time scale.
        int T = 500;
        // Retain k principal-component directions; this is not the number of stocks.
        int k = 2;

        int step = 20;
        for (int H = 1; H <= 201; H += step) { // test performance for various H
            params.add(
                new ParamH(
                    stocks,
                    T,
                    H,
                    k,
                    false, // Do not use the optional momentum/mean-reversion regime switch.
                    // This order policy fixes per-stock notional; it is not full-equity sizing.
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

    /**
     * Loads the chosen products and builds a simulator for the horizon comparisons.
     *
     * @param stocks products in a stable order shared by PCA columns and order sizing
     * @param interval daily-history interval, including data needed for model warm-up
     * @param rates currency-conversion table used to express performance consistently
     */
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

    /**
     * Runs and plots each supplied model configuration on this instance's market data.
     * A fresh strategy is constructed by the analyzer's factory for each configuration.
     *
     * @param params configurations to compare; changing H changes the forecast horizon
     * @return report mapping each configuration to its simulated performance
     * @throws MapOfExceptions if one or more parameter simulations fail
     */
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

    /**
     * Prints every configuration and its account-level performance report.
     *
     * @param report completed sensitivity results; values are not automatically ranked
     */
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
