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
package tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300;

import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import dev.nm.nmutils.plot.api.Chart;
import dev.nm.solver.multivariate.constrained.general.penaltymethod.PenaltyMethodMinimizer;
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.execution.report.SimulationResultsExporter;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.NamedParamFactory;
import tech.nmfin.algoquant.model.portfoliooptimization.*;
import tech.nmfin.algoquant.model.portfoliooptimization.corvalan2005.Corvalan2005Diversifier;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.OptimizationAlgorithmComparisonSimulation;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.PortfolioOptimizationSimulation.Results;
import tech.nmfin.portfoliooptimization.Lai2010OptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.corvalan2005.Corvalan2005;
import tech.nmfin.portfoliooptimization.corvalan2005.diversification.ProductOfWeights;

/**
 *
 *
 * <p><b>Using this example.</b>
 * Configures CSI 300 constituents and local adjusted-price files for a portfolio simulation.
 * The constituent CSV and price directory are independent inputs and must cover compatible
 * dates. Set {@code -Dcsi300.indexCsv=/path/to/index.csv} to a genuine CSI300
 * history with header {@code date,close} and ISO dates. Optional properties
 * {@code csi300.constituents} and {@code csi300.prices} override local paths.
 * The default runs buy-and-hold and equal-weight baselines only; opt into the
 * expensive 990-optimizer sweep with {@code -Dcsi300.fullGrid=true}.
 * No network data is downloaded. Reported risk-free rates are assumed zero.
 * Set {@code csi300.showCharts=true} to open chart windows; PNG export is automatic.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Related theory and the scope of this example are distinguished below.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Reuses rolling portfolio optimization with configured CSI 300 constituents and adjusted
 * prices. A constituent list needs time-consistent membership, and a price history needs
 * coverage for calibration as well as trading. These dataset choices are separate from the
 * underlying optimization theory.
 * </p>
 * @author Ken Yiu
 * @see <a href="https://doi.org/10.1111/j.1540-6261.1952.tb01525.x">H. Markowitz (1952), Portfolio Selection, Journal of Finance 7(1), 77–91</a>
 */
