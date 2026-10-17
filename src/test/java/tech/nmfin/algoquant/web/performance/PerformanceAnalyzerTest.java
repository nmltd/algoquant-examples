package tech.nmfin.algoquant.web.performance;
import org.junit.*;import static org.junit.Assert.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.Semaphore;import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.Gson;import com.sun.net.httpserver.HttpServer;
import tech.nmfin.algoquant.web.*;import tech.nmfin.algoquant.data.cache.TimedEntry;import tech.nmfin.algoquant.execution.datatype.StockEOD;
public class PerformanceAnalyzerTest {
 @BeforeClass public static void license(){TestLicense.load();}
 static String sample(){
  StringBuilder csv=new StringBuilder("Date,Alpha,Beta,S&P 500\n");
  for(LocalDate d=LocalDate.of(2020,1,1);!d.isAfter(LocalDate.of(2020,12,31));d=d.plusDays(1)){
   if(d.getDayOfWeek().getValue()>5)continue;double t=d.getDayOfYear();
   String line=d+","+(100+t*.05+Math.sin(t/10))+","+(200+t*.08+Math.sin(t/15))+","+(d.getDayOfYear()%37==0?"":String.valueOf(3000+t*2+30*Math.sin(t/20)))+"\n";
   csv.append(line);if(d.equals(LocalDate.of(2020,1,2)))csv.append(line);
  }return csv.toString();
 }
 static PerformanceAnalyzer analyzer(String csv){
  return new PerformanceAnalyzer(url->csv,new BuyAndHoldBenchmark((stock,start,end)->{throw new AssertionError("Input benchmark must be reused.");}));
 }
 @Test public void reuseBenchmarkNormalizeAndSaveFixture()throws Exception {
  PerformanceAnalyzer.Result r=analyzer(sample()).analyze("https://example.com/nav.csv","","");
  assertFalse(r.benchmarkAdded);assertEquals(3,r.accounts.size());assertEquals(1,r.duplicateRows);assertTrue(r.omittedDates>0);
  for(PerformanceAnalyzer.Account a:r.accounts){
   assertEquals(10000,a.series.get(0).nav,1e-8);assertEquals(187,a.performanceMeasures.size());
   for(PerformanceAnalyzer.Point p:a.series)assertEquals(p.value/a.initialValue,p.nav/10000,1e-10);
   assertEquals(a.series.get(a.series.size()-1).nav/10000-1,a.statistics.totalReturn,1e-10);
   assertTrue(a.performanceMeasures.stream().anyMatch(m->m.sourceClass.equals("ExecutionCount")&&m.status.equals("not_applicable")));
   assertTrue(a.performanceMeasures.stream().anyMatch(m->m.sourceClass.equals("CumulativeReturn")&&m.status.equals("ok")));
  }
  r.reportId="11111111111111111111111111111111";r.reportPath="/performance/reports/"+r.reportId;r.appRevision="fixture";
  Path dir=Paths.get("target/web-reports");Files.createDirectories(dir);
  Files.write(dir.resolve("performance.json"),new Gson().toJson(r).getBytes(StandardCharsets.UTF_8));
  Files.write(dir.resolve("performance.xlsx"),PerformanceRoutes.workbook(r));
 }
 @Test public void appendMissingBenchmarkWithoutInventingTrades()throws Exception {
  BuyAndHoldBenchmark market=new BuyAndHoldBenchmark((stock,start,end)->{
   List<TimedEntry<StockEOD>> quotes=new ArrayList<>();
   for(int i=1;i<=5;i++){double p=100+i;quotes.add(new TimedEntry<>(LocalDate.of(2020,1,i).atTime(16,0),new StockEOD(p,p,p,p,1000,p)));}return quotes;
  });
  PerformanceAnalyzer a=new PerformanceAnalyzer(url->"",market);
  PerformanceAnalyzer.Result r=a.analyzeCsv("Date,Fund\n2020-01-01,50\n2020-01-02,55\n2020-01-03,54\n","https://example.com/nav.csv","","");
  assertTrue(r.benchmarkAdded);assertEquals(2,r.accounts.size());assertTrue(r.accounts.get(1).benchmark);
  assertEquals(54./50-1,r.accounts.get(0).statistics.totalReturn,1e-10);
 }
 @Test public void rejectConflictsInvalidLevelsAndInsufficientOverlap()throws Exception {
  for(String csv:new String[]{
   "Date,Fund,S&P 500\n2020-01-01,100,3000\n2020-01-01,101,3000\n2020-01-02,102,3001\n",
   "Date,Fund,S&P 500\n2020-01-01,-1,3000\n2020-01-02,2,3001\n",
   "Date,Fund,S&P 500\n2020-01-01,100,\n2020-01-02,,3001\n",
   "Date,Fund,S&P 500\n2020-01-01,NaN,3000\n2020-01-02,100,3001\n"}){
   try{analyzer(csv).analyze("https://example.com/nav.csv","","");fail("Invalid input accepted");}catch(IllegalArgumentException expected){}
  }
 }
 @Test public void googleUrlsAndPrivateDestinationsAreHandledSafely()throws Exception {
  assertEquals("https://docs.google.com/spreadsheets/d/abc_123/export?format=csv&gid=7",PublicCsvSource.canonical("https://docs.google.com/spreadsheets/d/abc_123/edit#gid=7"));
  for(String url:new String[]{"file:///etc/passwd","http://example.com/data.csv","https://user:password@example.com/data.csv","https://example.com:8443/data.csv"}){
   try{PublicCsvSource.canonical(url);fail();}catch(IllegalArgumentException expected){}
  }
  for(String host:new String[]{"127.0.0.1","169.254.169.254","10.0.0.1","[::1]"}){
   try{PublicCsvSource.validateDestination(URI.create("https://"+host+"/data.csv"));fail();}catch(IllegalArgumentException expected){}
  }
 }
 @Test public void httpReportsAndExportsUseStoredSnapshot()throws Exception {
  AtomicInteger downloads=new AtomicInteger();Path dir=Files.createTempDirectory("performance-test-");
  PerformanceAnalyzer analyzer=new PerformanceAnalyzer(url->{downloads.incrementAndGet();return sample();},new BuyAndHoldBenchmark((s,a,b)->{throw new AssertionError();}));
  SnapshotStore store=new SnapshotStore(dir);PerformanceRoutes routes=new PerformanceRoutes(analyzer,store,new Semaphore(1));
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{try{routes.handle(x);}finally{x.close();}});server.start();
  try{
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   HttpURLConnection connection=(HttpURLConnection)new URL(base+"/performance/api/analyze").openConnection();
   connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
   try(OutputStream out=connection.getOutputStream()){out.write("url=https%3A%2F%2Fexample.com%2Fnav.csv".getBytes(StandardCharsets.UTF_8));}
   assertEquals(200,connection.getResponseCode());String body=read(connection.getInputStream());
   PerformanceAnalyzer.Result r=new Gson().fromJson(body,PerformanceAnalyzer.Result.class);
   assertTrue(r.reportPath.startsWith("/performance/reports/"));
   assertEquals(body,new SnapshotStore(dir).read(r.reportId));
   try(InputStream in=new URL(base+"/performance/api/reports/"+r.reportId).openStream()){assertEquals(body,read(in));}
   try(InputStream in=new URL(base+"/performance/api/reports/"+r.reportId+"/report.xlsx").openStream()){assertEquals('P',in.read());assertEquals('K',in.read());}
   try(InputStream in=new URL(base+"/performance/api/reports/"+r.reportId+"/series-0.csv").openStream()){assertTrue(read(in).startsWith("Product,"));}
   assertEquals(1,downloads.get());
  }finally{server.stop(0);try(java.util.stream.Stream<Path> files=Files.list(dir)){files.forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}Files.delete(dir);}
 }
 private static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
}
