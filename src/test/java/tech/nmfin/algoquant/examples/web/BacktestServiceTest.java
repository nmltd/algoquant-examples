package tech.nmfin.algoquant.examples.web;

import dev.nm.misc.license.License;
import java.io.File;
import org.junit.*;
import static org.junit.Assert.*;

public class BacktestServiceTest {
    @BeforeClass public static void license() {
        License.setLicenseFile(new File(System.getenv().getOrDefault("NM_LICENSE_FILE", "license/nm.lic")));
    }
    @Test public void invalidInputsAreRejected() {
        for (String[] p : new String[][]{{"BAD","2020-01-01","2021-01-01","10","30"},
            {"AAPL","2021-01-01","2020-01-01","10","30"},{"AAPL","2020-01-01","2021-01-01","30","10"}}) {
            try { new BacktestService.Request(p[0],p[1],p[2],Integer.parseInt(p[3]),Integer.parseInt(p[4])); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void extendingTheEndDateDoesNotChangeEarlierResults() {
        BacktestService service = new BacktestService(true);
        BacktestService.Result a=service.run(new BacktestService.Request("AAPL","2020-01-01","2020-06-30",5,20));
        BacktestService.Result b=service.run(new BacktestService.Request("AAPL","2020-01-01","2020-12-31",5,20));
        assertTrue(a.executions.size()>2);
        for(int i=0;i<a.series.size();i++) {
            assertEquals(a.series.get(i).date,b.series.get(i).date);
            assertEquals(a.series.get(i).nav,b.series.get(i).nav,1e-8);
        }
    }
    @Test public void navMatchesIndependentExecutionLedger() {
        BacktestService.Result r=new BacktestService(true).run(new BacktestService.Request("AAPL","2020-01-01","2020-12-31",5,20));
        double cash=10000,position=0; int next=0;
        for(BacktestService.Point p:r.series) {
            while(next<r.executions.size() && r.executions.get(next).date.substring(0,10).compareTo(p.date)<=0) {
                BacktestService.Fill f=r.executions.get(next++);
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
}
