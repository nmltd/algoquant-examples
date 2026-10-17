package tech.nmfin.algoquant.web;
import java.time.*;import java.util.*;import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import tech.nmfin.algoquant.data.cache.*;import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
/** Shared NAV-only analysis: do not manufacture trades, fees or positions from account values. */
public final class NavSeriesAnalysis {
 public static List<AllPerformanceMeasures.Metric> metrics(List<String> dates,List<Double> values,double capital,BenchmarkLookup benchmark) {
  LocalDate first=LocalDate.parse(dates.get(0)),last=LocalDate.parse(dates.get(dates.size()-1));
  LocalDateTimeInterval interval=LocalDateTimeInterval.of(first.atStartOfDay(),last.plusDays(1).atStartOfDay());
  BufferCache<Double> nav=new BufferCache<>();nav.add(interval.start(),capital);double previous=capital;
  double[] returns=new double[values.size()];
  for(int i=0;i<values.size();i++){nav.add(LocalDate.parse(dates.get(i)).atTime(16,0),values.get(i));returns[i]=values.get(i)/previous-1;previous=values.get(i);}
  nav.add(interval.end(),previous);
  AllPerformanceMeasures measures=new AllPerformanceMeasures(interval,capital,nav,Collections.emptyList(),new OrderBookCaches(),benchmark,null,returns);
  measures.computeForNavSeries();
  for(AllPerformanceMeasures.Metric m:measures.metrics)if(m.unit!=null&&m.unit.startsWith("USD")){m.unit=m.unit.replace("USD","normalized units");m.detail+=" Values use an initial normalized account of 10,000, not the source currency balance.";}
  return measures.metrics;
 }
}
