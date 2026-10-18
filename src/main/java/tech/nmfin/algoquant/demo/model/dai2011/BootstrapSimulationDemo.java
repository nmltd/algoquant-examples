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
import dev.nm.misc.parallel.*;
import dev.nm.nmutils.NMUtils;
import dev.nm.nmutils.io.NMIO;
import dev.nm.number.DoubleUtils;
import dev.nm.stat.random.sampler.resampler.Resampler;
import dev.nm.stat.random.sampler.resampler.bootstrap.block.PattonPolitisWhite2009;
import java.io.*;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.component.chart.plotter.NoOpPlotter;
import tech.nmfin.algoquant.execution.component.simulator.SimpleSimulatorBuilder;
import tech.nmfin.algoquant.execution.component.simulator.Simulator;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.SimpleExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.performance.measure.ProfitLoss;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatioForZeroInvestment;
import tech.nmfin.algoquant.execution.performance.measure.omega.OmegaForPeriods;
import static tech.nmfin.algoquant.execution.performance.report.KeyedMeasure.measures;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.model.trend.dai2011.strategy.Dai2011LongShort;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Demonstrates the bootstrap simulation of the Dai's trend following strategy.
 *
 * <p><b>Using this example.</b>
 * Resamples log returns for the Dai trend-following strategy, reconstructs price paths and
 * runs a fresh strategy on each path. The output file under log/demo contains scenario
 * reports. Sampled paths are hypothetical and the chosen block lengths and randomization
 * affect the distribution.
 * </p>
 */
@Demo
public class BootstrapSimulationDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    private static final String YAHOO_DATA_FOLDER = "./log/Yahoo";
    private static final String OUTPUT_FOLDER = "./log/demo";
    private static final String REPORT_FILE_NAME = "trendfollowing_reports.txt";

    public static void main(String[] args) throws Exception {
        Product product = new SimpleStock("^GSPC", Currencies.USD, Exchange.NYSE);
        BootstrapSimulationDemo demo = new BootstrapSimulationDemo();
        demo.run(product);
    }

    public void run(
        final Product product
    ) throws Exception {
        // set the denomination currency
        ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
        Stock stock = (Stock) product;
        // trading horizon
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2003, 1, 1, 0, 0),
                LocalDateTime.of(2013, 1, 1, 0, 0)
            );
        SequentialCache<OrderBook> cache
            = new YahooOrderBookCacheFactory<>(YAHOO_DATA_FOLDER)
                .newInstance(stock, interval);

        // setup parameters
        Period initial = Period.ofYears(3);
        Period recalibration = Period.ofYears(1);
        int obs = 21;
        double capital = 1e6;
        double threshold = 0.10;
        double rho = 0.03;

        // get log returns
        double[] prices = getOriginalPrices(cache);
        int size = prices.length;
        double[] logReturns = new double[size - 1];
        for (int i = 0; i < size - 1; ++i) {
            logReturns[i] = Math.log(prices[i + 1] / prices[i]);
        }
        PattonPolitisWhite2009 bootstrap
            = new PattonPolitisWhite2009(logReturns);

        // setup for parallel running
        boolean parallelized = true;
        ParallelExecutor EXECUTOR = new ParallelExecutor("Dai2011");
        int samples = 5;
        List<PerformanceReport> reports = new ArrayList<>(samples);

        try {
            EXECUTOR.conditionalForLoop(parallelized, 0, samples, new LoopBody() {
                @Override
                public void run(int i) throws Exception {
                    // construct the bootstrap prices
                    double[] pricesResample
                        = bootstrapPrices(prices[0], bootstrap);
                    SequentialCache<OrderBook> newCache
                        = getNewPriceCache(cache, pricesResample, product);
                    OrderBookCaches orderBookCaches = new OrderBookCaches();
                    orderBookCaches.addCache(product, newCache);

                    Dai2011LongShort strategy
                        = new Dai2011LongShort(
                            product,
                            interval.start(),
                            initial,
                            recalibration,
                            obs,
                            threshold,
                            rho
                        );
                    Simulator simulator
                        = new SimpleSimulatorBuilder()
                            // Supply product-keyed, chronological price events to the simulator.
                            .withOrderBookUpdates(orderBookCaches)
                            // Initial cash funds the account; order quantities are determined by the strategy.
                            .withInitialCash(capital)
                            .useExchangeRates(rates)
                            .useStrategyPlotter(new NoOpPlotter()) // no plotter
                            //.useStrategyPlotter(new SimpleStrategyPlotter("Dai's 2011 Optimal Trend Following: " + product.symbol()))
                            // Explicit measures replace the default measure selection; keep these instances per run.
                            .withMeasures(measures(
                                new ProfitLoss(),
                                new InformationRatioForZeroInvestment(
                                    Period.ofYears(1),
                                    0.
                                ),
                                // The threshold is expressed as a return per selected period, not an annual cash rate.
                                new OmegaForPeriods(
                                    Period.ofYears(1),
                                    ReturnsCalculators.ABSOLUTE,
                                    0.
                                )
                            ))
                            .build(); // true price
                    // run delivers events and applies the configured execution model; inspect fills as well as NAV.
                    PerformanceReport report = simulator.run(strategy, interval);

                    reports.add(report);
                }
            });
        } catch (MultipleExecutionException ex) {
            LOGGER.error("some simulation runs failed", ex);
        }
        savePerformanceReport(reports);
    }

    private SequentialCache<OrderBook> getNewPriceCache(
        SequentialCache<OrderBook> cache,
        double[] pricesResample,
        Product product
    ) {
        final List<TimedEntry<OrderBook>> newPriceList
            = new ArrayList<>(pricesResample.length);
        int i = -1;
        for (TimedEntry<OrderBook> it : cache) {
            newPriceList.add(new TimedEntry<>(
                it.time(),
                new OrderBook(product, pricesResample[++i])
            )
            );
        }

        return new SequentialCache<OrderBook>() {
            @Override
            public Iterator<TimedEntry<OrderBook>> iterator() {
                return newPriceList.iterator();
            }
        };
    }

    private double[] getOriginalPrices(SequentialCache<OrderBook> cache) {
        List<Double> prices = new ArrayList<>();
        for (TimedEntry<OrderBook> it : cache) {
            prices.add(it.data().mid());
        }

        return DoubleUtils.collection2DoubleArray(prices);
    }

    private double[] bootstrapPrices(
        double price,
        Resampler resampling
    ) {
        double[] resample = resampling.newResample();
        int size = resample.length + 1;
        double[] pricesResample = new double[size];
        pricesResample[0] = price;
        for (int k = 1; k < size; ++k) {
            pricesResample[k] = pricesResample[k - 1] * Math.exp(resample[k - 1]);
        }

        return pricesResample;
    }

    private void savePerformanceReport(
        List<PerformanceReport> reports
    ) {
        File file = new File(OUTPUT_FOLDER, REPORT_FILE_NAME);
        LOGGER.info("saving reports to {}...", file.getAbsolutePath());
        PrintWriter out = null;
        try {
            out
                = new PrintWriter(
                    new BufferedWriter(
                        new FileWriter(file)
                    )
                );
            for (PerformanceReport report : reports) {
                out.println(report.toString());
            }
        } catch (IOException ex) {
            LOGGER.error("error when printing performance reports", ex);
        } finally {
            NMIO.close(out);
        }
        LOGGER.info("saving reports done.");
    }
}
