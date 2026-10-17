package tech.nmfin.algoquant.web;

import static tech.nmfin.algoquant.execution.datatype.order.OrderUtils.newMarketOrder;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;

import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODWebDownloader;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.*;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.strategy.Strategy;

import java.time.*;
import java.util.*;

/** Reusable, independently funded S&P 500 price-index buy-and-hold simulation. */
public final class BuyAndHoldBenchmark {
    public static final String SYMBOL = "^GSPC";

    @FunctionalInterface
    public interface PriceSource {
        List<TimedEntry<StockEOD>> load(Stock stock, LocalDate start, LocalDate end);
    }

    private final PriceSource source;

    public BuyAndHoldBenchmark() {
        this(
                (stock, start, end) -> {
                    List<TimedEntry<StockEOD>> bars = new ArrayList<>();
                    for (TimedEntry<StockEOD> bar :
                            new YahooEODWebDownloader()
                                    .newInstance(
                                            stock,
                                            LocalDateTimeInterval.of(
                                                    start.atStartOfDay(), end.atTime(23, 59))))
                        bars.add(bar);
                    return bars;
                });
    }

    /**
     * Inject cached prices or explicit offline fixtures; no market-data fallback is substituted.
     */
    public BuyAndHoldBenchmark(PriceSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public static final class Point {
        public String date;
        public double close, nav;
    }

    public static final class Fill {
        public String date, side;
        public double quantity, price;
    }

    public static final class Statistics extends PerformanceStatistics.Values {}

    public static final class Result {
        public String symbol = SYMBOL, start, end, source;
        public double initialCapital;
        public Statistics statistics;
        public List<Point> series = new ArrayList<>();
        public List<Fill> executions = new ArrayList<>();
        public List<AllPerformanceMeasures.Metric> performanceMeasures;
        public transient BenchmarkLookup lookup;
    }

    public Result run(LocalDate start, LocalDate end, double capital) {
        if (start == null
                || end == null
                || end.isBefore(start)
                || !Double.isFinite(capital)
                || capital <= 0)
            throw new IllegalArgumentException("Invalid benchmark period or initial capital.");
        Stock index = USDStock.newInstance(SYMBOL, Exchange.NEW_YORK);
        NavigableMap<LocalDateTime, Double> prices = new TreeMap<>();
        for (TimedEntry<StockEOD> bar : source.load(index, start.minusYears(1), end)) {
            if (bar.time().toLocalDate().isAfter(end)) continue;
            double close = bar.data().adjClose();
            if (!Double.isFinite(close) || close <= 0)
                throw new IllegalStateException("Invalid S&P 500 index data.");
            if (prices.put(bar.time(), close) != null)
                throw new IllegalStateException("Duplicate S&P 500 observation.");
        }
        BufferCache<OrderBook> books = new BufferCache<>();
        NavigableMap<LocalDateTime, Double> selected = new TreeMap<>();
        prices.forEach(
                (time, close) -> {
                    if (!time.toLocalDate().isBefore(start)) {
                        selected.put(time, close);
                        books.add(time, new OrderBook(index, close));
                    }
                });
        if (selected.size() < 2)
            throw new IllegalArgumentException("Insufficient S&P 500 history for this backtest.");
        LocalDateTimeInterval interval =
                LocalDateTimeInterval.of(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Strategy strategy =
                new Strategy() {
                    private boolean entered;

                    @Override
                    public void onEvent(TraderContext context, Event event) {
                        if (!entered && event instanceof OrderBook) {
                            context.broker()
                                    .sendOrder(
                                            newMarketOrder(
                                                    index, capital / ((OrderBook) event).mid()));
                            entered = true;
                        }
                    }
                };
        PerformanceReport simulated =
                new SimpleSimulatorBuilder()
                        .withInitialCash(capital)
                        .withOrderBookUpdates(new OrderBookCaches(index, books))
                        .build()
                        .run(strategy, interval);
        Map<LocalDate, Double> navs = new TreeMap<>();
        for (TimedEntry<Double> nav : simulated.navCache())
            navs.put(nav.time().toLocalDate(), nav.data());
        Result result = new Result();
        result.start = start.toString();
        result.end = end.toString();
        result.initialCapital = capital;
        result.source =
                "Yahoo Finance ^GSPC S&P 500 price index; hypothetical fractional index units; no"
                    + " reinvested dividends or costs.";
        result.lookup =
                time -> {
                    Map.Entry<LocalDateTime, Double> value = prices.floorEntry(time);
                    return value == null ? prices.firstEntry().getValue() : value.getValue();
                };
        BufferCache<Double> measureNav = new BufferCache<>();
        measureNav.add(interval.start(), capital);
        List<String> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        double previous = capital;
        for (Map.Entry<LocalDateTime, Double> quote : selected.entrySet()) {
            Double nav = navs.get(quote.getKey().toLocalDate());
            if (nav == null || !Double.isFinite(nav))
                throw new IllegalStateException("Missing benchmark NAV.");
            Point point = new Point();
            point.date = quote.getKey().toLocalDate().toString();
            point.close = quote.getValue();
            point.nav = nav;
            result.series.add(point);
            measureNav.add(quote.getKey(), nav);
            dates.add(point.date);
            values.add(nav);
            previous = nav;
        }
        List<Double> returns = PerformanceStatistics.dailyReturns(values, capital);
        result.statistics = PerformanceStatistics.compute(dates, values, capital, new Statistics());
        measureNav.add(interval.end(), previous);
        for (Execution execution : simulated.tradeBlotter().allExecutions()) {
            Fill fill = new Fill();
            fill.date = execution.time().toString();
            fill.side = execution.side().toString();
            fill.quantity = execution.quantity();
            fill.price = execution.price();
            result.executions.add(fill);
        }
        AllPerformanceMeasures measures =
                new AllPerformanceMeasures(
                        interval,
                        capital,
                        measureNav,
                        simulated.tradeBlotter().allExecutions(),
                        new OrderBookCaches(index, books),
                        result.lookup,
                        null,
                        returns.stream().mapToDouble(Double::doubleValue).toArray());
        measures.compute(false);
        markOpenPositionMeasures(measures.metrics);
        result.performanceMeasures = measures.metrics;
        return result;
    }

    public static void markOpenPositionMeasures(List<AllPerformanceMeasures.Metric> metrics) {
        for (AllPerformanceMeasures.Metric metric : metrics)
            if (!"ok".equals(metric.status)
                    && (metric.group.equals("Per-trade distribution statistics")
                            || metric.group.equals("Trade return ratios")
                            || metric.sourceClass.startsWith("WinLoss"))) {
                metric.status = "not_applicable";
                metric.display = "N/A";
                metric.value = null;
                metric.detail =
                        "Buy-and-hold has one open position and no completed round-trip trades. "
                                + metric.detail;
            }
    }
}
