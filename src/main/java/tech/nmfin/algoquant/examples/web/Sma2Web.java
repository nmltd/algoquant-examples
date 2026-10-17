package tech.nmfin.algoquant.examples.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import dev.nm.misc.license.License;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Small same-origin web server; Cloud Run supplies HTTPS and the PORT environment variable. */
public final class Sma2Web {
    private static final Gson JSON = new Gson();
    public static void main(String[] args) throws Exception {
        File license = new File(System.getenv().getOrDefault("NM_LICENSE_FILE", "license/nm.lic"));
        if (!license.isFile()) throw new IllegalStateException("Configure NM_LICENSE_FILE with a valid server license.");
        License.setLicenseFile(license);
        boolean synthetic = "synthetic".equals(System.getenv("SMA2_DATA_MODE"));
        HttpServer server = create(Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")), new SMA2App(synthetic));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(2)));
        System.out.println("SMA2 web listening on " + server.getAddress() + "; synthetic=" + synthetic);
    }
    static HttpServer create(int port, SMA2App service) throws IOException {
        return create(port,service,ReportStore.fromEnvironment());
    }
    static HttpServer create(int port, SMA2App service, ReportStore reports) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 32);
        Semaphore running = new Semaphore(1);
        server.setExecutor(new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
            new ThreadPoolExecutor.CallerRunsPolicy()));
        server.createContext("/", e -> {
            try {
                String path = e.getRequestURI().getPath();
                if ("/api/backtest".equals(path)) {
                    if (!"POST".equals(e.getRequestMethod())) { e.getResponseHeaders().set("Allow", "POST"); send(e, 405, error("Use POST.")); return; }
                    String type = e.getRequestHeaders().getFirst("Content-Type");
                    if (type == null || !type.startsWith("application/x-www-form-urlencoded")) { send(e, 415, error("Use form-encoded input.")); return; }
                    Map<String, String> form = form(readLimited(e.getRequestBody()));
                    SMA2App.Request req;
                    try { req = new SMA2App.Request(required(form,"symbol"), required(form,"start"), required(form,"end"),
                        Integer.parseInt(required(form,"fast")), Integer.parseInt(required(form,"slow"))); }
                    catch (IllegalArgumentException ex) { send(e, 400, error(ex.getMessage())); return; }
                    if (!running.tryAcquire()) { e.getResponseHeaders().set("Retry-After", "5"); send(e, 429, error("A backtest is running. Please try again shortly.")); return; }
                    try {
                        SMA2App.Result result=service.run(req);
                        String snapshot;
                        try { snapshot=reports.save(result); }
                        catch(IOException storageError) {
                            System.err.println("Report storage failed: "+storageError.getClass().getSimpleName());
                            send(e,503,error("The backtest completed but its report could not be saved. Please retry later."));return;
                        }
                        e.getResponseHeaders().set("Location",result.reportPath);
                        send(e, 200, snapshot);
                    }
                    catch (IllegalArgumentException ex) { send(e, 400, error(ex.getMessage())); }
                    catch (Exception ex) { System.err.println("Backtest failed: " + ex.getClass().getSimpleName()); send(e, 502, error("The data provider or simulation could not complete this run. Try another period or retry later.")); }
                    finally { running.release(); }
                } else if (path.startsWith("/api/reports/") && "GET".equals(e.getRequestMethod())) {
                    String id=path.substring("/api/reports/".length());
                    ReportStore.validateId(id);
                    try {
                        String snapshot=reports.read(id);
                        if(snapshot==null)send(e,404,error("Saved report not found."));
                        else send(e,200,snapshot);
                    } catch(IOException ex) { send(e,503,error("Saved report storage is temporarily unavailable. Please retry.")); }
                } else if ("/api/health".equals(path) && "GET".equals(e.getRequestMethod())) send(e,200,"{\"status\":\"ok\"}");
                else if ("/api/stocks".equals(path) && "GET".equals(e.getRequestMethod())) send(e,200,JSON.toJson(SMA2App.SYMBOLS));
                else if ((Arrays.asList("/", "/app.js", "/style.css").contains(path) || path.matches("/reports/[a-f0-9]{32}")) && "GET".equals(e.getRequestMethod())) {
                    String file = ("/".equals(path) || path.startsWith("/reports/")) ? "index.html" : path.substring(1);
                    try (InputStream in = Sma2Web.class.getResourceAsStream("/web/" + file)) {
                        if (in == null) { send(e,404,error("Not found.")); return; }
                        ByteArrayOutputStream body = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
                        while ((n = in.read(b)) != -1) body.write(b,0,n);
                        respond(e,200,file.endsWith("js") ? "application/javascript" : file.endsWith("css") ? "text/css" : "text/html",body.toByteArray());
                    }
                } else send(e,404,error("Not found."));
            } catch (IllegalArgumentException ex) { send(e,400,error(ex.getMessage())); }
            catch (Exception ex) { System.err.println("HTTP error: " + ex.getClass().getSimpleName()); }
            finally { e.close(); }
        });
        return server;
    }
    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1) { if (out.size() >= 8192) throw new IllegalArgumentException("Request too large."); out.write(b); }
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    private static Map<String,String> form(String body) throws UnsupportedEncodingException {
        Map<String,String> values = new HashMap<>();
        for (String part : body.split("&")) { String[] p = part.split("=",2); String k = URLDecoder.decode(p[0],"UTF-8");
            if (values.put(k,p.length == 2 ? URLDecoder.decode(p[1],"UTF-8") : "") != null) throw new IllegalArgumentException("Duplicate field."); }
        return values;
    }
    private static String required(Map<String,String> f,String k) { String v = f.get(k); if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException("Missing " + k + "."); return v.trim(); }
    private static String error(String message) { return JSON.toJson(Collections.singletonMap("error",message)); }
    private static void send(HttpExchange e,int code,String body) throws IOException { respond(e,code,"application/json",body.getBytes(StandardCharsets.UTF_8)); }
    private static void respond(HttpExchange e,int code,String type,byte[] bytes) throws IOException {
        Headers h=e.getResponseHeaders(); h.set("Content-Type",type+"; charset=utf-8"); h.set("Cache-Control","no-store");
        h.set("X-Content-Type-Options","nosniff"); h.set("Referrer-Policy","same-origin");
        h.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        e.sendResponseHeaders(code,bytes.length); e.getResponseBody().write(bytes);
    }
}
