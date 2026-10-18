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
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.CsvzipCacheFactory;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.calendar.CalendarUtils;
import tech.nmfin.algoquant.data.calendar.HolidayCalendar;
import tech.nmfin.algoquant.data.calendar.HolidayCalendarFromYahoo;
import tech.nmfin.algoquant.data.historicaldata.china.ChinaIndexConstituentsHistoryReader;
import tech.nmfin.algoquant.data.historicaldata.china.SuspensionDataReader;
import tech.nmfin.algoquant.data.historicaldata.compustat.CompustatEODCacheFactory;
import tech.nmfin.algoquant.data.historicaldata.compustat.CompustatIndexConstituentsHistory;
import static tech.nmfin.algoquant.data.historicaldata.compustat.CompustatIndexConstituentsHistory.Index.SP500;
import tech.nmfin.algoquant.data.historicaldata.compustat.CompustatStock;
import tech.nmfin.algoquant.execution.component.TradabilityLookup;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.report.ProfitHistoriesExporter;
import tech.nmfin.algoquant.execution.report.SimulationResultsExporter;
import tech.nmfin.algoquant.execution.report.WeightsHistoriesExporter;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.NamedParamFactory;
import tech.nmfin.algoquant.model.portfoliooptimization.*;
import tech.nmfin.algoquant.model.portfoliooptimization.corvalan2005.Corvalan2005Diversifier;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.OptimizationAlgorithmComparisonSimulation;
import tech.nmfin.algoquant.model.portfoliooptimization.simulation.PortfolioOptimizationSimulation;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.*;
import tech.nmfin.algoquant.model.strategy.rebalance.CsvCacheProvider;
import tech.nmfin.algoquant.model.strategy.rebalance.DynamicCacheProvider;
import tech.nmfin.algoquant.model.strategy.rebalance.IndexCacheProvider;
import tech.nmfin.portfoliooptimization.Lai2010OptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.TopNOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.GARCHResamplerFactory2;
import tech.nmfin.portfoliooptimization.lai2010.fit.SimpleGARCHMoments2;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerLongOnly;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerShrankMean;

/**
 * Compares the different asset allocation algorithms. Prices are from
 * historical data.
 *
 * @author Ken Yiu
 * @param <P> a subtype of {@linkplain Product}
 */
public class PortfolioOptimComparisons2Demo<P extends Product> {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    private final Collection<NamedParam> params;
    private final DynamicCacheProvider<P> dataProvider;
    private final TradabilityLookup tradabilityLookup;

    public PortfolioOptimComparisons2Demo(
        Collection<NamedParam> params,
        DynamicCacheProvider<P> dataProvider
    ) {
        this(params, dataProvider, null);
    }

    public PortfolioOptimComparisons2Demo(
        Collection<NamedParam> params,
        DynamicCacheProvider<P> dataProvider,
        TradabilityLookup tradabilityLookup
    ) {
        this.params = params;
        this.dataProvider = dataProvider;
        this.tradabilityLookup = tradabilityLookup;
    }

