package tech.nmfin.algoquant.web.performance;

import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
import tech.nmfin.algoquant.web.AllPerformanceMeasures;
import tech.nmfin.algoquant.web.BuyAndHoldBenchmark;
import tech.nmfin.algoquant.web.NavSeriesAnalysis;
import tech.nmfin.algoquant.web.PerformanceStatistics;
import tech.nmfin.algoquant.web.PublicCsvSource;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Analyzes daily NAV or price levels from a public CSV using AlgoQuant.
 *
 * <p>Each series is rebased to the same initial value on dates observed in every column. An
 * existing S&amp;P 500 column is reused; otherwise the shared benchmark service downloads it. Input
 * levels are preserved: this class does not infer trades or remove deposits and withdrawals.
 *
 * <p><b>Using this example.</b>
 * The tutorial path is analyze input, align observations, normalize each account and delegate
 * NAV measures to shared analysis. Missing S&amp;P 500 data is supplied through
 * BuyAndHoldBenchmark. Imported account levels carry no execution history, so trade-dependent
 * statistics cannot be reconstructed from them.
 * </p>
 */
public final class PerformanceAnalyzer {
    /** Common starting NAV used to compare series expressed in different currencies or units. */
    public static final double BASE = 10000;

    /** Injectable source loader; production requests use the restricted public HTTPS downloader. */
    public interface CsvLoader {
        String load(String url) throws IOException;
    }

    private final CsvLoader loader;
    private final BuyAndHoldBenchmark market;

    public PerformanceAnalyzer() {
        this(new PublicCsvSource()::load, new BuyAndHoldBenchmark());
    }

    /** Allows offline tests and other callers to supply data and benchmark services. */
    public PerformanceAnalyzer(CsvLoader loader, BuyAndHoldBenchmark market) {
        this.loader = loader;
        this.market = market;
    }

    /** One aligned observation, retaining both the original level and the normalized NAV. */
    public static final class Point {
        public String date;
        public double value;
        public double nav;
    }

    /** One input series or benchmark, its aligned history and computed statistics. */
    public static final class Account {
        public String name;
        public String source;
        public boolean benchmark;
        public double initialValue;
        public PerformanceStatistics.Values statistics;
        public List<AllPerformanceMeasures.Metric> performanceMeasures;
        public List<Point> series = new ArrayList<>();
    }

    /**
     * Report payload; the HTTP layer fills in persistent report identifiers and revision metadata.
     */
    public static final class Result extends tech.nmfin.algoquant.web.SavedReport {
        public String app = "performance";
        public String sourceUrl;
        public String start;
        public String end;
        public String generatedAt;
        public String benchmarkSource;
        public int reportSchemaVersion = 1;
        public int sourceRows;
        public int duplicateRows;
        public int omittedDates;
        public boolean benchmarkAdded;
        public List<String> warnings = new ArrayList<>();
        public List<Account> accounts = new ArrayList<>();
    }

    /**
     * Downloads a public source and analyzes an optional inclusive date range.
     *
     * @param url public Google Sheet or HTTPS CSV URL
     * @param start ISO start date, or blank/null to use the first input date
     * @param end ISO end date, or blank/null to use the last input date
     * @return aligned statistics and report data
     * @throws IOException if the source or benchmark cannot be downloaded
     * @throws IllegalArgumentException if the input cannot be analyzed
     */
    public Result analyze(String url, String start, String end) throws IOException {
        String canonical = PublicCsvSource.canonical(url);
        return analyzeCsv(loader.load(canonical), canonical, start, end);
    }

