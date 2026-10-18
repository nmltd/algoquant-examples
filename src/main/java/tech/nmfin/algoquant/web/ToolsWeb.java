package tech.nmfin.algoquant.web;

import static tech.nmfin.algoquant.web.HttpSupport.*;

import com.sun.net.httpserver.*;

import dev.nm.misc.license.License;

import tech.nmfin.algoquant.web.performance.*;
import tech.nmfin.algoquant.web.sma2.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * Shared same-origin host. Cloud Run supplies HTTPS and PORT; apps own only their routes.
 *
 * <p><b>Using this example.</b>
 * Entry point for the shared tools server: configure the license, create the report store and
 * register app routes. The shared semaphore limits concurrent calculations; route handlers
 * delegate financial work to the app services. See web/README.md for local and Cloud Run
 * configuration.
 * </p>
 */
public final class ToolsWeb {
    public static void main(String[] args) throws Exception {
        File license = new File(System.getenv().getOrDefault("NM_LICENSE_FILE", "license/nm.lic"));
        if (!license.isFile())
            throw new IllegalStateException(
                    "Configure NM_LICENSE_FILE with a valid server license.");
        License.setLicenseFile(license);
        boolean synthetic = "synthetic".equals(System.getenv("SMA2_DATA_MODE"));
        ReportStore reports = ReportStore.fromEnvironment();
        Semaphore running = new Semaphore(1);
        HttpServer server =
                create(
                        Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")),
                        new Sma2Routes(new SMA2App(synthetic), reports, running),
                        new PerformanceRoutes(new PerformanceAnalyzer(), reports, running));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(2)));
        System.out.println(
                "NM tools listening on " + server.getAddress() + "; synthetic=" + synthetic);
    }

    /** Register app modules without coupling their route implementations to one another. */
    public static HttpServer create(int port, ToolRoutes... routes) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 32);
        server.setExecutor(
                new ThreadPoolExecutor(
                        4,
                        4,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(32),
                        new ThreadPoolExecutor.CallerRunsPolicy()));
        server.createContext(
                "/",
                e -> {
                    try {
                        String path = e.getRequestURI().getPath();
                        if ("GET".equals(e.getRequestMethod())) {
                            if ("/api/health".equals(path) || "/sma2/api/health".equals(path)) {
                                send(e, 200, "{\"status\":\"ok\"}");
                                return;
                            }
                            if ("/".equals(path)) {
                                resource(e, "tools.html");
                                return;
                            }
                            if ("/tools.css".equals(path)) {
                                resource(e, "tools.css");
                                return;
                            }
                            if (Arrays.asList("/shared/report.js", "/shared/report.css")
                                    .contains(path)) {
                                resource(e, path.substring(1));
                                return;
                            }
                        }
                        for (ToolRoutes route : routes) if (route.handle(e)) return;
                        send(e, 404, error("Not found."));
                    } catch (IllegalArgumentException ex) {
                        send(e, 400, error(ex.getMessage()));
                    } catch (Exception ex) {
                        ex.printStackTrace(System.err);
                        send(e, 500, error("Report export failed. Please retry."));
                    } catch (LinkageError ex) {
                        ex.printStackTrace(System.err);
                        send(e, 500, error("Report export runtime unavailable."));
                    } finally {
                        e.close();
                    }
                });
        return server;
    }
}
