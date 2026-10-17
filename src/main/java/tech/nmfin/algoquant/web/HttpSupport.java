package tech.nmfin.algoquant.web;
import com.google.gson.Gson;import com.sun.net.httpserver.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Same-origin HTTP responses, form parsing and bounded request handling shared by all tools. */
public final class HttpSupport {
 private static final Gson JSON=new Gson();
    public static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1) { if (out.size() >= 8192) throw new IllegalArgumentException("Request too large."); out.write(b); }
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    public static Map<String,String> form(String body) throws UnsupportedEncodingException {
        Map<String,String> values = new HashMap<>();
        for (String part : body.split("&")) { String[] p = part.split("=",2); String k = URLDecoder.decode(p[0],"UTF-8");
            if (values.put(k,p.length == 2 ? URLDecoder.decode(p[1],"UTF-8") : "") != null) throw new IllegalArgumentException("Duplicate field."); }
        return values;
    }
    public static String required(Map<String,String> f,String k) { String v = f.get(k); if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException("Missing " + k + "."); return v.trim(); }
    public static String error(String message) { return JSON.toJson(Collections.singletonMap("error",message)); }
    public static void send(HttpExchange e,int code,String body) throws IOException { respond(e,code,"application/json",body.getBytes(StandardCharsets.UTF_8)); }
    public static void respond(HttpExchange e,int code,String type,byte[] bytes) throws IOException {
        Headers h=e.getResponseHeaders(); h.set("Content-Type",type+"; charset=utf-8"); h.set("Cache-Control","no-store");
        h.set("X-Content-Type-Options","nosniff"); h.set("Referrer-Policy","same-origin");
        h.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        e.sendResponseHeaders(code,bytes.length); e.getResponseBody().write(bytes);
    }
}
