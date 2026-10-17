package tech.nmfin.algoquant.web.performance;
import java.io.*;import java.time.*;import java.time.format.DateTimeParseException;import java.util.*;
import org.supercsv.io.CsvListReader;import org.supercsv.prefs.CsvPreference;
import tech.nmfin.algoquant.web.*;import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
/** Analyze public daily NAV/price CSV columns using AlgoQuant, with an existing or downloaded S&P benchmark. */
public final class PerformanceAnalyzer {
 public static final double BASE=10000;
 public interface CsvLoader {String load(String url)throws IOException;}
 private final CsvLoader loader;private final BuyAndHoldBenchmark market;
 public PerformanceAnalyzer(){this(new PublicCsvSource()::load,new BuyAndHoldBenchmark());}
 public PerformanceAnalyzer(CsvLoader loader,BuyAndHoldBenchmark market){this.loader=loader;this.market=market;}
 public static final class Point {public String date;public double value,nav;}
 public static final class Account {
  public String name,source;public boolean benchmark;public double initialValue;
  public PerformanceStatistics.Values statistics;public List<AllPerformanceMeasures.Metric> performanceMeasures;public List<Point> series=new ArrayList<>();
 }
 public static final class Result {
  public String app="performance",sourceUrl,start,end,generatedAt,reportId,reportPath,appRevision,benchmarkSource;
  public int reportSchemaVersion=1,sourceRows,duplicateRows,omittedDates;public boolean benchmarkAdded;
  public List<String> warnings=new ArrayList<>();public List<Account> accounts=new ArrayList<>();
 }
 public Result analyze(String url,String start,String end)throws IOException {
  String canonical=PublicCsvSource.canonical(url);
  return analyzeCsv(loader.load(canonical),canonical,start,end);
 }
 public Result analyzeCsv(String csv,String source,String startText,String endText)throws IOException {
  if(csv.length()>2*1024*1024)throw new IllegalArgumentException("Input CSV exceeds the 2 MB limit.");
  Result result=new Result();result.sourceUrl=source;result.generatedAt=Instant.now().toString();
  List<String> names;List<NavigableMap<LocalDate,Double>> columns=new ArrayList<>();
  Set<LocalDate> allDates=new TreeSet<>();int benchmark=-1;
  try(CsvListReader reader=new CsvListReader(new StringReader(csv),CsvPreference.STANDARD_PREFERENCE)){
   names=reader.read();if(names==null||names.size()<2||names.size()>11)throw new IllegalArgumentException("Use a Date column followed by 1–10 NAV/price columns.");
   if(!"date".equalsIgnoreCase(names.get(0).replace("\uFEFF","").trim()))throw new IllegalArgumentException("The first CSV column must be Date (YYYY-MM-DD).");
   Set<String> unique=new HashSet<>();
   for(int i=1;i<names.size();i++){
    String name=names.get(i).trim();if(name.isEmpty()||name.length()>80||!unique.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Series names must be unique, nonempty and at most 80 characters.");
    names.set(i,name);columns.add(new TreeMap<>());
    if(isBenchmark(name)){if(benchmark!=-1)throw new IllegalArgumentException("Use only one S&P 500 benchmark column.");benchmark=i-1;}
   }
   List<String> row;
   while((row=reader.read())!=null){
    if(row.stream().allMatch(v->v==null||v.trim().isEmpty()))continue;
    if(++result.sourceRows>10000)throw new IllegalArgumentException("Input exceeds 10,000 daily rows.");
    if(row.size()!=names.size())throw new IllegalArgumentException("Every CSV row must have the same columns as the header (row "+reader.getRowNumber()+").");
    LocalDate date=parseDate(row.get(0));if(!date.isBefore(LocalDate.now(ZoneId.of("America/New_York"))))throw new IllegalArgumentException("Only completed dates before today in New York are supported.");
    if(!allDates.add(date))result.duplicateRows++;
    for(int i=0;i<columns.size();i++){
     Double value=number(row.get(i+1));if(value==null)continue;
     Double previous=columns.get(i).putIfAbsent(date,value);
     if(previous!=null&&Double.compare(previous,value)!=0)throw new IllegalArgumentException("Conflicting values for "+names.get(i+1)+" on "+date+".");
    }
   }
  }
  if(allDates.isEmpty())throw new IllegalArgumentException("No daily observations found.");
  LocalDate start=startText==null||startText.trim().isEmpty()?Collections.min(allDates):parseDate(startText);
  LocalDate end=endText==null||endText.trim().isEmpty()?Collections.max(allDates):parseDate(endText);
  if(end.isBefore(start))throw new IllegalArgumentException("End date must be on or after start date.");
  if(benchmark==-1){
   BuyAndHoldBenchmark.Result downloaded=market.run(start,end,BASE);
   NavigableMap<LocalDate,Double> data=new TreeMap<>();
   for(BuyAndHoldBenchmark.Point p:downloaded.series)data.put(LocalDate.parse(p.date),p.close);
   columns.add(data);names.add("S&P 500");benchmark=columns.size()-1;result.benchmarkAdded=true;result.benchmarkSource=downloaded.source;
  }else result.benchmarkSource="S&P 500 column supplied in the input; no Yahoo benchmark was downloaded.";
  if(columns.size()==1)throw new IllegalArgumentException("Include at least one non-benchmark NAV/price series.");
  NavigableSet<LocalDate> aligned=new TreeSet<>(columns.get(0).keySet());
  for(NavigableMap<LocalDate,Double> data:columns)aligned.retainAll(data.keySet());
  aligned.removeIf(date->date.isBefore(start)||date.isAfter(end));
  if(aligned.size()<2)throw new IllegalArgumentException("At least two common dates with a value in every series are required.");
  result.start=aligned.first().toString();result.end=aligned.last().toString();
  int requestedDates=0;for(LocalDate date:allDates)if(!date.isBefore(start)&&!date.isAfter(end))requestedDates++;
  result.omittedDates=requestedDates-aligned.size();
  result.warnings.add("Input columns are NAV/price levels, not daily returns. Deposits and withdrawals are not inferred or removed. Use unitized NAV or cash-flow-adjusted levels.");
  result.warnings.add("Comparisons use only common observed dates; blanks are not forward-filled. Daily volatility/Sharpe assume trading-session observations and annualize by sqrt(252).");
  if(result.duplicateRows>0)result.warnings.add(result.duplicateRows+" repeated date rows were merged; conflicting duplicate values are rejected.");
  if(result.omittedDates>0)result.warnings.add(result.omittedDates+" input dates were omitted because one or more series had no observation.");
  NavigableMap<LocalDate,Double> benchmarkValues=columns.get(benchmark);
  BenchmarkLookup lookup=time->{Map.Entry<LocalDate,Double> p=benchmarkValues.floorEntry(time.toLocalDate());return p==null?benchmarkValues.firstEntry().getValue():p.getValue();};
  for(int i=0;i<columns.size();i++){
   Account account=new Account();account.name=names.get(i+1);account.benchmark=i==benchmark;
   account.source=account.benchmark?result.benchmarkSource:"Input NAV/price column";
   account.initialValue=columns.get(i).get(aligned.first());if(account.initialValue<=0)throw new IllegalArgumentException("The first common value must be positive for "+account.name+".");List<String> dates=new ArrayList<>();List<Double> navs=new ArrayList<>();
   double previous=account.initialValue;int large=0;
   for(LocalDate date:aligned){
    double value=columns.get(i).get(date),nav=BASE*value/account.initialValue;
    if(!Double.isFinite(nav))throw new IllegalArgumentException("Series cannot be normalized: "+account.name);
    Point point=new Point();point.date=date.toString();point.value=value;point.nav=nav;account.series.add(point);
    dates.add(point.date);navs.add(nav);
    if(Math.abs(value/previous-1)>.3&&large++<3)result.warnings.add(account.name+" changes by more than 30% on "+date+". Check for deposits, withdrawals, unit changes or input errors; the value has not been altered.");
    previous=value;
   }
   long nonpositive=account.series.stream().filter(p->p.value<=0).count();
   if(nonpositive>0)result.warnings.add(account.name+" contains "+nonpositive+" zero/negative NAV observations. Return, CAGR and risk-adjusted statistics are unavailable. Source values have not been changed.");
   account.statistics=PerformanceStatistics.compute(dates,navs,BASE,new PerformanceStatistics.Values());
   account.performanceMeasures=NavSeriesAnalysis.metrics(dates,navs,BASE,lookup);result.accounts.add(account);
  }
  return result;
 }
 public static boolean isBenchmark(String name){String n=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");return Arrays.asList("sp500","sandp500","gspc","spx","sp500close","sp500nav").contains(n);}
 private static LocalDate parseDate(String value){try{return LocalDate.parse(value.trim());}catch(DateTimeParseException|NullPointerException ex){throw new IllegalArgumentException("Use daily ISO dates: YYYY-MM-DD.");}}
 private static Double number(String text){
  if(text==null||text.trim().isEmpty()||"N/A".equalsIgnoreCase(text.trim())||"NA".equalsIgnoreCase(text.trim()))return null;
  String raw=text.trim();if(raw.startsWith("$"))raw=raw.substring(1).trim();
  if(raw.contains(",")&&!raw.matches("[+-]?[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?"))throw new IllegalArgumentException("Invalid thousands separators in numeric value.");
  try{double value=Double.parseDouble(raw.replace(",",""));if(!Double.isFinite(value))throw new NumberFormatException();return value;}
  catch(NumberFormatException ex){throw new IllegalArgumentException("NAV/price levels must be finite numbers; daily return percentages are not supported.");}
 }
}
