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
package tech.nmfin.algoquant.demo.model.portfoliooptimization;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.io.File;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import tech.nmfin.algoquant.data.cache.VectorCache;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.ProductUtils;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.performance.report.ProductMeasures;
import tech.nmfin.algoquant.execution.performance.report.exporter.*;
import tech.nmfin.algoquant.execution.performance.rolling.*;
import tech.nmfin.algoquant.execution.simulation.template.*;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.portfoliooptimization.lai2010.Lai2010NPEBModelFixedLambda;
import tech.nmfin.algoquant.model.strategy.rebalance.*;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.lai2010.Lai2010NPEBModel;
import tech.nmfin.portfoliooptimization.lai2010.ceta.maximizer.CetaMaximizer;
import tech.nmfin.portfoliooptimization.lai2010.ceta.maximizer.CombinedCetaMaximizer;
import tech.nmfin.portfoliooptimization.lai2010.ceta.maximizer.GridSearchCetaMaximizer;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.ARResamplerFactory;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizer;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerLongOnly;
import tech.nmfin.returns.ReturnsCalculators;
import tech.nmfin.returns.moments.MomentsEstimatorLedoitWolf;

/**
 *
 *
 * <p><b>Using this example.</b>
 * Exercises Lai portfolio optimization with Yahoo estimation data and writes performance and
 * P/L CSV outputs under log. Resampling and optimization settings affect both runtime and the
 * solution; inspect the estimation and evaluation intervals before interpreting the
 * comparison.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Lai, Xing and Chen incorporate uncertainty in estimated moments into predictive portfolio risk
 * and develop a nonparametric empirical-Bayes resampling rule. “2010” in the API is historical
 * naming; the cited journal article appeared in 2011.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * The active configuration uses Lai2010NPEBModel, a long-only MV optimizer, Ledoit–Wolf moments,
 * an AR resampler, 100 bootstraps, lambda=1.4, a one-year calibration window and quarterly
 * rebalancing. These choices adapt the framework; they do not reproduce all models or tables
 * from the article. Follow the template’s data interval and rebalance strategy separately from
 * the CSV exporters.
 * </p>
 * @see <a href="https://doi.org/10.1214/10-AOAS422">T. L. Lai, H. Xing and Z. Chen (2011), Mean–variance portfolio optimization when means and covariances are unknown, Annals of Applied Statistics 5(2A), 798–823</a>
 */
@Demo
public class Lai2010NPEBModelDemo {

    public static void main(String[] args) {
        Lai2010NPEBModelDemo demo = new Lai2010NPEBModelDemo();
        demo.run();
    }
    // list the stocks to be traded
    private final List<Stock> stocks
        = ProductUtils.newStockList(
            new String[]{"ORCL", "AAPL", "AMZN", "IBM", "MSFT"},
            Exchange.NASDAQ,
            Currencies.USD
        );
    // specify the time period of the simulation
    private final LocalDateTimeInterval interval
        = LocalDateTimeInterval.of(
            LocalDateTime.of(2008, 1, 1, 0, 0),
            LocalDateTime.of(2013, 1, 1, 0, 0)
        );
    private final Period rebalancePeriod = Period.ofMonths(3);
    private final ExchangeRateTable rates
        = new SimpleExchangeRateTable(Currencies.USD);
    private final DateTimeFormatter formatter
        = DateTimeFormatter.BASIC_ISO_DATE;

    public void run() {
        SimTemplate sim0 = new SimTemplateSimple() {

            @Override
            public LocalDateTimeInterval interval() {
                return LocalDateTimeInterval.of(
                    interval.start().minusDays(5),
                    interval.end()
                );
            }

            @Override
            public List<Stock> products() {
                return Collections.unmodifiableList(stocks);
            }

            @Override
            public Strategy newStrategy() {
                MVOptimizer longOnlyOptimizer
                    = new MVOptimizerLongOnly();
                final int nBootstraps = 100;
                final double lambda = 1.4;
                final Period calibrationWindow = Period.ofYears(1);

                Lai2010NPEBModel model
                    = new Lai2010NPEBModel(
                        longOnlyOptimizer,
                        nBootstraps,
                        new MomentsEstimatorLedoitWolf(),
                        new ARResamplerFactory(),
                        //                                           new GARCHResamplerFactory(),
                        new CombinedCetaMaximizer(
                            new CetaMaximizer[]{
                                new GridSearchCetaMaximizer(0.1)
                            }
                        )
                    );
                PortfolioOptimizationAlgorithm optimizer
                    = new Lai2010NPEBModelFixedLambda<>(model, lambda);

                RebalancePortfolio<Stock> strategy
                    = new RebalancePortfolioTechnical<>(
                        stocks,
                        optimizer,
                        new YahooCacheProvider(),
                        calibrationWindow,
                        rebalancePeriod
                    );

                return strategy;
            }

            @Override
            public OrderBookCaches newOrderBookCaches() {
                YahooOrderBookCacheFactory<Stock> yahoo
                    = new YahooOrderBookCacheFactory<>(
                        SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
                    );
                return new OrderBookCaches(
                    yahoo,
                    stocks,
                    interval()
                );
            }

            @Override
            public ExchangeRateTable exchangeRateTable() {
                return rates;
            }

            @Override
            public List<KeyedMeasure> performanceMeasures() {
                double commissionRate = 0.005;

                return Arrays.asList(KeyedMeasure.measures(
                    new ProfitLoss(),
                    new Commission(commissionRate),
                    // The period is a calendar return bucket; the constant benchmark is a return per bucket.
                    new InformationRatioForPeriods(
                        Period.ofYears(1),
                        ReturnsCalculators.LOG,
                        0.
                    ),
                    new MaxDrawdownPercentage(),
                    new ExecutionCount()
                ));
            }

            @Override
            public LocalDateTimeInterval simulationInterval() {
                return interval();
            }
        };

        SimTemplate sim
            = new SimTemplatePortfolioRebalancing(
                sim0,
                interval,
                rebalancePeriod
            );

        PerformanceReport report = SimTemplateUtils.run(sim);

        // output the performance report
        PerformanceReportExporter exporter1 = new PerformanceReportCsvExporter();
        String reportFilename
            = String.format(
                "./log/lai2010NPEBModelDemo-performance.%s-%s.csv",
                formatter.format(interval.start()),
                formatter.format(interval.end())
            );
        exporter1.export(report, new File(reportFilename));

        RollingMeasureFactory measureFactory = new RollingMeasureFactory() {
            @Override
            public RollingMeasure newInstance() {
                return new RollingProfitLoss(rates);
            }
        };
        ProductMeasures measures = new ProductMeasures(measureFactory);
        List<Execution> executions = report.tradeBlotter().allExecutions();
        VectorCache<Double> cache = measures.valueOf(
            stocks,
            executions,
            sim.newOrderBookCaches());
        String pnlFilename
            = String.format(
                "./log/lai2010NPEBModelDemo-pnl.%s-%s.csv",
                formatter.format(interval.start()),
                formatter.format(interval.end())
            );
        VectorCacheCsvExporter exporter2
            = new VectorCacheCsvExporter(new File(pnlFilename));
        exporter2.write(stocks, cache);
    }
}
