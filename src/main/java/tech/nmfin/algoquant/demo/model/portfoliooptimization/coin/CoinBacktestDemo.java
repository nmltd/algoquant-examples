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
package tech.nmfin.algoquant.demo.model.portfoliooptimization.coin;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.Map;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.BufferCache;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.coin.CoinDataProvider;
import tech.nmfin.algoquant.data.historicaldata.coin.filter.MktCapInBTCFilter;
import tech.nmfin.algoquant.data.historicaldata.coin.jessevent.JesseventCoinDataUtils;
import tech.nmfin.algoquant.execution.datatype.EODMktCap;
import tech.nmfin.algoquant.execution.datatype.product.coin.CoinProduct;
import tech.nmfin.algoquant.execution.report.SimulationResultsExporter;
import tech.nmfin.algoquant.execution.report.WeightsHistoriesExporter;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.NamedParamFactory;
import tech.nmfin.algoquant.model.portfoliooptimization.*;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.OptimizationAlgorithmComparisonSimulation;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.PortfolioOptimizationSimulation;

/**
 * Compares the different asset allocation algorithms.
 *
 *
 * <p><b>Using this example.</b>
 * Loads the local crypto-markets CSV ZIP through JesseventCoinDataUtils and compares
 * allocation algorithms. The dataset path is test-data/coin/crypto-markets.csv.zip; its
 * historical coverage and constituent selection constrain the comparison.
 * </p>
 * @author Ken Yiu
 */
