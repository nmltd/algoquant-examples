package tech.nmfin.algoquant.web.performance;

import static tech.nmfin.algoquant.web.HttpSupport.*;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import tech.nmfin.algoquant.web.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Performance Analyzer routes, using the same immutable store and capacity gate as the other apps.
 *
 * <p><b>Using this example.</b>
 * Adapts public-URL form requests to PerformanceAnalyzer, saves the completed result and
 * serves its report and downloads. Use the shared capacity gate for computations and the
 * immutable store for report retrieval; export requests should not download market data again.
 * </p>
 */
public final class PerformanceRoutes implements ToolRoutes {
    private static final Gson JSON = new Gson();
    private final PerformanceAnalyzer analyzer;
    private final ReportStore reports;
    private final Semaphore gate;

    public PerformanceRoutes(PerformanceAnalyzer analyzer, ReportStore reports, Semaphore gate) {
        this.analyzer = analyzer;
        this.reports = reports;
        this.gate = gate;
    }

    public boolean handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.equals("/performance") && !path.startsWith("/performance/")) return false;
        try {
            if (path.equals("/performance")) {
                redirect(exchange, "/performance/");
                return true;
            }
            if (path.equals("/performance/api/analyze")) {
                Map<String, String> form = requestForm(exchange);
                if (form == null) return true;
                String url = required(form, "url");
                if (url.length() > 3000)
                    throw new IllegalArgumentException("URL exceeds 3,000 characters.");
                PublicCsvSource.canonical(url);
                if (!gate.tryAcquire()) {
                    exchange.getResponseHeaders().set("Retry-After", "5");
                    send(
                            exchange,
                            429,
                            error("An analysis or backtest is running. Please retry shortly."));
                    return true;
                }
                try {
                    PerformanceAnalyzer.Result report =
                            analyzer.analyze(url, form.get("start"), form.get("end"));
                    String snapshot = reports.save(report, "performance");
                    exchange.getResponseHeaders().set("Location", report.reportPath);
                    send(exchange, 200, snapshot);
                } finally {
                    gate.release();
                }
                return true;
            }
            if (!requireMethod(exchange, "GET")) return true;
            if (path.startsWith("/performance/api/reports/")) {
                String[] parts =
                        path.substring("/performance/api/reports/".length()).split("/", -1);
                SnapshotStore.validateId(parts[0]);
                if (parts.length > 2) {
                    send(exchange, 404, error("Not found."));
                    return true;
                }
                String body = reports.read(parts[0], "performance");
                if (body == null) {
                    send(exchange, 404, error("Saved report not found."));
                    return true;
                }
                PerformanceAnalyzer.Result report =
                        JSON.fromJson(body, PerformanceAnalyzer.Result.class);
                if (!"performance".equals(report.app)) {
                    send(exchange, 404, error("Performance report not found."));
                    return true;
                }
                if (parts.length == 1) {
                    send(exchange, 200, body);
                    return true;
                }
                String format = parts[1];
                if ("report.xlsx".equals(format)) {
                    attachment(
                            exchange,
                            "performance-" + report.reportId + ".xlsx",
                            SpreadsheetWorkbook.MIME,
                            PerformanceWorkbook.export(report));
                    return true;
                }
                if (format.matches("series-[0-9]+\\.csv")) {
                    int index = Integer.parseInt(format.substring(7, format.length() - 4));
                    if (index >= report.accounts.size())
                        throw new IllegalArgumentException("Unknown series.");
                    attachment(
                            exchange,
                            format,
                            "text/csv",
                            PerformanceCsv.export(report.accounts.get(index).performanceMeasures)
                                    .getBytes(StandardCharsets.UTF_8));
                    return true;
                }
                send(exchange, 404, error("Not found."));
                return true;
            }
            String file = null;
            if (path.equals("/performance/") || path.matches("/performance/reports/[a-f0-9]{32}"))
                file = "index.html";
            if (path.equals("/performance/app.js")) file = "app.js";
            if (file == null) {
                send(exchange, 404, error("Not found."));
                return true;
            }
            resource(exchange, "performance/" + file);
        } catch (IllegalArgumentException ex) {
            send(exchange, 400, error(ex.getMessage()));
        } catch (IOException ex) {
            send(
                    exchange,
                    502,
                    error(
                            ex.getMessage() == null
                                    ? "Input or report storage is temporarily unavailable."
                                    : ex.getMessage()));
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            send(
                    exchange,
                    502,
                    error("The input could not be analyzed. Check the CSV format and retry."));
        }
        return true;
    }
}
