package tech.nmfin.algoquant.examples.web;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class ReportStoreTest {
    @Test public void snapshotsSurviveStoreRecreationAndRunsHaveIndependentIds() throws Exception {
        Path dir=Files.createTempDirectory("sma2-reports-");
        try {
            ReportStore first=new ReportStore(dir);
            SMA2App.Result result=new SMA2App.Result();result.symbol="AAPL";result.pnl=12.34;
            String original=first.save(result),id=result.reportId;
            assertEquals(original,new ReportStore(dir).read(id));
            result.pnl=999;
            String second=first.save(result);
            assertNotEquals(id,result.reportId);
            assertEquals(original,first.read(id));
            assertEquals(second,first.read(result.reportId));
            assertNull(first.read("00000000000000000000000000000000"));
            for(String bad:Arrays.asList("../secret","a/b","",id+".json")) {
                try {first.read(bad);fail("Accepted unsafe ID");}catch(IllegalArgumentException expected){}
            }
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(dir)){
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
    @Test public void cloudProtocolCreatesImmutableObjectAndLoadsExactSnapshot() throws Exception {
        final byte[][] object={null};
        final String[] name={null};
        HttpServer cloud=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        cloud.createContext("/",e->{
            assertEquals("Bearer test-token",e.getRequestHeaders().getFirst("Authorization"));
            if("POST".equals(e.getRequestMethod())) {
                assertTrue(e.getRequestURI().getQuery().contains("ifGenerationMatch=0"));
                assertTrue(e.getRequestURI().getQuery().contains("uploadType=media"));
                String query=e.getRequestURI().getRawQuery();
                name[0]=URLDecoder.decode(query.substring(query.indexOf("&name=")+6),"UTF-8");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                while((n=e.getRequestBody().read(b))!=-1)out.write(b,0,n);
                object[0]=out.toByteArray();e.sendResponseHeaders(200,2);e.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            } else if(e.getRequestURI().getPath().endsWith(name[0])) {
                assertEquals("alt=media",e.getRequestURI().getQuery());
                e.sendResponseHeaders(200,object[0].length);e.getResponseBody().write(object[0]);
            } else e.sendResponseHeaders(404,-1);
            e.close();
        });
        cloud.start();
        try {
            ReportStore store=new ReportStore("test-bucket","http://127.0.0.1:"+cloud.getAddress().getPort(),()->"test-token");
            SMA2App.Result r=new SMA2App.Result();r.symbol="MSFT";
            String json=store.save(r);
            assertEquals(r.reportId+".json",name[0]);
            assertEquals(json,store.read(r.reportId));
            assertNull(store.read("00000000000000000000000000000000"));
        } finally {cloud.stop(0);}
    }
    @Test public void failedCloudWriteIsNotReportedAsSaved() throws Exception {
        HttpServer cloud=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        cloud.createContext("/",e->{e.sendResponseHeaders(403,-1);e.close();});cloud.start();
        try {
            ReportStore store=new ReportStore("test-bucket","http://127.0.0.1:"+cloud.getAddress().getPort(),()->"test-token");
            try {store.save(new SMA2App.Result());fail("Must reject failed storage");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("403"));}
        } finally {cloud.stop(0);}
    }
}
