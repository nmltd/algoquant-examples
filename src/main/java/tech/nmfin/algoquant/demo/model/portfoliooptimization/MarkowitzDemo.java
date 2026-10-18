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
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
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
import tech.nmfin.algoquant.execution.simulation.template.*;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.portfoliooptimization.markowitz.Markowitz1952;
import tech.nmfin.algoquant.model.strategy.rebalance.*;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.portfoliooptimization.markowitz.constraints.QPConstraint;
import tech.nmfin.portfoliooptimization.markowitz.constraints.QPMinWeights;
import tech.nmfin.returns.ReturnsCalculators;

/**
 *
 * @author Chung Lee
 */
@Demo
public class MarkowitzDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        MarkowitzDemo demo = new MarkowitzDemo();
        demo.run();
    }

    private void run() {
        final Currency currency = Currencies.HKD;

        /**
         * list the products to be traded
         */
        List<Stock> stocks
            = ProductUtils.newStockList(
                new String[]{
                    "0019.HK",
                    "0483.HK",
                    "0321.HK",
                    "0001.HK"
                // "2823.HK",
                // "0173.HK",
                },
                Exchange.HKEX,
                currency
            );
        ExchangeRateTable rates
            = new SimpleExchangeRateTable(currency);
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2005, 6, 1, 0, 0),
                LocalDateTime.of(2011, 10, 1, 0, 0)
            );
        Period rebalancingPeriod = Period.ofMonths(3);

        /**
         * According to the original Markowitz portfolio theory, the portfolio
         * is rebalanced every 3 months. The returns and covariances are
         * estimated using 4 quarterly fixings.
         */
        SimTemplate sim0 = new SimTemplateSimple() {
            @Override
            public LocalDateTimeInterval interval() {
                return interval;
            }

            @Override
            public List<Stock> products() {
                return Collections.unmodifiableList(stocks);
            }

            @Override
            public Strategy newStrategy() {
                QPConstraint constraints
                    = new QPMinWeights(stocks.size(), -0.1);
                // QPConstraint constraints = new NoConstraint();
                // QPConstraint constraints = new NoShortSelling(products.length);
                Markowitz1952 markowitz1952
                    = new Markowitz1952(0.5, constraints);

                RebalancePortfolio<Stock> strategy
                    = new RebalancePortfolioTechnical<>(
                        stocks,
                        markowitz1952,
                        new YahooCacheProvider(),
                        Period.ofYears(1),
                        rebalancingPeriod
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
                    LocalDateTimeInterval.of(
                        interval.start().minusDays(1),
                        interval.end()
                    )
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
                rebalancingPeriod
            );

        PerformanceReport report = SimTemplateUtils.run(sim);
        LOGGER.info("report = {}", report);
    }
}
