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
package tech.nmfin.algoquant.demo.model.technical;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.performance.rolling.RollingMaxExposure.ExposureType;
import tech.nmfin.algoquant.execution.simulation.template.*;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * This demonstrates how to simulate a strategy using a template.
 *
 * @author Haksun Li
 */
@Demo
public class SMA2Demo020 {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMA2Demo020.class);

    public static void main(String[] args) {
        DemoSupport.initialize(args);
        // the simulation parameters
        final Stock product = USDStock.newInstance("XOM", Exchange.NEW_YORK);
        final ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        final InformationRatioForTrades ir = new InformationRatioForTrades();
        final OmegaForTrades omega = new OmegaForTrades();
        final MaxExposure maxCapital = new MaxExposure(ExposureType.LONG);
        final int fastLag = 1;
        final int slowLag = 2;

        // the simulation specifications
        SimTemplate spec = new SimTemplateYahooEOD() {
            private final ProfitLoss pnl = new ProfitLoss();

            @Override
            public tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches newOrderBookCaches() {
                return new tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches(
                    DemoSupport.orderBooks(), products(), interval());
            }

            @Override
            public LocalDateTimeInterval interval() {
                return LocalDateTimeInterval.of(
                    LocalDateTime.of(2010, 1, 1, 0, 0),
                    LocalDateTime.of(2011, 1, 3, 0, 0)
                );
            }

            @Override
            public List<Stock> products() {
                return Collections.singletonList(product);
            }

            @Override
            public Strategy newStrategy() {
                SMA2 strategy = new SMA2(
                    product,
                    fastLag,
                    slowLag
                );
                return strategy;
            }

            @Override
            public List<KeyedMeasure> performanceMeasures() {
                return Arrays.asList(measures(
                    pnl,
                    ir,
                    omega,
                    maxCapital
                ));
            }

            @Override
            public ExchangeRateTable exchangeRateTable() {
                return rates;
            }

            @Override
            public LocalDateTimeInterval simulationInterval() {
                return interval();
            }
        };

        /*
         * running the simulation
         */
        PerformanceReport report = SimTemplateUtils.run(spec);
        DemoSupport.verify(report);
        LOGGER.info(report.toString());
        LOGGER.info("There are {} executions.", report.tradeBlotter().allExecutions().size());
    }
}