public class CoinBacktestDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) throws IOException {
        Collection<NamedParam> params = new NamedParamFactory()
            .forValues("rebalance", new Period[]{Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12)})
            .forValues("window", new Period[]{Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(12)})
            .forDoubleValues("riskAverse", new double[]{500, 1000, 10000})
            .forDoubleValues("shrinkage", new double[]{0.0, 0.5, 1.0})
            .params();

        LocalDateTimeInterval interval
            = LocalDateTimeInterval.of(
                LocalDateTime.of(2015, 1, 1, 0, 0),
                LocalDateTime.of(2018, 1, 31, 0, 0)
            );

        /*
         * Data provider for coins.
         */
        JesseventCoinDataUtils utils
            = new JesseventCoinDataUtils("./test-data/coin/crypto-markets.csv.zip");
        Map<CoinProduct, SequentialCache<EODMktCap>> coinEODs
            = utils.allCoinEODs();
        BufferCache<EODMktCap> btcEODs
            = BufferCache.fromSequentialCache(
                utils.getCacheBySymbol("BTC/USD")
            );
        CoinDataProvider dataProvider
            = new CoinDataProvider(
                coinEODs,
                //                new ShortHistoryFilter(interval.getStart()),
                //                new AverageTurnoverFilter(1e6),
                //                new GoodSymbolFilter(
                //                    "BTC/USD",
                //                    "ETH/USD",
                //                    "XRP/USD",
                //                    "BCH/USD",
                //                    "LTC/USD",
                //                    "ADA/USD",
                //                    "NEO/USD",
                //                    "XLM/USD",
                //                    "EOS/USD",
                //                    "MIOTA/USD" // the 10th
                //                    "DASH/USD",
                //                    "XMR/USD",
                //                    "ETC/USD",
                //                    "XEM/USD",
                //                    "VEN/USD",
                //                    "TRX/USD",
                //                    "USDT/USD",
                //                    "LSK/USD",
                //                    "BTG/USD",
                //                    "QTUM/USD", // the 20th
                //                    "OMG/USD",
                //                    "NANO/USD",
                //                    "ICX/USD",
                //                    "ZEC/USD",
                //                    "BNB/USD",
                //                    "STEEM/USD",
                //                    "XVG/USD",
                //                    "BCN/USD",
                //                    "PPT/USD",
                //                    "STRAT/USD" // the 30th
                //                )
                //                new MktCapFilter(1e9), // mktcap in USD
                new MktCapInBTCFilter(1e6, t -> btcEODs.get(t).data().close()) // mktcap in BTC
            );
        SimulationResultsExporter exporter = new SimulationResultsExporter();
        WeightsHistoriesExporter weightsExporter = new WeightsHistoriesExporter();
        params.forEach(param -> {
            try {
                OptimizationAlgorithmComparisonSimulation sim
                    = new OptimizationAlgorithmComparisonSimulation(
                        dataProvider.allProducts(interval),
                        dataProvider,
                        dataProvider,
                        param.value("rebalance"),
                        param.value("window"),
                        param.value("riskAverse"),
                        String.format(
                            "Portfolio Optimization Algorithms (%s,%s,%.2f,%.2f)",
                            param.value("rebalance"),
                            param.value("window"),
                            param.value("riskAverse"),
                            param.value("shrinkage")
                        )
                    );

                sim.addAlgorithm(
                    "bitcoin-only",
                    new SingleProductAlgorithm("BTC/USD")
                );
                sim.addAlgorithm(
                    "equal",
                    new EqualWeightOptimizationAlgorithm()
                );
//                sim.addAlgorithm(
//                    "plugin-markowitz",
//                    new PluginMarkowitzAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "equal-mean-shrank-cov",
//                    new EqualMeanAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "sample-mean-shrank-cov/markowitz",
//                    new MarkowitzOptimizationAlgorithm(param.value("riskAverse"))
//                );
//                GARCHResamplerFactory2 f = new GARCHResamplerFactory2();
//                sim.addAlgorithm("NMSAAM",
//                    new Lai2010OptimizationAlgorithm(
//                        new MVOptimizerShrankMean(new MVOptimizerLongOnly(), param.value("shrinkage")),
//                        //                            new MomentsEstimatorLedoitWolf(),
//                        //                            new GroupResamplerFactory(),
//                        new SimpleGARCHMoments2(f),
//                        f,
//                        50,
//                        param.value("riskAverse"))
//                );
//                sim.addAlgorithm("partial-NMSAAM",
//                    new TopNOptimizationAlgorithm(
//                        new Lai2010OptimizationAlgorithm(
//                            new MVOptimizerShrankMean(new MVOptimizerLongOnly(), param.value("shrinkage")),
//                            //                            new MomentsEstimatorLedoitWolf(),
//                            //                            new GroupResamplerFactory(),
//                            new SimpleGARCHMoments2(f),
//                            f,
//                            50,
//                            param.value("riskAverse")),
//                        10, // max number of assets
//                        1.0 // weights explained
//                    ));
//                sim.addAlgorithm(
//                    "sample-mean-equal-cor",
//                    new EqualCorAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "long-short-mean",
//                    new LongShortMeanAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "markowitz-corvalan",
//                    new Corvalan2005Diversifier(
//                        new MarkowitzOptimizationAlgorithm(param.value("riskAverse")),
//                        param.value("riskAverse")
//                    )
//                );
//                sim.addAlgorithm(
//                    "socp",
//                    new SOCPOptimizationAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "socp-corvalan",
//                    new Corvalan2005Diversifier(
//                        new SOCPOptimizationAlgorithm(param.value("riskAverse")),
//                        param.value("riskAverse")
//                    )
//                );
//                sim.addAlgorithm(
//                    "lai2010",
//                    new Lai2010OptimizationAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                    "lai2010-corvalan",
//                    new Corvalan2005Diversifier(
//                        new Lai2010OptimizationAlgorithm(param.value("riskAverse")),
//                        param.value("riskAverse")
//                    )
//                );
                Map<String, PortfolioOptimizationSimulation.Results> results
                    = sim.run(interval);
                results.forEach((algoName, result) -> {
                    NamedParam paramWithAlgoName
                        = new NamedParam(param).set("algorithm", algoName);
                    exporter.add(
                        paramWithAlgoName,
                        result.report()
                    );
                    weightsExporter.addHistory(
                        paramWithAlgoName,
                        result.actualWeightsHistory()
                    );
                });

            } catch (IOException ex) {
                LOGGER.error("simulation failed", ex);
            }
        });

        exporter.writeAsCsv();
        weightsExporter.writeAsCsvZip();
    }

}
