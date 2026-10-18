package tech.nmfin.algoquant.web;

import java.io.*;
import tech.nmfin.algoquant.web.AllPerformanceMeasures;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.execution.component.tradeblotter.TradeBlotter;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.performance.report.*;
import tech.nmfin.algoquant.execution.performance.report.exporter.PerformanceReportCsvExporter;

/**
 * Export saved scalar results through the library's CSV writer without rerunning a simulation.
 *
 * <p><b>Using this example.</b>
 * Reconstructs the saved account-level scalar measure view and passes it to
 * PerformanceReportCsvExporter. This adapter exports registered statistics, not daily price or
 * NAV history; daily rows are assembled separately for the workbook. The exporter logs some
 * failures, so this wrapper verifies that a complete file was produced.
 * </p>
 */
public final class PerformanceCsv {
    public static String export(List<AllPerformanceMeasures.Metric> metrics) throws IOException {
        if (metrics == null || metrics.isEmpty())
            throw new IllegalArgumentException("This saved report has no measures for this strategy. Run a new backtest.");
        final Map<String,Double> values = new LinkedHashMap<>();
        final List<KeyedMeasure> measures = new ArrayList<>();
        for (AllPerformanceMeasures.Metric metric : metrics) {
            // Parameters and units distinguish configured variants; status explains undefined values.
            String key = metric.group + " / " + metric.label + " [" + metric.unit + "] "
                + metric.sourceClass + " / " + metric.detail + " / status=" + metric.status;
            if (values.containsKey(key)) throw new IOException("Duplicate performance measure key.");
            double value = "ok".equals(metric.status) && metric.value != null ? metric.value : Double.NaN;
            values.put(key,value);
            measures.add(KeyedMeasure.measure(key,(interval,capital,fees,fills,books,rates)->value));
        }
        // These measures describe the whole funded account. Do not invent per-product results.
        PerformanceReport saved = new PerformanceReport() {
            public SequentialCache<Double> navCache() { return new BufferCache<Double>(); }
            public Collection<Product> products() { return Collections.emptyList(); }
            public Collection<KeyedMeasure> measures() { return measures; }
            public Collection<String> measureKeys() { return values.keySet(); }
            public TradeBlotter tradeBlotter() { throw new UnsupportedOperationException("Saved summary has no live blotter."); }
            public double getPerformance(String key) { return values.get(key); }
            public double getPerformance(Product product,String key) { throw new UnsupportedOperationException("Account-level measures only."); }
        };
        Path file = Files.createTempFile("performance-",".csv");
        try {
            new PerformanceReportCsvExporter().export(saved,file.toFile());
            byte[] bytes = Files.readAllBytes(file);
            // The library logs IO errors; reject incomplete output instead of returning an empty download.
            String csv = new String(bytes,StandardCharsets.UTF_8);
            if (!csv.startsWith("Product,") || !csv.contains("\nTotal,") || !csv.endsWith("\n"))
                throw new IOException("Performance CSV export did not complete.");
            return csv;
        } finally { Files.deleteIfExists(file); }
    }
}