    /**
     * Analyzes CSV text without downloading the input, allowing deterministic tests.
     *
     * <p>The first column must be Date; remaining columns contain daily levels, not returns.
     * Missing values are omitted rather than forward-filled. Identical or complementary duplicate
     * rows are merged, while conflicting observations are rejected.
     *
     * @param csv CSV contents, including a header row
     * @param source source description or URL retained in the report
     * @param startText inclusive ISO start date, or blank/null for the input minimum
     * @param endText inclusive ISO end date, or blank/null for the input maximum
     * @return normalized accounts, statistics and data-quality warnings
     * @throws IOException if CSV parsing or benchmark retrieval fails
     * @throws IllegalArgumentException if schema, values or date overlap are invalid
     */
    public Result analyzeCsv(String csv, String source, String startText, String endText)
            throws IOException {
        if (csv.length() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("Input CSV exceeds the 2 MB limit.");
        Result result = new Result();
        result.sourceUrl = source;
        result.generatedAt = Instant.now().toString();
        List<String> names;
        List<NavigableMap<LocalDate, Double>> columns = new ArrayList<>();
        Set<LocalDate> allDates = new TreeSet<>();
        int benchmark = -1;
        // Sorted maps make input row order irrelevant and allow per-series duplicate checks.
        try (CsvListReader reader =
                new CsvListReader(new StringReader(csv), CsvPreference.STANDARD_PREFERENCE)) {
            names = reader.read();
            if (names == null || names.size() < 2 || names.size() > 11)
                throw new IllegalArgumentException(
                        "Use a Date column followed by 1–10 NAV/price columns.");
            if (!"date".equalsIgnoreCase(names.get(0).replace("\uFEFF", "").trim()))
                throw new IllegalArgumentException(
                        "The first CSV column must be Date (YYYY-MM-DD).");
            // Match benchmark aliases after validating distinct display names.
            Set<String> unique = new HashSet<>();
            for (int i = 1; i < names.size(); i++) {
                String name = names.get(i).trim();
                if (name.isEmpty()
                        || name.length() > 80
                        || !unique.add(name.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException(
                            "Series names must be unique, nonempty and at most 80 characters.");
                names.set(i, name);
                columns.add(new TreeMap<>());
                if (isBenchmark(name)) {
                    if (benchmark != -1)
                        throw new IllegalArgumentException(
                                "Use only one S&P 500 benchmark column.");
                    benchmark = i - 1;
                }
            }
            List<String> row;
            while ((row = reader.read()) != null) {
                if (row.stream().allMatch(v -> v == null || v.trim().isEmpty())) continue;
                if (++result.sourceRows > 10000)
                    throw new IllegalArgumentException("Input exceeds 10,000 daily rows.");
                if (row.size() != names.size())
                    throw new IllegalArgumentException(
                            "Every CSV row must have the same columns as the header (row "
                                    + reader.getRowNumber()
                                    + ").");
                LocalDate date = parseDate(row.get(0));
                if (!date.isBefore(LocalDate.now(ZoneId.of("America/New_York"))))
                    throw new IllegalArgumentException(
                            "Only completed dates before today in New York are supported.");
                // A repeated date may supply previously blank cells, but must not change an
                // existing value.
                if (!allDates.add(date)) result.duplicateRows++;
                for (int i = 0; i < columns.size(); i++) {
                    Double value = number(row.get(i + 1));
                    if (value == null) continue;
                    Double previous = columns.get(i).putIfAbsent(date, value);
                    if (previous != null && Double.compare(previous, value) != 0)
                        throw new IllegalArgumentException(
                                "Conflicting values for " + names.get(i + 1) + " on " + date + ".");
                }
            }
        }
        if (allDates.isEmpty()) throw new IllegalArgumentException("No daily observations found.");
        LocalDate start =
                startText == null || startText.trim().isEmpty()
                        ? Collections.min(allDates)
                        : parseDate(startText);
        LocalDate end =
                endText == null || endText.trim().isEmpty()
                        ? Collections.max(allDates)
                        : parseDate(endText);
        if (end.isBefore(start))
            throw new IllegalArgumentException("End date must be on or after start date.");
        // Reuse a supplied benchmark. Download only when it is absent from the input.
        if (benchmark == -1) {
            BuyAndHoldBenchmark.Result downloaded = market.run(start, end, BASE);
            NavigableMap<LocalDate, Double> data = new TreeMap<>();
            for (BuyAndHoldBenchmark.Point p : downloaded.series)
                data.put(LocalDate.parse(p.date), p.close);
            columns.add(data);
            names.add("S&P 500");
            benchmark = columns.size() - 1;
            result.benchmarkAdded = true;
            result.benchmarkSource = downloaded.source;
        } else
            result.benchmarkSource =
                    "S&P 500 column supplied in the input; no Yahoo benchmark was downloaded.";
        if (columns.size() == 1)
            throw new IllegalArgumentException(
                    "Include at least one non-benchmark NAV/price series.");
        // Compare every account over exactly the same observed dates and inclusive period.
        NavigableSet<LocalDate> aligned = new TreeSet<>(columns.get(0).keySet());
        for (NavigableMap<LocalDate, Double> data : columns) aligned.retainAll(data.keySet());
        aligned.removeIf(date -> date.isBefore(start) || date.isAfter(end));
        if (aligned.size() < 2)
            throw new IllegalArgumentException(
                    "At least two common dates with a value in every series are required.");
        result.start = aligned.first().toString();
        result.end = aligned.last().toString();
        int requestedDates = 0;
        for (LocalDate date : allDates)
            if (!date.isBefore(start) && !date.isAfter(end)) requestedDates++;
        result.omittedDates = requestedDates - aligned.size();
        result.warnings.add(
                "Input columns are NAV/price levels, not daily returns. Deposits and withdrawals"
                        + " are not inferred or removed. Use unitized NAV or cash-flow-adjusted"
                        + " levels.");
        result.warnings.add(
                "Comparisons use only common observed dates; blanks are not forward-filled. Daily"
                        + " volatility/Sharpe assume trading-session observations and annualize by"
                        + " sqrt(252).");
        if (result.duplicateRows > 0)
            result.warnings.add(
                    result.duplicateRows
                            + " repeated date rows were merged; conflicting duplicate values are"
                            + " rejected.");
        if (result.omittedDates > 0)
            result.warnings.add(
                    result.omittedDates
                            + " input dates were omitted because one or more series had no"
                            + " observation.");
        // AlgoQuant may query intraday or boundary timestamps; resolve to the latest known daily
        // level.
        NavigableMap<LocalDate, Double> benchmarkValues = columns.get(benchmark);
        BenchmarkLookup lookup =
                time -> {
                    Map.Entry<LocalDate, Double> p = benchmarkValues.floorEntry(time.toLocalDate());
                    return p == null ? benchmarkValues.firstEntry().getValue() : p.getValue();
                };
        for (int i = 0; i < columns.size(); i++) {
            Account account = new Account();
            account.name = names.get(i + 1);
            account.benchmark = i == benchmark;
            account.source = account.benchmark ? result.benchmarkSource : "Input NAV/price column";
            account.initialValue = columns.get(i).get(aligned.first());
            if (account.initialValue <= 0)
                throw new IllegalArgumentException(
                        "The first common value must be positive for " + account.name + ".");
            List<String> dates = new ArrayList<>();
            List<Double> navs = new ArrayList<>();
            // Rebase levels without inventing executions or changing the source account values.
            double previous = account.initialValue;
            int large = 0;
            for (LocalDate date : aligned) {
                double value = columns.get(i).get(date), nav = BASE * value / account.initialValue;
                if (!Double.isFinite(nav))
                    throw new IllegalArgumentException(
                            "Series cannot be normalized: " + account.name);
                Point point = new Point();
                point.date = date.toString();
                point.value = value;
                point.nav = nav;
                account.series.add(point);
                dates.add(point.date);
                navs.add(nav);
                // Flag large changes for review; they may be cash flows rather than investment
                // returns.
                if (Math.abs(value / previous - 1) > .3 && large++ < 3)
                    result.warnings.add(
                            account.name
                                    + " changes by more than 30% on "
                                    + date
                                    + ". Check for deposits, withdrawals, unit changes or input"
                                    + " errors; the value has not been altered.");
                previous = value;
            }
            // Keep zero/negative observations visible. Shared calculators suppress invalid return
            // metrics.
            long nonpositive = account.series.stream().filter(p -> p.value <= 0).count();
            if (nonpositive > 0)
                result.warnings.add(
                        account.name
                                + " contains "
                                + nonpositive
                                + " zero/negative NAV observations. Return, CAGR and risk-adjusted"
                                + " statistics are unavailable. Source values have not been"
                                + " changed.");
            account.statistics =
                    PerformanceStatistics.compute(
                            dates, navs, BASE, new PerformanceStatistics.Values());
            account.performanceMeasures = NavSeriesAnalysis.metrics(dates, navs, BASE, lookup);
            result.accounts.add(account);
        }
        return result;
    }

    /** Recognizes common S&amp;P 500 labels independently of capitalization and punctuation. */
    public static boolean isBenchmark(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return Arrays.asList("sp500", "sandp500", "gspc", "spx", "sp500close", "sp500nav")
                .contains(n);
    }

    /** Require unambiguous ISO dates instead of guessing locale-specific date formats. */
    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("Use daily ISO dates: YYYY-MM-DD.");
        }
    }

    /** Parse finite levels, accepting missing cells and correctly grouped thousands separators. */
    private static Double number(String text) {
        if (text == null
                || text.trim().isEmpty()
                || "N/A".equalsIgnoreCase(text.trim())
                || "NA".equalsIgnoreCase(text.trim())) return null;
        String raw = text.trim();
        if (raw.startsWith("$")) raw = raw.substring(1).trim();
        if (raw.contains(",") && !raw.matches("[+-]?[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?"))
            throw new IllegalArgumentException("Invalid thousands separators in numeric value.");
        try {
            double value = Double.parseDouble(raw.replace(",", ""));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "NAV/price levels must be finite numbers; daily return percentages are not"
                            + " supported.");
        }
    }
}
