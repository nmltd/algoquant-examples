package tech.nmfin.algoquant.web.performance;
import java.io.*;import java.nio.charset.StandardCharsets;import java.time.LocalDate;import java.util.*;import java.util.concurrent.Semaphore;
import com.google.gson.Gson;import com.sun.net.httpserver.HttpExchange;import tech.nmfin.algoquant.web.*;
import static tech.nmfin.algoquant.web.HttpSupport.*;
/** Performance Analyzer routes, using the same immutable store and capacity gate as the other apps. */
public final class PerformanceRoutes {
 private static final Gson JSON=new Gson();private final PerformanceAnalyzer analyzer;private final SnapshotStore reports;private final Semaphore gate;
 public PerformanceRoutes(PerformanceAnalyzer analyzer,SnapshotStore reports,Semaphore gate){this.analyzer=analyzer;this.reports=reports;this.gate=gate;}
 public boolean handle(HttpExchange exchange)throws IOException {
  String path=exchange.getRequestURI().getPath();
  if(!path.equals("/performance")&&!path.startsWith("/performance/"))return false;
  try {
   if(path.equals("/performance")){exchange.getResponseHeaders().set("Location","/performance/");exchange.sendResponseHeaders(308,-1);return true;}
   if(path.equals("/performance/api/analyze")){
    if(!"POST".equals(exchange.getRequestMethod())){exchange.getResponseHeaders().set("Allow","POST");send(exchange,405,error("Use POST."));return true;}
    String type=exchange.getRequestHeaders().getFirst("Content-Type");
    if(type==null||!type.startsWith("application/x-www-form-urlencoded")){send(exchange,415,error("Use form-encoded input."));return true;}
    Map<String,String> form=form(readLimited(exchange.getRequestBody()));
    String url=required(form,"url");if(url.length()>3000)throw new IllegalArgumentException("URL exceeds 3,000 characters.");
    PublicCsvSource.canonical(url);
    if(!gate.tryAcquire()){exchange.getResponseHeaders().set("Retry-After","5");send(exchange,429,error("An analysis or backtest is running. Please retry shortly."));return true;}
    try {
     PerformanceAnalyzer.Result report=analyzer.analyze(url,form.get("start"),form.get("end"));
     report.reportId=UUID.randomUUID().toString().replace("-","");report.reportPath="/performance/reports/"+report.reportId;
     report.appRevision=System.getenv().getOrDefault("SMA2_APP_REVISION","local");
     String snapshot=reports.save(report.reportId,JSON.toJson(report));
     exchange.getResponseHeaders().set("Location",report.reportPath);send(exchange,200,snapshot);
    }finally{gate.release();}
    return true;
   }
   if(!"GET".equals(exchange.getRequestMethod())){send(exchange,405,error("Use GET."));return true;}
   if(path.startsWith("/performance/api/reports/")){
    String[] parts=path.substring("/performance/api/reports/".length()).split("/",-1);SnapshotStore.validateId(parts[0]);
    if(parts.length>2){send(exchange,404,error("Not found."));return true;}
    String body=reports.read(parts[0]);if(body==null){send(exchange,404,error("Saved report not found."));return true;}
    PerformanceAnalyzer.Result report=JSON.fromJson(body,PerformanceAnalyzer.Result.class);
    if(!"performance".equals(report.app)){send(exchange,404,error("Performance report not found."));return true;}
    if(parts.length==1){send(exchange,200,body);return true;}
    String format=parts[1];
    if("report.xlsx".equals(format)){
     exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=\"performance-"+report.reportId+".xlsx\"");
     respond(exchange,200,SpreadsheetWorkbook.MIME,workbook(report));return true;
    }
    if(format.matches("series-[0-9]+\\.csv")){
     int index=Integer.parseInt(format.substring(7,format.length()-4));if(index>=report.accounts.size())throw new IllegalArgumentException("Unknown series.");
     exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+format+"\"");
     respond(exchange,200,"text/csv",PerformanceCsv.export(report.accounts.get(index).performanceMeasures).getBytes(StandardCharsets.UTF_8));return true;
    }
    send(exchange,404,error("Not found."));return true;
   }
   String file=null;
   if(path.equals("/performance/")||path.matches("/performance/reports/[a-f0-9]{32}"))file="index.html";
   if(path.equals("/performance/app.js"))file="app.js";
   if(file==null){send(exchange,404,error("Not found."));return true;}
   try(InputStream in=PerformanceRoutes.class.getResourceAsStream("/web/performance/"+file)){
    if(in==null){send(exchange,404,error("Not found."));return true;}
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int count;while((count=in.read(b))!=-1)out.write(b,0,count);
    respond(exchange,200,file.endsWith(".js")?"application/javascript":"text/html",out.toByteArray());
   }
  }catch(IllegalArgumentException ex){send(exchange,400,error(ex.getMessage()));}
  catch(IOException ex){send(exchange,502,error(ex.getMessage()==null?"Input or report storage is temporarily unavailable.":ex.getMessage()));}
  catch(RuntimeException ex){ex.printStackTrace(System.err);send(exchange,502,error("The input could not be analyzed. Check the CSV format and retry."));}
  return true;
 }
 public static byte[] workbook(PerformanceAnalyzer.Result report)throws IOException {
  List<String> names=new ArrayList<>(),sheets=new ArrayList<>();
  for(PerformanceAnalyzer.Account account:report.accounts){names.add(account.name+" statistics");sheets.add(SpreadsheetWorkbook.statistics(account.performanceMeasures));}
  List<String> headers=new ArrayList<>();headers.add("date");
  for(PerformanceAnalyzer.Account account:report.accounts){headers.add(account.name+" source level");headers.add(account.name+" normalized NAV");}
  List<List<Object>> rows=new ArrayList<>();
  for(int i=0;i<report.accounts.get(0).series.size();i++){
   List<Object> row=new ArrayList<>();row.add(LocalDate.parse(report.accounts.get(0).series.get(i).date));
   for(PerformanceAnalyzer.Account account:report.accounts){row.add(account.series.get(i).value);row.add(account.series.get(i).nav);}rows.add(row);
  }
  names.add("Daily data");sheets.add(SpreadsheetWorkbook.table(headers.toArray(new String[0]),rows));
  rows=new ArrayList<>();rows.add(Arrays.<Object>asList("Source URL",report.sourceUrl));rows.add(Arrays.<Object>asList("Saved report",report.reportPath));
  rows.add(Arrays.<Object>asList("Period",report.start+" to "+report.end));rows.add(Arrays.<Object>asList("Benchmark",report.benchmarkSource));
  rows.add(Arrays.<Object>asList("Normalization","All accounts start at 10,000 normalized units; original levels are retained in Daily data."));
  for(String warning:report.warnings)rows.add(Arrays.<Object>asList("Note",warning));
  names.add("Analysis notes");sheets.add(SpreadsheetWorkbook.table(new String[]{"Item","Details"},rows));
  return SpreadsheetWorkbook.export(names.toArray(new String[0]),sheets.toArray(new String[0]));
 }
}
