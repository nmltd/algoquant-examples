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

import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.analysis.function.rn2r1.RealScalarFunction;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.portfolio.weighting.PortfolioWeights;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HKEXStock;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.portfoliooptimization.performance.PerformanceOptimizedYahooEODPortfolio;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 *
 * @author Haksun Li
 */
@Demo
public class PerformanceOptimizedYahooEODPortfolioDemo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) throws Exception {
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2008, 1, 1, 0, 0),
                LocalDateTime.of(2015, 1, 1, 0, 0)
            );

        List<Stock> stocks = new ArrayList<>();
        stocks.add(HKEXStock.newInstance("0019.HK"));
        stocks.add(HKEXStock.newInstance("0483.HK"));
        stocks.add(HKEXStock.newInstance("0321.HK"));
        stocks.add(HKEXStock.newInstance("0005.HK"));

        double initialCapital = 1e2;
        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.HKD);

        PerformanceOptimizedYahooEODPortfolio.Template template
            = new PerformanceOptimizedYahooEODPortfolio.Template(
                stocks,
                rates
            ) {
            @Override
            public Map<KeyedMeasure, Double> newPerformanceMeasures() {
                Map<KeyedMeasure, Double> measures = new HashMap<>();
                measures.put(
                    measure(
                        new InformationRatioForPeriods(
                            Period.ofYears(1),
                            ReturnsCalculators.SIMPLE,
                            0.
                        )
                    ),
                    1.0
                ); // coefficients
                measures.put(
                    measure(new ProfitLoss()),
                    0.7 / initialCapital
                );  // normalized coefficients
                measures.put(
                    measure(new MaxDrawdown()),
                    0.3 / initialCapital
                );  // normalized coefficients

                return measures;
            }
        };

        PerformanceOptimizedYahooEODPortfolio optim
            = new PerformanceOptimizedYahooEODPortfolio(template);
        PortfolioWeights<Product> weights = optim.getOptimalWeights(interval);
        LOGGER.info("weights = {}", weights);

        RealScalarFunction f = optim.getObjectiveFunction(interval);
        Vector x = weights.toVector();
        double fx = f.evaluate(x);
        LOGGER.info("f(x) = {}", fx);

        // redo the simulation
        OrderBookCaches orderBookCaches
            = template.newOrderBookCachesFactory().newOrderBookCaches(interval);
        Simulator simulator
            = new SimpleSimulatorBuilder()
                .withOrderBookUpdates(orderBookCaches)
                .withMeasures(
                    template.newPerformanceMeasures().keySet()
                )
                .build();
        Strategy strategy = optim.newStrategy(x);
        PerformanceReport report = simulator.run(strategy, interval);

        LOGGER.info(report.toString());
    }
}
