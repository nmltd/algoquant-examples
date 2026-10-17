package tech.nmfin.algoquant.web;

import java.time.*;
import java.util.*;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import org.junit.*;
import static org.junit.Assert.*;

public class BuyAndHoldBenchmarkTest {
    @BeforeClass public static void license(){TestLicense.load();}
    @Test public void independentAccountInvestsAllCapitalAndTracksIndex() {
        BuyAndHoldBenchmark benchmark=new BuyAndHoldBenchmark((stock,start,end)->{
            assertEquals("^GSPC",stock.symbol());
            List<TimedEntry<StockEOD>> bars=new ArrayList<>();
            int[] days={2,3,6};double[] prices={100,110,99};
            for(int i=0;i<days.length;i++){double p=prices[i];bars.add(new TimedEntry<>(LocalDate.of(2020,1,days[i]).atTime(16,0),new StockEOD(p,p,p,p,1000,p)));}
            return bars;
        });
        BuyAndHoldBenchmark.Result r=benchmark.run(LocalDate.of(2020,1,1),LocalDate.of(2020,1,7),10000);
        assertEquals(1,r.executions.size());assertEquals(100,r.executions.get(0).quantity,1e-10);
        assertEquals(9900,r.statistics.finalNav,1e-8);assertEquals(-.01,r.statistics.totalReturn,1e-10);
        assertEquals(.1,r.statistics.maxDrawdown,1e-10);
        for(BuyAndHoldBenchmark.Point point:r.series)assertEquals(point.close/100,point.nav/10000,1e-10);
        assertEquals(187,r.performanceMeasures.size());
        assertEquals(100,r.lookup.valueAt(LocalDateTime.of(2020,1,2,17,0)),1e-10);
        BuyAndHoldBenchmark.Result doubled=benchmark.run(LocalDate.of(2020,1,1),LocalDate.of(2020,1,7),20000);
        assertEquals(19800,doubled.statistics.finalNav,1e-8);
        assertEquals(r.statistics.totalReturn,doubled.statistics.totalReturn,1e-10);
    }
    @Test public void missingOrInvalidIndexDataCannotProduceAPartialComparison() {
        try {new BuyAndHoldBenchmark((s,a,b)->Collections.emptyList()).run(LocalDate.of(2020,1,1),LocalDate.of(2020,2,1),10000);fail();}
        catch(IllegalArgumentException expected){}
        try {new BuyAndHoldBenchmark((s,a,b)->{throw new IllegalStateException("provider unavailable");}).run(LocalDate.of(2020,1,1),LocalDate.of(2020,2,1),10000);fail();}
        catch(IllegalStateException expected){}
    }
}
