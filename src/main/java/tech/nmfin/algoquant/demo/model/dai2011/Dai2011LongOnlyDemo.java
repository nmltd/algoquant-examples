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
package tech.nmfin.algoquant.demo.model.dai2011;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import java.time.Period;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.ListedProduct;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.performance.measure.MaxDrawdownPercentage;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.model.trend.dai2011.strategy.Dai2011LongOnly;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates the Dai's optimal trend following trading strategy.
 *
 * <p><b>Using this example.</b>
 * Constructs Dai2011LongOnly, supplies Yahoo order books and initial capital, then runs the
 * simulator and reads its PerformanceReport. The plotter opens a desktop chart. Inspect the
 * model parameters and trade sizing independently of the initial cash balance.
 * </p>
 */
@Demo
public class Dai2011LongOnlyDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final String YAHOO_DATA_FOLDER = "./log/Yahoo";

    public static void main(String[] args) throws Exception {
        Stock stock = new SimpleStock("^GSPC", Currencies.USD, Exchange.NYSE);
        Dai2011LongOnlyDemo demo = new Dai2011LongOnlyDemo();
        demo.run(stock);
    }

    public void run(Stock stock) throws Exception {
        // set the denomination currency
        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        // trading horizon
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2006, 1, 1, 0, 0),
                LocalDateTime.of(2015, 12, 31, 0, 0)
            );
        OrderBookCacheFactory<ListedProduct> orderBookCacheFactory
            = new YahooOrderBookCacheFactory<>(YAHOO_DATA_FOLDER);

        OrderBookCaches orderBookCaches = new OrderBookCaches();
        orderBookCaches.addCache(
            stock,
            orderBookCacheFactory.newInstance(stock, interval)
        );

        Period burnin = Period.ofYears(3);
        Period recalibration = Period.ofYears(1);
        int tau_window = 21;
        double threshold = 0.10;
        double rho = 0; //0.03;
        double capital0 = 1e6;

        Dai2011LongOnly strategy
            = new Dai2011LongOnly(
                stock,
                interval.start(),
                burnin,
                recalibration,
                tau_window,
                threshold,
                rho
            );
        Simulator simulator
            = new SimpleSimulatorBuilder()
                // Supply product-keyed, chronological price events to the simulator.
                .withOrderBookUpdates(orderBookCaches) // true price
                // Initial cash funds the account; order quantities are determined by the strategy.
                .withInitialCash(capital0)
                .useExchangeRates(rates)
                .useStrategyPlotter(
                    new SimpleStrategyPlotter(
                        String.format(
                            "Dai's 2011 Optimal Trend Following Strategy: %s",
                            stock.symbol()
                        )
                    )
                )
                // Explicit measures replace the default measure selection; keep these instances per run.
                .withMeasures(measures(
                    new ProfitLoss(),
                    new MaxDrawdownPercentage(),
                    // The period is a calendar return bucket; the constant benchmark is a return per bucket.
                    new InformationRatioForPeriods(
                        Period.ofYears(1),
                        ReturnsCalculators.SIMPLE,
                        0.
                    ),
                    // The threshold is expressed as a return per selected period, not an annual cash rate.
                    new OmegaForPeriods(
                        Period.ofYears(1),
                        ReturnsCalculators.ABSOLUTE,
                        0.
                    ))
                )
                .build();
        // run delivers events and applies the configured execution model; inspect fills as well as NAV.
        PerformanceReport report = simulator.run(strategy, interval);

        LOGGER.info(report.toString());
    }
}
