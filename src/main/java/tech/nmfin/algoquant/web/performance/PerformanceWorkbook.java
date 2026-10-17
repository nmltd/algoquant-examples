package tech.nmfin.algoquant.web.performance;

import tech.nmfin.algoquant.web.SpreadsheetWorkbook;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/** Analyzer worksheet selection over the shared Excel writer. */
public final class PerformanceWorkbook {
    public static byte[] export(PerformanceAnalyzer.Result report) throws IOException {
        List<String> names = new ArrayList<>(), sheets = new ArrayList<>();
        for (PerformanceAnalyzer.Account account : report.accounts) {
            names.add(account.name + " statistics");
            sheets.add(SpreadsheetWorkbook.statistics(account.performanceMeasures));
        }
        List<String> headers = new ArrayList<>();
        headers.add("date");
        for (PerformanceAnalyzer.Account account : report.accounts) {
            headers.add(account.name + " source level");
            headers.add(account.name + " normalized NAV");
        }
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < report.accounts.get(0).series.size(); i++) {
            List<Object> row = new ArrayList<>();
            row.add(LocalDate.parse(report.accounts.get(0).series.get(i).date));
            for (PerformanceAnalyzer.Account account : report.accounts) {
                row.add(account.series.get(i).value);
                row.add(account.series.get(i).nav);
            }
            rows.add(row);
        }
        names.add("Daily data");
        sheets.add(SpreadsheetWorkbook.table(headers.toArray(new String[0]), rows));
        rows = new ArrayList<>();
        rows.add(Arrays.<Object>asList("Source URL", report.sourceUrl));
        rows.add(Arrays.<Object>asList("Saved report", report.reportPath));
        rows.add(Arrays.<Object>asList("Period", report.start + " to " + report.end));
        rows.add(Arrays.<Object>asList("Benchmark", report.benchmarkSource));
        rows.add(
                Arrays.<Object>asList(
                        "Normalization",
                        "All accounts start at 10,000 normalized units; original levels are"
                                + " retained in Daily data."));
        for (String warning : report.warnings) rows.add(Arrays.<Object>asList("Note", warning));
        names.add("Analysis notes");
        sheets.add(SpreadsheetWorkbook.table(new String[] {"Item", "Details"}, rows));
        return SpreadsheetWorkbook.export(
                names.toArray(new String[0]), sheets.toArray(new String[0]));
    }
}