public class CSI300SimulationDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) throws IOException {
        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2010, 1, 1, 0, 0),
                LocalDateTime.of(2017, 1, 1, 0, 0)
            );

        int nResults = new CSI300SimulationDemo(interval).run();
        LOGGER.info("{} simulations done", nResults);
    }

    private final LocalDateTimeInterval interval;
    private final LocalCSI300DataProvider dataProvider;

    public CSI300SimulationDemo(LocalDateTimeInterval interval) throws IOException {
        this.interval = interval;
        this.dataProvider = new LocalCSI300DataProvider(
            System.getProperty("csi300.constituents", "./test-data/china/constituent_index_300.csv"),
            System.getProperty("csi300.prices", "./test-data/china/20070104-20171027/adjust_prices"),
            Paths.get(System.getProperty("csi300.indexCsv", "./test-data/china/csi300-index.csv")),
            interval
        );
    }

    public int run() throws IOException {
        LOGGER.info("CSI300 tutorial: local data only; full research grid enabled={}",
            Boolean.getBoolean("csi300.fullGrid"));
        Map<NamedParam, Results> results = new LinkedHashMap<>();

        results.putAll(simulateBaseline(interval));
        if (Boolean.getBoolean("csi300.fullGrid")) {
            LOGGER.info("Starting full research grid: 990 optimizer simulations");
            results.putAll(simulateAlgorithms(interval));
        }
        LOGGER.info(String.format("simulating %d sets of parameters/strategies", results.size()));
        saveResults(results);

        LinkedHashMap<String, SequentialCache<Double>> profitCurves
            = extractProfitCurves(results);

        Collection<SequentialCache<Double>> set = profitCurves.values();
        for (SequentialCache<Double> cache : set) {
            LOGGER.info(print(cache, true));
        }

        plotCurves(profitCurves);

        return results.size();
    }

    private Map<NamedParam, Results> simulateBaseline(LocalDateTimeInterval interval) {
        Map<NamedParam, Results> results = new LinkedHashMap<>();
        NamedParam param
            = new NamedParam()
                .set("rebalance", Period.between(interval.start().toLocalDate(), interval.end().toLocalDate())) // no rebalance
                .set("window", Period.ofWeeks(1)) // not used
                .set("riskAverse", 0.);

        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set("algorithm", "buy-and-hold"),
                new IndexAwareOptimizer(
                    (returns, r, s, i) -> new DenseVector(returns.nCols()), // put 0% capital in stocks
                    (a, b, c) -> 1. // put 100% capital in the index
                )
            )
        );

        results.putAll(simulateEqual(interval));

        return results;
    }

    private Map<NamedParam, Results> simulateEqual(LocalDateTimeInterval interval) {
        Collection<NamedParam> params
            = new NamedParamFactory()
                //            .forValues("rebalance", Period.weeks(1), Period.weeks(2), Period.months(1), Period.months(3), Period.months(6))
                .forValues("rebalance", Period.ofMonths(1))
                .set("window", Period.ofWeeks(1)) // not used
                .set("riskAverse", 0.)
                .params();

        Map<NamedParam, Results> results = new ConcurrentHashMap<>();

        params.stream().forEach(param
            -> results.putAll(simulateEqual(interval, param))
        );
        return results;
    }

    private Map<NamedParam, Results> simulateEqual(
        LocalDateTimeInterval interval,
        NamedParam param
    ) {
        return simulateAlgorithm(
            interval,
            new NamedParam(param).<String>set(
                "algorithm",
                String.format("equal(%s)", param.<Period>value("rebalance"))
            ),
            new IndexAwareOptimizer(
                new EqualWeightOptimizationAlgorithm()
            )
        );
    }

    private Map<NamedParam, Results> simulateAlgorithms(
        LocalDateTimeInterval interval
    ) {
        Collection<NamedParam> params
            = new NamedParamFactory()
                .forValues("rebalance", new Period[]{Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12)})
                .forValues("window", new Period[]{Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(12)})
                .forDoubleValues("riskAverse", new double[]{0.1, 1, 100, 500, 1000, 10000})
                .params();

        Map<NamedParam, Results> results = new ConcurrentHashMap<>();
        params.stream().forEach(param -> {
            results.putAll(simulateAlgorithms(interval, param));
        });

        return results;
    }

    private Map<NamedParam, Results> simulateAlgorithms(
        LocalDateTimeInterval interval,
        NamedParam param
    ) {
        String paramString
            = String.format(
                "(%s,%s,%s)",
                param.<Period>value("rebalance"),
                param.<Period>value("window"),
                param.<Double>value("riskAverse")
            );

        Map<NamedParam, Results> results = new LinkedHashMap<>();

        Corvalan2005 corvalan
            = new Corvalan2005(
                new PenaltyMethodMinimizer(),
                new ProductOfWeights(),
                0.2,
                0.2
            );

        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set("algorithm", "markowitz" + paramString),
                new IndexAwareOptimizer(
                    new MarkowitzOptimizationAlgorithm(
                        param.<Double>value("riskAverse")
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set("algorithm", "markowitz-corvalan" + paramString),
                new IndexAwareOptimizer(
                    new Corvalan2005Diversifier(
                        new MarkowitzOptimizationAlgorithm(
                            param.<Double>value("riskAverse")
                        ),
                        corvalan
                    )
                )
            )
        );

        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "aboveindexir-markowitz-corvalan" + paramString
                    ),
                new AboveIndexIRSelector(
                    new IndexAwareOptimizer(
                        new Corvalan2005Diversifier(
                            new MarkowitzOptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        )
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "betahedged-top100ir-markowitz-corvalan" + paramString
                    ),
                new IndexAwareOptimizer(
                    new IRBasedAssetSelector(
                        new TopIRSelector(100),
                        new Corvalan2005Diversifier(
                            new MarkowitzOptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        )
                    ),
                    new BetaHedging()
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "betahedged-aboveindexir-markowitz-corvalan" + paramString
                    ),
                new AboveIndexIRSelector(
                    new IndexAwareOptimizer(
                        new Corvalan2005Diversifier(
                            new MarkowitzOptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        ),
                        new BetaHedging()
                    )
                )
            )
        );

        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set("algorithm", "lai2010" + paramString),
                new IndexAwareOptimizer(
                    new Lai2010OptimizationAlgorithm(
                        param.<Double>value("riskAverse")
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set("algorithm", "lai2010-corvalan" + paramString),
                new IndexAwareOptimizer(
                    new Corvalan2005Diversifier(
                        new Lai2010OptimizationAlgorithm(
                            param.<Double>value("riskAverse")
                        ),
                        corvalan
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "top100ir-lai2010-corvalan" + paramString
                    ),
                new IndexAwareOptimizer(
                    new IRBasedAssetSelector(
                        new TopIRSelector(100),
                        new Corvalan2005Diversifier(
                            new Lai2010OptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        )
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "aboveindexir-lai2010-corvalan" + paramString
                    ),
                new AboveIndexIRSelector(
                    new IndexAwareOptimizer(
                        new Corvalan2005Diversifier(
                            new Lai2010OptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        )
                    )
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "betahedged-top100ir-lai2010-corvalan" + paramString
                    ),
                new IndexAwareOptimizer(
                    new IRBasedAssetSelector(
                        new TopIRSelector(100),
                        new Corvalan2005Diversifier(
                            new Lai2010OptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        )
                    ),
                    new BetaHedging()
                )
            )
        );
        results.putAll(
            simulateAlgorithm(
                interval,
                new NamedParam(param)
                    .set(
                        "algorithm",
                        "betahedged-aboveindexir-lai2010-corvalan" + paramString
                    ),
                new AboveIndexIRSelector(
                    new IndexAwareOptimizer(
                        new Corvalan2005Diversifier(
                            new Lai2010OptimizationAlgorithm(
                                param.<Double>value("riskAverse")
                            ),
                            corvalan
                        ),
                        new BetaHedging()
                    )
                )
            )
        );
        return results;
    }

    private Map<NamedParam, Results> simulateAlgorithm(
        LocalDateTimeInterval interval,
        NamedParam param,
        PortfolioOptimizationAlgorithm algorithm
    ) {
        try {
            OptimizationAlgorithmComparisonSimulation sim
                = new OptimizationAlgorithmComparisonSimulation(
                    dataProvider.allProducts(interval),
                    dataProvider,
                    dataProvider,
                    param.value("rebalance"),
                    param.value("window"),
                    param.value("riskAverse"),
                    false
                );
            // Use the actual CSI300 benchmark rather than the simulation's SP500/Yahoo defaults.
            sim.setBenchmarkLookupFactory("CSI300", ignored -> dataProvider::indexValueAt);
            // Explicit tutorial assumption: zero risk-free rate (not a downloaded US bill yield).
            sim.setRiskFreeRateLookup(time -> 0.0);
            String algoName = param.value("algorithm");
            sim.addAlgorithm(algoName, algorithm);
            LOGGER.info("simulating {} for interval {}...", algoName, interval);
            return Collections.singletonMap(
                param,
                sim.run(interval).get(algoName) // where it actually does the simulation
            );
        } catch (IOException ex) {
            throw new RuntimeException(
                "simulation of algorithm " + param.value("algorithm")
                + " failed for param " + param,
                ex
            );
        }
    }

    public static <T> String print(SequentialCache<T> cache, boolean isTimestamp) {
        StringBuilder sb = new StringBuilder();

        for (TimedEntry<T> entry : cache) {
            if (isTimestamp) {
                sb.append(String.format("%s: %s, ", entry.time().toString(), entry.data().toString()));
            } else {
                sb.append(String.format("%s, ", entry.data().toString()));
            }
        }

        return sb.toString();
    }

    private void plotCurves(
        LinkedHashMap<String, SequentialCache<Double>> curves
    ) throws IOException {
        String title = "Portfolio Optimization Algorithms on CSI300";
        Chart chart = ChartUtils.plotTimeSeries(
            title,
            new ArrayList<>(curves.keySet()),
            new ArrayList<>(curves.values()),
            true
        );
        if (Boolean.getBoolean("csi300.showCharts")) {
            chart.show();
        }
        File output = new File("./log/" + title + ".png");
        java.nio.file.Files.createDirectories(output.toPath().getParent());
        chart.saveAsPNG(output, 1200, 800);
    }

    private LinkedHashMap<String, SequentialCache<Double>> extractProfitCurves(
        Map<NamedParam, Results> results
    ) {
        return results.entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey().<String>value("algorithm"),
                    entry -> entry.getValue().profitHistory(),
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                )
            );
    }

    private void saveResults(
        Map<NamedParam, Results> results
    ) throws IOException {
        SimulationResultsExporter exporter = new SimulationResultsExporter();
        results.entrySet()
            .forEach(
                entry
                -> exporter.add(
                    entry.getKey(),
                    entry.getValue().report()
                )
            );
        exporter.writeAsCsvZip();
    }

}

