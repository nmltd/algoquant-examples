package tech.nmfin.algoquant.web.sma2;

import static tech.nmfin.algoquant.web.HttpSupport.*;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import tech.nmfin.algoquant.web.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

/** SMA2 endpoints and historical aliases. Server lifecycle and shared assets belong to ToolsWeb. */
public final class Sma2Routes implements ToolRoutes {
    private static final Gson JSON = new Gson();
    private final SMA2App service;
    private final ReportStore reports;
    private final Semaphore running;

    public Sma2Routes(SMA2App service, ReportStore reports, Semaphore running) {
        this.service = service;
        this.reports = reports;
        this.running = running;
    }

    @Override
    public boolean handle(HttpExchange e) throws IOException {
        String incoming = e.getRequestURI().getPath();
        if (!incoming.equals("/sma2")
                && !incoming.startsWith("/sma2/")
                && !incoming.startsWith("/api/")
                && !incoming.startsWith("/reports/")
                && !incoming.equals("/app.js")
                && !incoming.equals("/style.css")) return false;
        if ("GET".equals(e.getRequestMethod())
                && ("/sma2".equals(incoming) || incoming.matches("/reports/[a-f0-9]{32}"))) {
            redirect(e, "/sma2".equals(incoming) ? "/sma2/" : "/sma2" + incoming);
            return true;
        }
        boolean sma2Path = incoming.startsWith("/sma2/");
        String path = sma2Path ? incoming.substring("/sma2".length()) : incoming;
        if ("/api/backtest".equals(path)) {
            Map<String, String> form = requestForm(e);
            if (form == null) return true;
            SMA2App.Request req;
            try {
                req =
                        new SMA2App.Request(
                                required(form, "symbol"),
                                required(form, "start"),
                                required(form, "end"),
                                Integer.parseInt(required(form, "fast")),
                                Integer.parseInt(required(form, "slow")));
            } catch (IllegalArgumentException ex) {
                send(e, 400, error(ex.getMessage()));
                return true;
            }
            if (!running.tryAcquire()) {
                e.getResponseHeaders().set("Retry-After", "5");
                send(e, 429, error("A backtest is running. Please try again shortly."));
                return true;
            }
            try {
                SMA2App.Result result = service.run(req);
                String snapshot;
                try {
                    snapshot = reports.save(result, "sma2");
                } catch (IOException storageError) {
                    System.err.println(
                            "Report storage failed: " + storageError.getClass().getSimpleName());
                    send(
                            e,
                            503,
                            error(
                                    "The backtest completed but its report could not be saved."
                                            + " Please retry later."));
                    return true;
                }
                e.getResponseHeaders().set("Location", result.reportPath);
                send(e, 200, snapshot);
            } catch (IllegalArgumentException ex) {
                send(e, 400, error(ex.getMessage()));
            } catch (Exception ex) {
                System.err.println("Backtest failed: " + ex.getClass().getSimpleName());
                send(
                        e,
                        502,
                        error(
                                "The data provider or simulation could not complete this run. Try"
                                        + " another period or retry later."));
            } finally {
                running.release();
            }
        } else if (path.startsWith("/api/reports/") && "GET".equals(e.getRequestMethod())) {
            String requested = path.substring("/api/reports/".length());
            String[] parts = requested.split("/", -1);
            String id = parts[0];
            String format = parts.length == 2 ? parts[1] : null;
            if (parts.length > 2
                    || (format != null
                            && !Arrays.asList(
                                            "sma2.csv", "buy-hold.csv", "sp500.csv", "report.xlsx")
                                    .contains(format))) {
                send(e, 404, error("Not found."));
                return true;
            }
            SnapshotStore.validateId(id);
            try {
                String snapshot = reports.read(id, "sma2");
                if (snapshot == null) send(e, 404, error("Saved report not found."));
                else if (format == null) send(e, 200, snapshot);
                else {
                    SMA2App.Result saved = JSON.fromJson(snapshot, SMA2App.Result.class);
                    if ("report.xlsx".equals(format)) {
                        attachment(
                                e,
                                id + "-report.xlsx",
                                PerformanceWorkbook.MIME,
                                PerformanceWorkbook.export(saved));
                        return true;
                    }
                    String csv =
                            PerformanceCsv.export(
                                    "sma2.csv".equals(format)
                                            ? saved.performanceMeasures
                                            : "sp500.csv".equals(format)
                                                    ? (saved.sp500 == null
                                                            ? null
                                                            : saved.sp500.performanceMeasures)
                                                    : saved.buyHoldPerformanceMeasures);
                    attachment(
                            e, id + "-" + format, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ex) {
                send(
                        e,
                        503,
                        error("Saved report storage is temporarily unavailable. Please retry."));
            }
        } else if ("/api/stocks".equals(path) && "GET".equals(e.getRequestMethod()))
            send(e, 200, JSON.toJson(SMA2App.SYMBOLS));
        else if ((Arrays.asList("/", "/app.js", "/style.css").contains(path)
                        || path.matches("/reports/[a-f0-9]{32}"))
                && "GET".equals(e.getRequestMethod())) {
            String file =
                    "/".equals(path)
                            ? "sma2/index.html"
                            : path.startsWith("/reports/")
                                    ? "sma2/index.html"
                                    : "sma2/" + path.substring(1);
            resource(e, file);
        } else send(e, 404, error("Not found."));

        return true;
    }
}
