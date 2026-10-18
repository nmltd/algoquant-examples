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
package tech.nmfin.algoquant.demo.model.volarb;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanReversionStrategy;
import tech.nmfin.algoquant.model.meanreversion.volarb.MRModelFixedGrid;
import tech.nmfin.algoquant.model.meanreversion.volarb.MeanEstimatorConstant;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.sgx.SGXTickDataCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.SimpleStrategyPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.basket.Basket;
import tech.nmfin.algoquant.execution.datatype.product.basket.BasketUtils;
import tech.nmfin.algoquant.execution.datatype.product.derivative.futures.sgx.SGXFutures;
import static tech.nmfin.algoquant.execution.datatype.product.derivative.futures.sgx.SGXFuturesFactories.NIKKEI_225_FUTURES;
import static tech.nmfin.algoquant.execution.datatype.product.derivative.futures.sgx.SGXFuturesFactories.SiMSCI_FUTURES;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForTrades;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 *
 * @author Chung Lee
 */
@Demo
public class MRModelFixedGridOnSGXDataDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) throws Exception {
        // setup data source
        SGXFutures NIKKEIFutures = NIKKEI_225_FUTURES.newContract(2010, 6);
        SGXFutures SiMSCIFutures = SiMSCI_FUTURES.newContract(2010, 6);

        OrderBookCacheFactory<SGXFutures> SiMSCICache
            = new SGXTickDataCacheFactory("./test-data/sgx/SiMSCI");
        OrderBookCacheFactory<SGXFutures> NIKKEICache
            = new SGXTickDataCacheFactory("./test-data/sgx/NK");

        // simulation period
        LocalDateTime begin = LocalDateTime.of(2010, 6, 1, 0, 0);
        LocalDateTime end = begin.plusMonths(1);
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        rates.setExchangeRate(Currencies.SGD, 0.77);
        rates.setExchangeRate(Currencies.JPY, 0.0124988);

        // create the strategy
        Basket<Product> basket
            = BasketUtils.newPairBasket(SiMSCIFutures, NIKKEIFutures, -0.015);
        Strategy strategy
            = new MeanReversionStrategy(
                basket,
                new MRModelFixedGrid(50, new MeanEstimatorConstant()),
                1e3, // scale
                1 // lot numberOfProducts
            );

        // create source of market order book
        OrderBookCaches orderBookCaches = new OrderBookCaches();
        orderBookCaches.addCache(
            SiMSCIFutures, SiMSCICache.newInstance(SiMSCIFutures, interval)
        );
        orderBookCaches.addCache(
            NIKKEIFutures, NIKKEICache.newInstance(NIKKEIFutures, interval)
        );

        // run simulation
        Simulator simulator = new SimpleSimulatorBuilder()
            .withOrderBookUpdates(orderBookCaches)
            .useExchangeRates(rates)
            .useStrategyPlotter(
                new SimpleStrategyPlotter(
                    String.format(
                        "Fixed-Grid Mean Reversion on %s~%s",
                        SiMSCIFutures.symbol(),
                        NIKKEIFutures.symbol()
                    )
                )) // new NoOpPlotter());// turn off plotting for faster performance
            .withMeasures(KeyedMeasure.measures(
                new ProfitLoss(),
                new InformationRatioForTrades(),
                new OmegaForTrades()
            ))
            .build();
        PerformanceReport report = simulator.run(strategy, interval);
        LOGGER.info("report = {}", report);
    }

    private MRModelFixedGridOnSGXDataDemo() {
    }
}
