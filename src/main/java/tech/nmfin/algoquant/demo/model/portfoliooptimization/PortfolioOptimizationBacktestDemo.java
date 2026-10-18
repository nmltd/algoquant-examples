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

import dev.nm.algebra.linear.matrix.doubles.matrixtype.sparse.SparseVector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import dev.nm.solver.multivariate.constrained.convex.sdp.socp.problem.portfoliooptimization.SOCPPortfolioConstraint;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static java.util.stream.Collectors.toList;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.ListedProduct;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.NASDAQStock;
import tech.nmfin.algoquant.model.portfoliooptimization.SOCPOptimizationAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.PortfolioOptimizationSimulation;
import tech.nmfin.algoquant.model.portfoliooptimization.socp.constraints.SOCPUnity;
import tech.nmfin.algoquant.model.strategy.rebalance.RebalancePortfolio;
import tech.nmfin.algoquant.model.strategy.rebalance.YahooCacheProvider;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;

/**
 * Backtests a particular optimizer to see its performance over time using
 * historical data.
 *
 *
 * <p><b>Using this example.</b>
 * Evaluates a portfolio optimizer over historical windows rather than computing one static
 * allocation. Read the configured estimation, rebalancing and simulation periods together.
 * Training inputs must be available before the corresponding rebalance decision.
 * </p>
 */
public class PortfolioOptimizationBacktestDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        PortfolioOptimizationBacktestDemo demo
            = new PortfolioOptimizationBacktestDemo();
        demo.run();
    }

    private void run() {
        List<Product> products
            = Arrays.asList(
                NASDAQStock.newInstance("AAPL"),
                NASDAQStock.newInstance("GOOG"),
                NASDAQStock.newInstance("MSFT")
            );
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2015, 1, 1, 0, 0),
                LocalDateTime.of(2017, 1, 1, 0, 0)
            );
        Period rebalancingPeriod = Period.ofMonths(3);
        Period calibrationWindow = Period.ofMonths(6);
        OrderBookCaches orderBookCaches
            = orderBookCaches(products, interval, calibrationWindow);
        double riskParameter = 0.1;
        List<SOCPPortfolioConstraint> constraints
            = Collections.singletonList(
                new SOCPUnity(new SparseVector(products.size()))
            );
        PortfolioOptimizationAlgorithm optimizer
            = new SOCPOptimizationAlgorithm(riskParameter, constraints);

        PortfolioOptimizationSimulation backtest
            = new PortfolioOptimizationSimulation(
                new RebalancePortfolio.ProductChooser<Product>() {
                @Override
                public List<Product> chooseProducts(LocalDateTime now) {
                    return new ArrayList<>(products);
                }

                @Override
                public Set<Product> allProducts(LocalDateTimeInterval interval) {
                    return new LinkedHashSet<>(products);
                }
            },
                orderBookCaches,
                optimizer,
                interval,
                rebalancingPeriod,
                calibrationWindow,
                new YahooCacheProvider()
            );

        PortfolioOptimizationSimulation.Results results = backtest.run();
        LOGGER.info("results: {}", results);
    }

    private OrderBookCaches orderBookCaches(
        List<Product> products,
        LocalDateTimeInterval interval,
        Period calibrationWindow
    ) {
        String dir
            = String.format(
                "./log/%s",
                LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            );
        return new OrderBookCaches(
            new YahooOrderBookCacheFactory<>(dir),
            products.stream().map(p -> (ListedProduct) p).collect(toList()),
            LocalDateTimeInterval.of(
                interval.start().minus(calibrationWindow),
                interval.end()
            )
        );
    }
}
