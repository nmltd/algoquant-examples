package tech.nmfin.algoquant.examples.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Immutable JSON snapshots. Cloud Run uses private GCS objects; local runs use a directory. */
final class ReportStore {
    private static final Gson JSON = new Gson();
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final Path directory;
    private final String bucket, api;
    interface Token { String value() throws IOException; }
    private final Token token;

    static ReportStore fromEnvironment() throws IOException {
        String bucket = System.getenv("SMA2_REPORT_BUCKET");
        if (bucket != null && !bucket.isEmpty())
            return new ReportStore(bucket, "https://storage.googleapis.com", ReportStore::metadataToken);
        if (System.getenv("K_SERVICE") != null)
            throw new IOException("Configure SMA2_REPORT_BUCKET for durable Cloud Run reports.");
        return new ReportStore(Paths.get(System.getenv().getOrDefault("SMA2_REPORT_DIRECTORY", "reports")));
    }
    ReportStore(Path directory) throws IOException {
        this.directory=directory.toAbsolutePath();this.bucket=null;this.api=null;this.token=null;
        Files.createDirectories(this.directory);
    }
    ReportStore(String bucket,String api,Token token) {
        if (!bucket.matches("[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]")) throw new IllegalArgumentException("Invalid report bucket.");
        this.directory=null;this.bucket=bucket;this.api=api;this.token=token;
    }
    String save(SMA2App.Result report) throws IOException {
        String id=UUID.randomUUID().toString().replace("-","");
        report.reportId=id;report.reportPath="/sma2/reports/"+id;
        report.appRevision=System.getenv().getOrDefault("SMA2_APP_REVISION","local");
        String body=JSON.toJson(report);
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Report exceeds storage limit.");
        if(directory!=null) {
            // Publish a fully written snapshot atomically, so readers cannot see a partial report.
            Path temp=Files.createTempFile(directory,"pending-",".json");
            try { Files.write(temp,bytes);Files.move(temp,directory.resolve(id+".json"),StandardCopyOption.ATOMIC_MOVE); }
            finally { Files.deleteIfExists(temp); }
        } else {
            HttpURLConnection c=connection(api+"/upload/storage/v1/b/"+encode(bucket)+"/o?uploadType=media&ifGenerationMatch=0&name="+encode(id+".json"),"POST");
            try {
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setDoOutput(true);c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
                int status=c.getResponseCode();
                if(status<200||status>=300)throw new IOException("Report upload failed (HTTP "+status+").");
            } finally { c.disconnect(); }
        }
        return body;
    }
    String read(String id) throws IOException {
        validateId(id);
        if(directory!=null) {
            Path file=directory.resolve(id+".json");
            if(!Files.isRegularFile(file))return null;
            try(InputStream in=Files.newInputStream(file)){return readBody(in);}
        }
        HttpURLConnection c=connection(api+"/storage/v1/b/"+encode(bucket)+"/o/"+encode(id+".json")+"?alt=media","GET");
        try {
            int status=c.getResponseCode();
            if(status==404)return null;
            if(status!=200)throw new IOException("Report download failed (HTTP "+status+").");
            try(InputStream in=c.getInputStream()){return readBody(in);}
        } finally { c.disconnect(); }
    }
    static void validateId(String id) {
        if(id==null||!id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid report ID.");
    }
    private HttpURLConnection connection(String url,String method) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token.value());
        return c;
    }
    private static String encode(String value) throws UnsupportedEncodingException {return URLEncoder.encode(value,"UTF-8");}
    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Stored report exceeds read limit.");out.write(b,0,n);}
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    private static String metadataToken() throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token").openConnection();
        c.setRequestProperty("Metadata-Flavor","Google");c.setConnectTimeout(3000);c.setReadTimeout(5000);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Runtime identity unavailable.");
            try(InputStream in=c.getInputStream()){
                JsonObject obj=new JsonParser().parse(readBody(in)).getAsJsonObject();
                return obj.get("access_token").getAsString();
            }
        } finally {c.disconnect();}
    }
}
