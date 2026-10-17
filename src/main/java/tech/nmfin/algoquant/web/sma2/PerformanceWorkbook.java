package tech.nmfin.algoquant.web.sma2;
import java.io.*;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import tech.nmfin.algoquant.web.SpreadsheetWorkbook;
import static tech.nmfin.algoquant.web.SpreadsheetWorkbook.*;
/** SMA2 sheet selection over the shared Excel writer. */
final class PerformanceWorkbook {
 static final String MIME=SpreadsheetWorkbook.MIME;
 static byte[] export(SMA2App.Result report)throws IOException {
        String[] names={"SMA2 statistics","Buy and hold statistics","S&P 500 statistics","Daily data"};
        String[] sheets={statistics(report.performanceMeasures),statistics(report.buyHoldPerformanceMeasures),statistics(report.sp500==null?null:report.sp500.performanceMeasures),daily(report)};

  return SpreadsheetWorkbook.export(names,sheets);
 }
    private static String daily(SMA2App.Result report) {
        StringBuilder sheet=start(new int[]{18,23,18,18,18,34,23,23});
        Map<String,tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point> index=new HashMap<>();
        if(report.sp500!=null)for(tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point point:report.sp500.series)index.put(point.date,point);
        header(sheet,"date","adjusted_close","fast_sma","slow_sma","sma2_nav",
            "FULL_EQUITY_V1".equals(report.sizingModel)?"fully_invested_buy_hold_nav":"legacy_one_share_benchmark_nav","sp500_close","sp500_nav");
        int r=1;
        for(SMA2App.Point p:report.series) {
            r++;sheet.append("<row r=\"").append(r).append("\">");
            number(sheet,"A"+r,(double)ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),LocalDate.parse(p.date)),4);
            number(sheet,"B"+r,p.close,2);number(sheet,"C"+r,p.fast,2);number(sheet,"D"+r,p.slow,2);
            number(sheet,"E"+r,p.nav,2);number(sheet,"F"+r,p.benchmark,2);
            // Join the separately simulated index by date, never by array position or forward fill.
            tech.nmfin.algoquant.web.BuyAndHoldBenchmark.Point benchmark=index.get(p.date);
            if(benchmark!=null){number(sheet,"G"+r,benchmark.close,2);number(sheet,"H"+r,benchmark.nav,2);}
            sheet.append("</row>");
        }
        return finish(sheet,"H",r);
    }

}
