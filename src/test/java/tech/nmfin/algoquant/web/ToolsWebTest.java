package tech.nmfin.algoquant.web;

import static org.junit.Assert.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import tech.nmfin.algoquant.web.performance.*;
import tech.nmfin.algoquant.web.sma2.*;

import java.net.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/** Regression coverage for the shared host, app isolation and historical report URLs. */
public class ToolsWebTest {
    @Test
    public void hostRoutesAppsAndKeepsExistingSnapshotsReadable() throws Exception {
        Path dir = Files.createTempDirectory("tools-reports-");
        SnapshotStore snapshots = new SnapshotStore(dir);
        ReportStore reports = new ReportStore(snapshots);
        Semaphore gate = new Semaphore(1);
        HttpServer server =
                ToolsWeb.create(
                        0,
                        new Sma2Routes(new SMA2App(true), reports, gate),
                        new PerformanceRoutes(new PerformanceAnalyzer(), reports, gate));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            for (String path :
                    new String[] {
                        "/",
                        "/sma2/",
                        "/performance/",
                        "/shared/report.js",
                        "/shared/report.css",
                        "/api/health",
                        "/sma2/api/health"
                    }) {
                HttpURLConnection c = request(base, path, "GET");
                assertEquals(path, 200, c.getResponseCode());
                c.disconnect();
            }
            assertEquals(404, request(base, "/shared/../../pom.xml", "GET").getResponseCode());
            assertEquals(405, request(base, "/sma2/api/backtest", "GET").getResponseCode());
            assertEquals(405, request(base, "/performance/api/analyze", "GET").getResponseCode());
            String id = "12345678901234567890123456789012";
            // A pre-refactor SMA2 snapshot has no app discriminator and metadata is stored as flat
            // fields.
            String legacy =
                    "{\"reportId\":\""
                            + id
                            + "\",\"reportPath\":\"/sma2/reports/"
                            + id
                            + "\",\"symbol\":\"AAPL\"}";
            snapshots.save(id, legacy);
            assertEquals(legacy, reports.read(id, "sma2"));
            assertNull(reports.read(id, "performance"));
            assertEquals(200, request(base, "/sma2/api/reports/" + id, "GET").getResponseCode());
            assertEquals(
                    404, request(base, "/performance/api/reports/" + id, "GET").getResponseCode());
            HttpURLConnection redirect = request(base, "/reports/" + id, "GET");
            assertEquals(308, redirect.getResponseCode());
            assertEquals("/sma2/reports/" + id, redirect.getHeaderField("Location"));
            PerformanceAnalyzer.Result analyzer = new PerformanceAnalyzer.Result();
            reports.save(analyzer, "performance");
            assertNull(reports.read(analyzer.reportId, "sma2"));
            assertEquals(
                    404,
                    request(base, "/sma2/api/reports/" + analyzer.reportId, "GET")
                            .getResponseCode());
        } finally {
            server.stop(0);
            ((ExecutorService) server.getExecutor()).shutdownNow();
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                for (Path path : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator)
                    Files.delete(path);
            }
        }
    }

    private static HttpURLConnection request(String base, String path, String method)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        return c;
    }
}