    public void run() throws IOException {
        LocalDateTimeInterval interval0 = dataProvider.dataInterval();
        final LocalDateTimeInterval interval = interval0 != null ? interval0
            // change this parameter to adjust the sim interval
            : LocalDateTimeInterval.of(
                LocalDateTime.of(2015, 1, 1, 0, 0),
                LocalDateTime.of(2018, 1, 31, 0, 0)
            );

        SimulationResultsExporter simExporter = new SimulationResultsExporter();
        WeightsHistoriesExporter computedWeightsExporter = new WeightsHistoriesExporter("computed.weights");
        WeightsHistoriesExporter actualWeightsExporter = new WeightsHistoriesExporter("actual.weights");
        ProfitHistoriesExporter profitHistoriesExporter = new ProfitHistoriesExporter();
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
                sim.setTradabilityLookup(tradabilityLookup);

                //<editor-fold defaultstate="collapsed" desc="add the optim algorithms">
                sim.addAlgorithm(
                    "equal", // all time benchmark
                    new EqualWeightOptimizationAlgorithm()
                );
                sim.addAlgorithm(
                    "plugin-markowitz", // year 1952 Markowitz
                    new PluginMarkowitzAlgorithm(param.value("riskAverse"))
                );
                sim.addAlgorithm(
                    "sample-mean-equal-cor", // per historical performance
                    new EqualCorAlgorithm(param.value("riskAverse"))
                );
                sim.addAlgorithm(
                    "equal-mean-shrank-cov", // min. var. portfolio
                    new EqualMeanAlgorithm(param.value("riskAverse"))
                );
                sim.addAlgorithm(
                    "sample-mean-shrank-cov/markowitz", // Markowitz + linear cov. shrinkage
                    new MarkowitzOptimizationAlgorithm(param.value("riskAverse"))
                );
                sim.addAlgorithm(
                    "markowitz-corvalan", // Markowitz + diversification
                    new Corvalan2005Diversifier(
                        new MarkowitzOptimizationAlgorithm(param.value("riskAverse"))
                    )
                );
//                sim.addAlgorithm(
//                        "socp", // SOCP, same as Markowitz without non-linear constraints
//                        new SOCPOptimizationAlgorithm(param.value("riskAverse"))
//                );
//                sim.addAlgorithm(
//                        "socp-corvalan", // SOCP + diversification
//                        new Corvalan2005Diversifier(
//                                new SOCPOptimizationAlgorithm(param.value("riskAverse")),
//                                param.value("riskAverse")
//                        )
//                );
                sim.addAlgorithm(
                    "lai2010", // Lai basic
                    new Lai2010OptimizationAlgorithm(param.value("riskAverse"))
                );
                GARCHResamplerFactory2 f = new GARCHResamplerFactory2();
                sim.addAlgorithm("NMSAAM", // Lai fully customizable
                    new Lai2010OptimizationAlgorithm(
                        new MVOptimizerShrankMean(new MVOptimizerLongOnly(), param.value("shrinkage")),
                        //                            new MomentsEstimatorLedoitWolf(),
                        //                            new GroupResamplerFactory(),
                        new SimpleGARCHMoments2(f),
                        f,
                        50,
                        param.value("riskAverse"))
                );
                sim.addAlgorithm("partial-NMSAAM", // Lai truncated
                    new TopNOptimizationAlgorithm(
                        new Lai2010OptimizationAlgorithm(
                            new MVOptimizerShrankMean(new MVOptimizerLongOnly(), param.value("shrinkage")),
                            //                            new MomentsEstimatorLedoitWolf(),
                            //                            new GroupResamplerFactory(),
                            new SimpleGARCHMoments2(f),
                            f,
                            50,
                            param.value("riskAverse")),
                        10, // max number of assets
                        1.0 // weights explained
                    ));
                sim.addAlgorithm(
                    "lai2010-corvalan", // Lai + diversification
                    new Corvalan2005Diversifier(
                        new Lai2010OptimizationAlgorithm(param.value("riskAverse"))
                    )
                );
                //</editor-fold>

                Map<String, PortfolioOptimizationSimulation.Results> results
                    = sim.run(interval);
                results.forEach((algoName, result) -> {
                    NamedParam paramWithAlgoName
                        = new NamedParam(param).set("algorithm", algoName);
                    simExporter.add(
                        paramWithAlgoName,
                        result.report()
                    );
                    computedWeightsExporter.addHistory(
                        paramWithAlgoName,
                        result.computedWeightsHistory()
                    );
                    actualWeightsExporter.addHistory(
                        paramWithAlgoName,
                        result.actualWeightsHistory()
                    );
                    profitHistoriesExporter.addHistory(
                        paramWithAlgoName,
                        result.profitHistory()
                    );
                });
            } catch (IOException ex) {
                LOGGER.error("simulation failed", ex);
            }
        });

        simExporter.writeAsCsv();
        computedWeightsExporter.writeAsCsv();
        actualWeightsExporter.writeAsCsv();
        profitHistoriesExporter.writeAsCsv();
    }

    //<editor-fold defaultstate="collapsed" desc="csv demo">
    private static void demo_csv_0000(Collection<NamedParam> params) throws IOException {
        /**
         * Data provider for CSV data files.
         */
        CsvCacheProvider dataProvider
            = new CsvCacheProvider(
                "./test-data/portfoliooptim/sample_assets.csv",
                Currencies.CNY
            );

        PortfolioOptimComparisons2Demo demo
            = new PortfolioOptimComparisons2Demo(params, dataProvider);

        demo.run();
    }

    private static void demo_csv_0010() throws IOException {
        Collection<NamedParam> params = new NamedParamFactory()
            .forValues("rebalance", new Period[]{Period.ofMonths(6)})
            .forValues("window", new Period[]{Period.ofMonths(6)})
            .forDoubleValues("riskAverse", new double[]{10000})
            .forDoubleValues("shrinkage", new double[]{0.5})
            .params();
        demo_csv_0000(params);
    }

    private static void demo_csv_0020() throws IOException {
        Collection<NamedParam> params = new NamedParamFactory()
            .forValues("rebalance", new Period[]{Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12)})
            .forValues("window", new Period[]{Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(12)})
            .forDoubleValues("riskAverse", new double[]{500, 1000, 10000})
            .forDoubleValues("shrinkage", new double[]{0.0, 0.5, 1.0})
            .params();
        demo_csv_0000(params);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="China index demo">
    private static void demo_SZ50_0010() throws IOException {
        Collection<NamedParam> params = new NamedParamFactory()
            .forValues("rebalance", new Period[]{Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12)})
            .forValues("window", new Period[]{Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(12)})
            .forDoubleValues("riskAverse", new double[]{500, 1000, 10000})
            .forDoubleValues("shrinkage", new double[]{0.0, 0.5, 1.0})
            .params();

        /**
         * Data provider for China SZ50 index.
         */
        ChinaIndexDataProvider dataProvider
            = new ChinaIndexDataProvider(
                "./test-data/china/constituents_sz50.csv",
                "./test-data/china/20070104-20171027/adjust_prices"
            );

        Map<String, SortedSet<LocalDate>> suspensionDates
            = new SuspensionDataReader().read("./test-data/china/suspend_data.zip");
        HolidayCalendar calendar = HolidayCalendarFromYahoo.forExchange(Exchange.SS);
        TradabilityLookup lookup = (t, p) -> {
            LocalDate lastBusinessDay
                = CalendarUtils.lastBusinessDay(
                    calendar,
                    t.toLocalDate(),
                    1
                );
                
            boolean isTradable
                = !suspensionDates.containsKey(p.symbol())
                || !suspensionDates.get(p.symbol()).contains(lastBusinessDay);
            LOGGER.trace("checking tradability for {} on date {}: {}", p, lastBusinessDay, isTradable);
            return isTradable;
        };

        PortfolioOptimComparisons2Demo demo
            = new PortfolioOptimComparisons2Demo(params, dataProvider, lookup);

        demo.run();
    }

    private static class ChinaIndexDataProvider extends IndexCacheProvider<Stock> {

        public ChinaIndexDataProvider(
            String indexDataFileName,
            String stockDailyPriceDataFolder
        ) throws IOException {
            super(
                new ChinaIndexConstituentsHistoryReader(indexDataFileName),
                new CsvzipCacheFactory<Stock, StockEOD>(
                    stockDailyPriceDataFolder,
                    new CsvzipCacheFactory.RowParser<Stock, StockEOD>() {
                    final DateTimeFormatter DATE_FORMATTER
                        = DateTimeFormatter.BASIC_ISO_DATE;

                    @Override
                    public TimedEntry<StockEOD> parse(
                        Stock stock,
                        Map<String, Object> rowMapping
                    ) {
                        LocalDateTime time
                            = stock.exchange().closingTimeOnDate(
                                LocalDate.parse(
                                    (String) rowMapping.get("date"),
                                    DATE_FORMATTER
                                )
                            );
                        return new TimedEntry<>(
                            time,
                            new StockEOD(
                                parseDouble(rowMapping.get("adj_open")),
                                parseDouble(rowMapping.get("adj_high")),
                                parseDouble(rowMapping.get("adj_low")),
                                parseDouble(rowMapping.get("adj_close")),
                                0., // no such info
                                parseDouble(rowMapping.get("adj_close"))
                            )
                        );
                    }

                    private double parseDouble(Object value) {
                        return value != null
                            ? Double.parseDouble((String) value)
                            : 0.;
                    }
                })
            );
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="USA index demo">
    private static class CompustatIndexDataProvider extends IndexCacheProvider<CompustatStock> {

        CompustatIndexDataProvider(
            CompustatIndexConstituentsHistory indexHistory,
            String stockDailyPriceDataFolder
        ) {
            super(
                indexHistory,
                new CompustatEODCacheFactory(stockDailyPriceDataFolder)
            );
        }
    }

    private static void demo_SP500_0010() throws IOException {
        Collection<NamedParam> params = new NamedParamFactory()
            .forValues("rebalance", new Period[]{Period.ofMonths(1), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12)})
            .forValues("window", new Period[]{Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(12)})
            .forDoubleValues("riskAverse", new double[]{500, 1000, 10000})
            .forDoubleValues("shrinkage", new double[]{0.0, 0.5, 1.0})
            .params();

        /**
         * Data provider for S&P 500 index.
         */
        CompustatIndexDataProvider dataProvider
            = new CompustatIndexDataProvider(
                new CompustatIndexConstituentsHistory(
                    "C:\\NM\\svn\\wip\\roboadvisor\\branch\\bocdemo20171218\\test-data\\compustat_index_constituents.190001-201705.csv.zip",
                    SP500,
                    id -> Exchange.NEW_YORK
                ),
                "C:\\NM\\svn\\wip\\roboadvisor\\branch\\bocdemo20171218\\test-data\\by_gvkey"
            );

        PortfolioOptimComparisons2Demo demo
            = new PortfolioOptimComparisons2Demo(params, dataProvider);

        demo.run();
    }
    //</editor-fold>

    public static void main(String[] args) throws IOException {
        // choose one to run or it will take too long
        demo_csv_0010();
//        demo_csv_0020();

//        demo_SZ50_0010();
//        demo_SP500_0010();
    }

}
