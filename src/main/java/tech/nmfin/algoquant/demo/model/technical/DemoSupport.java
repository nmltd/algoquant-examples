package tech.nmfin.algoquant.demo.model.technical;

import java.io.File;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.misc.license.License;
import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.data.historicaldata.yahoo.*;
import tech.nmfin.algoquant.execution.component.chart.plotter.*;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;

/**
 * Shared license setup, data selection, plotting and simulation checks for the demos.
 * Defaults are deterministic synthetic prices and no GUI. Synthetic prices demonstrate
 * execution mechanics and are not historical market observations or strategy evidence.
 * Configuration is process-wide, so each command-line demo initializes it once.
 */
public final class DemoSupport {
    private static boolean live;
    private static boolean charts;
    private static boolean quick;
    private DemoSupport() { }

    /**
     * Validates options and loads the external license before licensed objects are created.
     * {@code --live} selects Yahoo downloads, {@code --charts} enables desktop plots,
     * and {@code --quick} reduces the dynamic demo's workload.
     * Set {@code -Dnm.license=/path/nm.lic} to override {@code license/nm.lic}.
     *
     * @param args command-line options passed to the demo
     */
    public static void initialize(String[] args) {
        for (String arg : args) {
            if (!Arrays.asList("--live", "--charts", "--quick").contains(arg))
                throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        live = Arrays.asList(args).contains("--live");
        charts = Arrays.asList(args).contains("--charts");
        quick = Arrays.asList(args).contains("--quick");
        File file = new File(System.getProperty("nm.license", "license/nm.lic"));
        if (!file.isFile()) throw new IllegalArgumentException("Supply a valid license at " + file.getAbsolutePath()
            + " or use -Dnm.license=/absolute/path/nm.lic");
        License.setLicenseFile(file);
        System.out.println("Data: " + (live ? "live Yahoo download" : "deterministic SYNTHETIC prices")
            + "; charts: " + charts + "; quick: " + quick);
    }

    /** Whether the dynamic example should use its short interval and single parameter set. */
    public static boolean quick() { return quick; }

    /** Selects a desktop plotter only when requested, allowing headless CI by default. */
    public static StrategyPlotter plotter(String title) {
        return charts ? new SimpleStrategyPlotter(title) : new NoOpPlotter();
    }

    /** Adapts daily adjusted closes to simulated order books, or uses cached Yahoo history. */
    public static OrderBookCacheFactory<Stock> orderBooks() {
        if (live) return new YahooOrderBookCacheFactory<Stock>("./log/Yahoo");
        return (stock, interval) -> {
            BufferCache<OrderBook> books = new BufferCache<>();
            for (TimedEntry<StockEOD> entry : eod(stock, interval))
                books.add(entry.time(), new OrderBook(stock, entry.data().adjClose()));
            return books;
        };
    }

    /**
     * Returns Yahoo history in live mode, or generated weekday OHLCV bars otherwise.
     * Generated bars use exchange closing times in [start, end); exchange holidays
     * are not modeled. Every stock uses the same synthetic formula.
     */
    public static SequentialCache<StockEOD> eod(Stock stock, LocalDateTimeInterval interval) throws Exception {
        if (live) return new YahooEODCacheFactory("./log/Yahoo").newInstance(stock, interval);
        BufferCache<StockEOD> data = new BufferCache<>();
        for (LocalDate date = interval.start().toLocalDate(); !date.isAfter(interval.end().toLocalDate()); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            LocalDateTime time = stock.exchange().closingTimeOnDate(date);
            if (time.isBefore(interval.start()) || !time.isBefore(interval.end())) continue;
            // Anchor values to calendar dates so overlapping calibration windows agree.
            long day = ChronoUnit.DAYS.between(LocalDate.of(2009, 1, 1), date);
            // A mild trend plus oscillations exercises crossover logic without network access.
            double close = 100 + 0.02 * day + 8 * Math.sin(day / 11.0) + 3 * Math.cos(day / 29.0);
            double high = close + 1.5 + 0.4 * Math.sin(day / 7.0);
            double low = close - 1.5 + 0.4 * Math.cos(day / 13.0);
            data.add(time, new StockEOD(close - 0.2, high, low, close, 1000000 + 1000 * (day % 200), close));
        }
        return data;
    }

    /** Checks nonempty, finite NAV output; prints execution count without requiring a trade. */
    public static void verify(PerformanceReport report) {
        int observations = 0;
        for (TimedEntry<Double> entry : report.navCache()) {
            if (!Double.isFinite(entry.data())) throw new AssertionError("Non-finite simulated NAV");
            observations++;
        }
        if (observations == 0) throw new AssertionError("Simulation produced no NAV observations");
        System.out.println("Simulation checked: " + observations + " NAV observations; "
            + report.tradeBlotter().allExecutions().size() + " executions.");
    }
}
