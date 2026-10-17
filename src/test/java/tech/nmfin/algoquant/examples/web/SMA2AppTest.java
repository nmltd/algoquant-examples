package tech.nmfin.algoquant.examples.web;

import dev.nm.misc.license.License;
import java.io.File;
import org.junit.*;
import static org.junit.Assert.*;

public class SMA2AppTest {
    @BeforeClass public static void license() {
        License.setLicenseFile(new File(System.getenv().getOrDefault("NM_LICENSE_FILE", "license/nm.lic")));
    }
    @Test public void invalidInputsAreRejected() {
        for (String[] p : new String[][]{{"BAD","2020-01-01","2021-01-01","10","30"},
            {"AAPL","2021-01-01","2020-01-01","10","30"},{"AAPL","2020-01-01","2021-01-01","30","10"}}) {
            try { new SMA2App.Request(p[0],p[1],p[2],Integer.parseInt(p[3]),Integer.parseInt(p[4])); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void extendingTheEndDateDoesNotChangeEarlierResults() {
        SMA2App service = new SMA2App(true);
        SMA2App.Result a=service.run(new SMA2App.Request("AAPL","2020-01-01","2020-06-30",5,20));
        SMA2App.Result b=service.run(new SMA2App.Request("AAPL","2020-01-01","2020-12-31",5,20));
        assertTrue(a.executions.size()>2);
        for(int i=0;i<a.series.size();i++) {
            assertEquals(a.series.get(i).date,b.series.get(i).date);
            assertEquals(a.series.get(i).nav,b.series.get(i).nav,1e-8);
        }
    }
    @Test public void navMatchesIndependentExecutionLedger() {
        SMA2App.Result r=new SMA2App(true).run(new SMA2App.Request("AAPL","2020-01-01","2020-12-31",5,20));
        double cash=10000,position=0; int next=0;
        for(SMA2App.Point p:r.series) {
            while(next<r.executions.size() && r.executions.get(next).date.substring(0,10).compareTo(p.date)<=0) {
                SMA2App.Fill f=r.executions.get(next++);
                double qty=f.side.equals("BUY")?f.quantity:-f.quantity;
                cash-=qty*f.price;position+=qty;
                assertEquals(1.,Math.abs(position),1e-8);
                assertEquals(p.close,f.price,1e-8);
            }
            assertEquals(cash+position*p.close,p.nav,1e-7);
        }
        assertEquals(r.finalNav-10000,r.pnl,1e-8);
        assertTrue(Double.isFinite(r.sharpe));
    }
    @Test public void executionsFollowThePreviousSessionsCrossover() {
        SMA2App.Result r=new SMA2App(true).run(new SMA2App.Request("AAPL","2020-01-01","2020-12-31",5,20));
        int checked=0;
        for(SMA2App.Fill fill:r.executions) {
            for(int i=2;i<r.series.size();i++) if(fill.date.startsWith(r.series.get(i).date)) {
                SMA2App.Point previous=r.series.get(i-1), before=r.series.get(i-2);
                double signal=previous.fast-previous.slow, old=before.fast-before.slow;
                assertTrue("Fill must follow yesterday's crossover",signal*old<=0);
                assertEquals(signal>0 ? "BUY" : "SELL",fill.side);
                checked++;
            }
        }
        assertTrue(checked>2);
    }

    @Test public void comprehensiveLibraryResultsAreSerializableAndMatchLedger() {
        SMA2App.Result r=new SMA2App(true).run(new SMA2App.Request("AAPL","2020-01-01","2020-12-31",5,20));
        assertTrue(r.performanceMeasures.size()>150);
        assertEquals(12,r.periodMeasures.size());
        java.util.Map<String,AllPerformanceMeasures.Metric> byClass=new java.util.HashMap<>();
        for(AllPerformanceMeasures.Metric m:r.performanceMeasures) {
            assertNotNull(m.label);assertNotNull(m.sourceClass);assertNotNull(m.status);
            if(m.value!=null) assertTrue(Double.isFinite(m.value));
            byClass.put(m.sourceClass,m);
        }
        assertEquals(r.pnl,byClass.get("ProfitLoss").value,1e-7);
        assertEquals(r.finalNav,byClass.get("EndBalance").value,1e-7);
        assertEquals((double)r.executions.size(),byClass.get("ExecutionCount").value,0);
        for(String required:new String[]{"SortinoRatio","AlphaCAPM","BetaCAPM","RSquaredCAPM",
                "TreynorRatio","HistoricalVaR","AnalyticVaR","ConditionalVaR","PerTradeStat",
                "RollingAnnualReturnAverage","RollingAnnualReturnMin","RollingAnnualReturnMax"})
            assertTrue("Missing "+required,byClass.containsKey(required));
        for(String name:java.util.Arrays.asList("AlphaCAPM","AverageAnnualRateOfReturn","BenchmarkCorrelation","BetaCAPM","CalmarRatio","Commission","CommissionProfitRatio","CumulativeReturn","DividendAdjustedProfitLoss","DownsideDeviation","EndBalance","ExecutionCount","LinearlyCombinedPerformanceMeasure","MaxDrawdown","MaxDrawdownDuration","MaxDrawdownPercentage","MaxExposure","PeriodReturnsAverage","PeriodReturnsBest","PeriodReturnsCompound","PeriodReturnsNegative","PeriodReturnsPositive","PeriodReturnsStdev","PeriodReturnsWorst","ProfitAfterTransactionFee","ProfitLoss","ProfitLossRealized","ProfitNoTransactionFee","ProfitOverMaxExposure","RSquaredCAPM","RollingAnnualReturnAverage","RollingAnnualReturnMax","RollingAnnualReturnMin","SharpeRatioMonthlyAnnualized","SortinoRatio","SortinoRatioMonthlyAnnualized","StartBalance","SumReturn","TransactionFeeByContract","TransactionFeeByPercentage","TreynorRatio","WinLossRatio","WinLossSizeRatio","PartialPerformanceMeasure","InformationRatioForPeriods","InformationRatioForTrades","InformationRatioForZeroInvestment","OmegaForPeriods","OmegaForTrades","PerTradePairReturn","PerTradeStat","AnalyticVaR"))
            assertTrue("Unrepresented concrete library measure: "+name,byClass.containsKey(name));
        assertEquals("not_applicable",byClass.get("PerTradePairReturn").status);
        assertEquals("not_applicable",byClass.get("DividendAdjustedProfitLoss").status);
        String json=new com.google.gson.Gson().toJson(r);
        new com.google.gson.JsonParser().parse(json);
        for(AllPerformanceMeasures.PeriodTable t:r.periodMeasures)
            for(AllPerformanceMeasures.PeriodValue p:t.values)
                if(p.value!=null)assertTrue(Double.isFinite(p.value));
    }
    @Test public void shortPeriodKeepsUndefinedStatisticsWithoutBreakingReport() {
        SMA2App.Result r=new SMA2App(true).run(new SMA2App.Request("AAPL","2020-01-02","2020-01-10",5,20));
        assertTrue(r.series.size()>1);
        assertTrue(r.performanceMeasures.stream().anyMatch(m->!"ok".equals(m.status)));
        for(AllPerformanceMeasures.Metric m:r.performanceMeasures)
            if(m.value!=null)assertTrue(Double.isFinite(m.value));
        new com.google.gson.JsonParser().parse(new com.google.gson.Gson().toJson(r));
    }


    @Test public void longHistoryComputesRollingAndBenchmarkMeasures() {
        SMA2App.Result r=new SMA2App(true).run(new SMA2App.Request("AAPL","2016-01-01","2020-12-31",5,20));
        for(AllPerformanceMeasures.Metric m:r.performanceMeasures) {
            assertFalse(m.label+": "+m.detail,"unavailable".equals(m.status));
            if(m.sourceClass.startsWith("RollingAnnualReturn") || m.sourceClass.equals("TreynorRatio"))
                assertEquals(m.label+": "+m.detail,"ok",m.status);
        }
        for(AllPerformanceMeasures.PeriodTable t:r.periodMeasures)
            assertFalse(t.label+": "+t.detail,"unavailable".equals(t.status));
    }
}
