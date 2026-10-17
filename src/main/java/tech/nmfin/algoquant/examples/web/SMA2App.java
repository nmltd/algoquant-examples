package tech.nmfin.algoquant.examples.web;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODWebDownloader;
import tech.nmfin.algoquant.execution.component.simulator.*;
import tech.nmfin.algoquant.execution.component.context.TraderContext;
import tech.nmfin.algoquant.execution.component.simulator.event.Event;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
import tech.nmfin.algoquant.execution.strategy.Strategy;
import tech.nmfin.algoquant.model.signal.technical.crossover.sma2.SMA2;

/** Runs the published AlgoQuant strategy, keeping mutable simulation state per request. */
public final class SMA2App {
    public static final double CAPITAL = 10000;
    public static final List<String> SYMBOLS = Collections.unmodifiableList(Arrays.asList(
        "AAPL", "MSFT", "AMZN", "GOOGL", "META", "NVDA", "TSLA", "AMD", "INTC", "NFLX",
        "JPM", "BAC", "V", "MA", "WMT", "KO", "PEP", "DIS", "XOM", "CVX"));
    private final boolean synthetic;
    private final Map<String, List<TimedEntry<StockEOD>>> cache = new LinkedHashMap<String, List<TimedEntry<StockEOD>>>(32, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, List<TimedEntry<StockEOD>>> e) { return size() > 32; }
    };

    /** Synthetic mode is only enabled by explicit server configuration for offline verification. */
    public SMA2App(boolean synthetic) { this.synthetic = synthetic; }

    public static final class Request {
        public final String symbol;
        public final LocalDate start, end;
        public final int fast, slow;
        public Request(String symbol, String start, String end, int fast, int slow) {
            this.symbol = symbol.toUpperCase(Locale.ROOT);
            if (!SYMBOLS.contains(this.symbol)) throw new IllegalArgumentException("Choose a supported US stock.");
            try { this.start = LocalDate.parse(start); this.end = LocalDate.parse(end); }
            catch (DateTimeException e) { throw new IllegalArgumentException("Enter valid start and end dates."); }
            if (this.start.isBefore(LocalDate.of(2000, 1, 1)) || this.end.isBefore(this.start)
                || ChronoUnit.DAYS.between(this.start, this.end) > 3660)
                throw new IllegalArgumentException("Choose a period of at most ten years, beginning in 2000 or later.");
            if (!this.end.isBefore(LocalDate.now(ZoneId.of("America/New_York"))))
                throw new IllegalArgumentException("End date must be before today; incomplete daily bars are excluded.");
            if (fast < 1 || fast >= slow || slow > 250)
                throw new IllegalArgumentException("Require 1 <= fast SMA < slow SMA <= 250 trading observations.");
            this.fast = fast; this.slow = slow;
        }
    }

    public static final class Point {
        public String date; public double close, nav, benchmark; public Double fast, slow;
    }
    public static final class Fill {
        public String date, side; public double quantity, price;
    }
    public static final class Result {
        public String symbol, start, end, source, generatedAt;
        public String reportId, reportPath, appRevision;
        public int reportSchemaVersion = 1;
        public int fast, slow; public double initialCapital = CAPITAL, finalNav, pnl, totalReturn, maxDrawdown;
        public Double annualizedVolatility, sharpe;
        public List<Point> series = new ArrayList<>(); public List<Fill> executions = new ArrayList<>();
        public List<AllPerformanceMeasures.Metric> performanceMeasures;
        public List<AllPerformanceMeasures.PeriodTable> periodMeasures;
        public String benchmarkSource;
    }

    public Result run(Request req) {
        Stock stock = USDStock.newInstance(req.symbol, Exchange.NEW_YORK);
        // A wide calendar buffer covers weekends/holidays for up to 250 warm-up observations.
        LocalDate from = req.start.minusDays(req.slow * 3L + 30);
        List<TimedEntry<StockEOD>> bars = prices(stock, from, req.end);
        List<TimedEntry<OrderBook>> warm = new ArrayList<>();
        BufferCache<OrderBook> books = new BufferCache<>();
        List<TimedEntry<StockEOD>> selected = new ArrayList<>();
        for (TimedEntry<StockEOD> b : bars) {
            double price = b.data().adjClose();
            if (!Double.isFinite(price) || price <= 0) throw new IllegalStateException("Price provider returned invalid data.");
            if (b.time().toLocalDate().isBefore(req.start)) warm.add(new TimedEntry<>(b.time(), new OrderBook(stock, price)));
            else if (!b.time().toLocalDate().isAfter(req.end)) {
                books.add(b.time(), new OrderBook(stock, price)); selected.add(b);
            }
        }
        if (warm.size() < req.slow || selected.size() < 2)
            throw new IllegalArgumentException("Insufficient price history for this period and slow SMA. Try a later start or longer period.");
        SMA2 sma = new SMA2(stock, req.fast, req.slow);
        BufferCache<OrderBook> history = new BufferCache<>();
        for (int i = 0; i < warm.size() - 1; i++) history.add(warm.get(i).time(), warm.get(i).data());
        sma.initialize(history);
        final OrderBook[] previous = {warm.get(warm.size() - 1).data()};
        // Decide using the previous session's close; execute at the current session's adjusted close.
        // This deliberate one-bar delay avoids using today's closing signal for a same-close fill.
        Strategy delayed = new Strategy() {
            @Override public void onEvent(TraderContext ctx, Event evt) {
            if (evt instanceof OrderBook) {
                sma.onOrderBookUpdate(ctx.now(), previous[0], ctx.marketCondition(), ctx.tradeBlotter(), ctx.broker());
                previous[0] = (OrderBook) evt;
            }
            }
        };
        PerformanceReport report = new SimpleSimulatorBuilder()
            .withInitialCash(CAPITAL).withOrderBookUpdates(new OrderBookCaches(stock, books)).build()
            .run(delayed, LocalDateTimeInterval.of(req.start.atStartOfDay(), req.end.plusDays(1).atStartOfDay()));
        Map<LocalDate, Double> navs = new TreeMap<>();
        for (TimedEntry<Double> n : report.navCache()) {
            if (!Double.isFinite(n.data())) throw new IllegalStateException("Simulation returned invalid NAV.");
            navs.put(n.time().toLocalDate(), n.data());
        }
        Result out = new Result(); out.symbol = req.symbol; out.start = req.start.toString(); out.end = req.end.toString();
        out.fast = req.fast; out.slow = req.slow; out.source = synthetic ? "SYNTHETIC — offline verification" : "Yahoo Finance daily adjusted close";
        out.generatedAt = Instant.now().toString();
        List<Double> closes = new ArrayList<>();
        for (TimedEntry<OrderBook> w : warm) closes.add(w.data().mid());
        double first = selected.get(0).data().adjClose(), peak = CAPITAL, last = CAPITAL;
        List<Double> returns = new ArrayList<>();
        for (TimedEntry<StockEOD> b : selected) {
            Double nav = navs.get(b.time().toLocalDate());
            if (nav == null) throw new IllegalStateException("Missing daily NAV observation.");
            Point p = new Point(); p.date = b.time().toLocalDate().toString(); p.close = b.data().adjClose();
            closes.add(p.close); p.fast = average(closes, req.fast); p.slow = average(closes, req.slow);
            p.nav = nav; p.benchmark = CAPITAL + p.close - first; out.series.add(p);
            peak = Math.max(peak, nav); out.maxDrawdown = Math.max(out.maxDrawdown, (peak - nav) / peak);
            returns.add(nav / last - 1); last = nav;
        }
        out.finalNav = last; out.pnl = last - CAPITAL; out.totalReturn = out.pnl / CAPITAL;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0), sum = 0;
        for (double r : returns) sum += (r - mean) * (r - mean);
        double sd = Math.sqrt(sum / (returns.size() - 1));
        if (sd > 0) { out.annualizedVolatility = sd * Math.sqrt(252); out.sharpe = mean / sd * Math.sqrt(252); }
        else out.annualizedVolatility = 0.;
        for (Execution e : report.tradeBlotter().allExecutions()) {
            Fill f = new Fill(); f.date = e.time().toString(); f.side = e.side().toString(); f.quantity = e.quantity(); f.price = e.price();
            out.executions.add(f);
        }
        // Evaluate each library measure independently so one undefined statistic cannot lose the report.
        BufferCache<Double> measureNav = new BufferCache<>();
        LocalDateTimeInterval measureInterval = LocalDateTimeInterval.of(req.start.atStartOfDay(),req.end.plusDays(1).atStartOfDay());
        measureNav.add(measureInterval.start(),CAPITAL);
        for(TimedEntry<Double> entry:report.navCache()) measureNav.add(entry.time(),entry.data());
        measureNav.add(measureInterval.end(),out.finalNav);
        BenchmarkLookup benchmark = null;
        String benchmarkError = null;
        try {
            NavigableMap<LocalDateTime,Double> market = new TreeMap<>();
            for(TimedEntry<StockEOD> b:prices(USDStock.newInstance("SPY",Exchange.NEW_YORK),from,req.end))
                if(Double.isFinite(b.data().adjClose()) && b.data().adjClose()>0) market.put(b.time(),b.data().adjClose());
            if(market.size()<2) throw new IllegalArgumentException("Insufficient benchmark history");
            benchmark = time -> {
                Map.Entry<LocalDateTime,Double> entry = market.floorEntry(time);
                return entry==null?market.firstEntry().getValue():entry.getValue();
            };
            out.benchmarkSource = synthetic?"SYNTHETIC SPY — offline verification":"SPY adjusted close from Yahoo; monthly CAPM returns; zero risk-free rate.";
        } catch(RuntimeException ex) {
            benchmarkError = "SPY benchmark data unavailable; benchmark-dependent measures cannot be computed.";
            out.benchmarkSource = benchmarkError;
        }
        AllPerformanceMeasures measures = new AllPerformanceMeasures(measureInterval,CAPITAL,measureNav,
            report.tradeBlotter().allExecutions(),new OrderBookCaches(stock,books),benchmark,benchmarkError,
            returns.stream().mapToDouble(Double::doubleValue).toArray());
        measures.compute();
        out.performanceMeasures = measures.metrics;
        out.periodMeasures = measures.periods;
        return out;
    }
    private static double average(List<Double> values, int lag) {
        double sum = 0; for (int i = values.size() - lag; i < values.size(); i++) sum += values.get(i); return sum / lag;
    }
    private List<TimedEntry<StockEOD>> prices(Stock stock, LocalDate from, LocalDate end) {
        String key = stock.symbol() + ":" + from + ":" + end;
        synchronized (cache) { if (cache.containsKey(key)) return cache.get(key); }
        List<TimedEntry<StockEOD>> result = new ArrayList<>();
        if (synthetic) {
            for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
                if (d.getDayOfWeek().getValue() > 5) continue;
                long t = ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 1), d);
                double p = 100 + .005 * t + 10 * Math.sin(t / 9.0);
                result.add(new TimedEntry<>(d.atTime(16, 0), new StockEOD(p, p + 1, p - 1, p, 1000000, p)));
            }
        } else {
            SequentialCache<StockEOD> downloaded = new YahooEODWebDownloader().newInstance(stock,
                LocalDateTimeInterval.of(from.atStartOfDay(), end.atTime(23, 59)));
            for (TimedEntry<StockEOD> b : downloaded) if (!b.time().toLocalDate().isAfter(end)) result.add(b);
        }
        result.sort(Comparator.comparing(TimedEntry::time));
        synchronized (cache) { cache.put(key, Collections.unmodifiableList(result)); }
        return result;
    }
}
