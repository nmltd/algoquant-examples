package tech.nmfin.algoquant.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same-origin HTTP responses, form parsing and bounded request handling shared by all tools. */
public final class HttpSupport {
    private static final Gson JSON = new Gson();

    public static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (out.size() >= 8192) throw new IllegalArgumentException("Request too large.");
            out.write(b);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static Map<String, String> form(String body) throws UnsupportedEncodingException {
        Map<String, String> values = new HashMap<>();
        for (String part : body.split("&")) {
            String[] p = part.split("=", 2);
            String k = URLDecoder.decode(p[0], "UTF-8");
            if (values.put(k, p.length == 2 ? URLDecoder.decode(p[1], "UTF-8") : "") != null)
                throw new IllegalArgumentException("Duplicate field.");
        }
        return values;
    }

    public static String required(Map<String, String> f, String k) {
        String v = f.get(k);
        if (v == null || v.trim().isEmpty())
            throw new IllegalArgumentException("Missing " + k + ".");
        return v.trim();
    }

    public static String error(String message) {
        return JSON.toJson(Collections.singletonMap("error", message));
    }

    public static void send(HttpExchange e, int code, String body) throws IOException {
        respond(e, code, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    public static void respond(HttpExchange e, int code, String type, byte[] bytes)
            throws IOException {
        Headers h = e.getResponseHeaders();
        h.set("Content-Type", type + "; charset=utf-8");
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "same-origin");
        h.set(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:;"
                        + " object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        e.sendResponseHeaders(code, bytes.length);
        e.getResponseBody().write(bytes);
    }

    /** Reject incorrect methods before reading a request body. */
    public static boolean requireMethod(HttpExchange e, String method) throws IOException {
        if (method.equals(e.getRequestMethod())) return true;
        e.getResponseHeaders().set("Allow", method);
        send(e, 405, error("Use " + method + "."));
        return false;
    }

    /** Both tools accept the same bounded form encoding. Null means a response was already sent. */
    public static Map<String, String> requestForm(HttpExchange e) throws IOException {
        if (!requireMethod(e, "POST")) return null;
        String type = e.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.startsWith("application/x-www-form-urlencoded")) {
            send(e, 415, error("Use form-encoded input."));
            return null;
        }
        return form(readLimited(e.getRequestBody()));
    }

    public static void redirect(HttpExchange e, String target) throws IOException {
        e.getResponseHeaders().set("Location", target);
        e.sendResponseHeaders(308, -1);
    }

    /** Serve a route-selected classpath resource; never accept an unchecked request path here. */
    public static void resource(HttpExchange e, String file) throws IOException {
        try (InputStream in = HttpSupport.class.getResourceAsStream("/web/" + file)) {
            if (in == null) {
                send(e, 404, error("Not found."));
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            respond(
                    e,
                    200,
                    file.endsWith(".js")
                            ? "application/javascript"
                            : file.endsWith(".css") ? "text/css" : "text/html",
                    out.toByteArray());
        }
    }

    /** Callers supply generated safe names (report IDs and fixed format names). */
    public static void attachment(HttpExchange e, String name, String type, byte[] body)
            throws IOException {
        if (!name.matches("[a-zA-Z0-9._-]+"))
            throw new IllegalArgumentException("Invalid download name.");
        e.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
        respond(e, 200, type, body);
    }
}
