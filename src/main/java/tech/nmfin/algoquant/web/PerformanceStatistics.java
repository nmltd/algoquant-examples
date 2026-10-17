package tech.nmfin.algoquant.web;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
/** Shared headline conventions for simulated and imported NAV series. */
public final class PerformanceStatistics {
 public static class Values {public double finalNav,pnl,totalReturn,maxDrawdown;public Double annualizedVolatility,sharpe,cagr;}
 public static <T extends Values> T compute(List<String> dates,List<Double> nav,double capital,T stats) {
  double previous=capital,peak=capital,mean=0,sum=0;List<Double> returns=new ArrayList<>();
  for(double value:nav){peak=Math.max(peak,value);stats.maxDrawdown=Math.max(stats.maxDrawdown,(peak-value)/peak);double r=value/previous-1;returns.add(r);mean+=r;previous=value;}
  stats.finalNav=previous;stats.pnl=previous-capital;stats.totalReturn=previous/capital-1;mean/=returns.size();
  for(double r:returns)sum+=(r-mean)*(r-mean);double sd=Math.sqrt(sum/(returns.size()-1));
  if(Double.isFinite(sd))stats.annualizedVolatility=sd*Math.sqrt(252);
  if(sd>0&&Double.isFinite(mean/sd))stats.sharpe=mean/sd*Math.sqrt(252);
  long days=ChronoUnit.DAYS.between(LocalDate.parse(dates.get(0)),LocalDate.parse(dates.get(dates.size()-1)));
  if(days>0&&previous>0){double annual=Math.pow(previous/capital,365.2425/days)-1;if(Double.isFinite(annual))stats.cagr=annual;}
  return stats;
 }
}
