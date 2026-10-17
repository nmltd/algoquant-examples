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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import static java.util.stream.Collectors.toMap;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.datatype.trade.calculator.SimpleTradeCalculator;
import tech.nmfin.algoquant.execution.datatype.trade.calculator.TradeCalculator;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForPeriods;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForTrades;
import tech.nmfin.algoquant.execution.performance.report.KeyedMeasure;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.report.SimulationResultsExporter;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.NamedParamFactory;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2Dynamic;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * A dynamically calibrated SMA2Crossover strategy.
 */
@Demo
public class SMA2DynamicDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMA2DynamicDemo.class);
    private final TradeCalculator TRADE_CALCULATOR
        = new SimpleTradeCalculator();

    public static void main(String[] args) throws Exception {
        DemoSupport.initialize(args);
        final Currency ccy = Currencies.CNY;
        final Stock stock = new SimpleStock("000001.SS", ccy, Exchange.SS);
        final double capital0 = 3300.;

        LocalDateTimeInterval interval = LocalDateTimeInterval.of(
            DemoSupport.quick() ? LocalDateTime.of(2016, 1, 1, 0, 0) : LocalDateTime.of(2010, 1, 1, 0, 0),
            DemoSupport.quick() ? LocalDateTime.of(2016, 4, 1, 0, 0) : LocalDateTime.of(2017, 3, 1, 0, 0)
        );

        // setup data source
        tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory<Stock> yahoo = DemoSupport.orderBooks();
        // cache the longest history needed so we don't download it from the web over and over again
        yahoo.newInstance(
            stock,
            LocalDateTimeInterval.of(
                interval.start().minusYears(1),
                interval.end()
            )
        );

        Collection<NamedParam> params
            = new NamedParamFactory()
                .forValues("rebalance period", Period.ofWeeks(1), Period.ofWeeks(2), Period.ofMonths(1), Period.ofMonths(3))
                .forValues("data window", Period.ofMonths(1), Period.ofMonths(2), Period.ofMonths(3), Period.ofMonths(6))
                .params();

        if (DemoSupport.quick()) {
            params = new NamedParamFactory()
                .forValues("rebalance period", Period.ofMonths(1))
                .forValues("data window", Period.ofMonths(3)).params();
        }
        SMA2DynamicDemo demo = new SMA2DynamicDemo();

        demo.run(stock, interval, yahoo, capital0, params);
    }

    public void run(
        Stock stock,
        LocalDateTimeInterval interval,
        OrderBookCacheFactory<Stock> cacheFactory,
        double capital0,
        Collection<NamedParam> params
    ) throws Exception {
        long start = System.currentTimeMillis();

        Map<NamedParam, PerformanceReport> reports = params.stream()
            .collect(toMap(
                p -> p,
                p -> simulate(newSimulator(stock, interval, capital0, cacheFactory, newMeasures()), stock, interval, cacheFactory, p)
            ));

        SimulationResultsExporter exporter = new SimulationResultsExporter();
        reports.forEach((param, report) -> exporter.add(param, report));
        exporter.writeAsCsv();

        LOGGER.info(
            "time elapsed: {} milliseconds",
            (System.currentTimeMillis() - start)
        );
    }

    private PerformanceReport simulate(
        Simulator simulator,
        Stock stock,
        LocalDateTimeInterval interval,
        OrderBookCacheFactory<Stock> cacheFactory,
        NamedParam param
    ) {
        // create the strategy
        Period rebalancePeriod = param.value("rebalance period");
        Period dataWindow = param.value("data window");
        SMA2Dynamic<Stock> strategy
            = new SMA2Dynamic<>(
                stock,
                cacheFactory, // data source
                rebalancePeriod,
                dataWindow
            );

        LOGGER.info("running strategy {}...", strategy);

        PerformanceReport report = simulator.run(strategy, interval);
        DemoSupport.verify(report);
        return report;
    }

    private List<KeyedMeasure> newMeasures() {
        return Arrays.asList(KeyedMeasure.measures(
            new ProfitLoss(),
            new InformationRatioForZeroInvestment(Period.ofYears(1), 0.),
            new InformationRatioForPeriods(
                Period.ofYears(1),
                ReturnsCalculators.SIMPLE),
            new OmegaForTrades(),
            new MaxDrawdown(),
            new MaxDrawdownPercentage(),
            new MaxDrawdownDuration(Duration.ofDays(1)),
            new WinLossRatio(TRADE_CALCULATOR),
            new WinLossSizeRatio(TRADE_CALCULATOR)
        ));
    }

    private Simulator newSimulator(
        Stock stock,
        LocalDateTimeInterval interval,
        double capital0,
        OrderBookCacheFactory<Stock> cacheFactory,
        List<KeyedMeasure> measures
    ) {
        OrderBookCaches orderBookCaches
            = new OrderBookCaches(
                cacheFactory,
                Collections.singletonList(stock),
                interval
            );
        return new SimpleSimulatorBuilder()
            .withOrderBookUpdates(orderBookCaches)
            .withInitialCash(capital0)
            .withMeasures(measures)
            .build();
    }
}
