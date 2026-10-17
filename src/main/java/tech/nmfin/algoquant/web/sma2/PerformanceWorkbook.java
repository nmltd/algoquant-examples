package tech.nmfin.algoquant.web.sma2;

import static tech.nmfin.algoquant.web.SpreadsheetWorkbook.*;

import tech.nmfin.algoquant.web.SpreadsheetWorkbook;

import java.io.*;
import java.time.*;
import java.util.*;

/** SMA2 sheet selection over the shared Excel writer. */
final class PerformanceWorkbook {
    static final String MIME = SpreadsheetWorkbook.MIME;

    static byte[] export(SMA2App.Result report) throws IOException {
        String[] names = {
            "SMA2 statistics", "Buy and hold statistics", "S&P 500 statistics", "Daily data"
        };
        String[] sheets = {
            statistics(report.performanceMeasures),
            statistics(report.buyHoldPerformanceMeasures),
            statistics(report.sp500 == null ? null : report.sp500.performanceMeasures),
            daily(report)
        };

        return SpreadsheetWorkbook.export(names, sheets);
    }

    private static String daily(SMA2App.Result report) {
        List<List<Object>> rows = new ArrayList<>();
        Map<String, tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point> index = new HashMap<>();
        if (report.sp500 != null)
            for (tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point point : report.sp500.series)
                index.put(point.date, point);
        String[] headers = {
            "date",
            "adjusted_close",
            "fast_sma",
            "slow_sma",
            "sma2_nav",
            "FULL_EQUITY_V1".equals(report.sizingModel)
                    ? "fully_invested_buy_hold_nav"
                    : "legacy_one_share_benchmark_nav",
            "sp500_close",
            "sp500_nav"
        };
        for (SMA2App.Point p : report.series) {
            List<Object> row =
                    new ArrayList<>(
                            Arrays.<Object>asList(
                                    LocalDate.parse(p.date),
                                    p.close,
                                    p.fast,
                                    p.slow,
                                    p.nav,
                                    p.benchmark));
            // Join the separately simulated index by date, never by array position or forward fill.
            tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point benchmark = index.get(p.date);
            row.add(benchmark == null ? null : benchmark.close);
            row.add(benchmark == null ? null : benchmark.nav);
            rows.add(row);
        }
        return SpreadsheetWorkbook.table(headers, rows);
    }
}
