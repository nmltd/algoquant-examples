package tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.Test;
import static org.junit.Assert.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.SimpleStock;

public class LocalCSI300DataProviderTest {
    private Path csv(String value) throws IOException {
        Path p = Files.createTempFile("csi-index-test", ".csv");
        Files.write(p, value.getBytes(StandardCharsets.UTF_8));
        p.toFile().deleteOnExit();
        return p;
    }
    @Test public void readsPrices() throws Exception {
        assertEquals(2, LocalCSI300DataProvider.readIndex(csv("date,close\n2009-12-01,3000\n2010-04-01,3300\n")).size());
    }
    @Test(expected=IOException.class) public void rejectsEmptyHistory() throws Exception {
        LocalCSI300DataProvider.readIndex(csv("date,close\n"));
    }
    @Test(expected=IOException.class) public void rejectsNonfinitePrice() throws Exception {
        LocalCSI300DataProvider.readIndex(csv("date,close\n2010-01-01,NaN\n"));
    }
    @Test(expected=IOException.class) public void rejectsDuplicateDates() throws Exception {
        LocalCSI300DataProvider.readIndex(csv("date,close\n2010-01-01,1\n2010-01-01,2\n"));
    }
    @Test(expected=IOException.class) public void rejectsWrongHeader() throws Exception {
        LocalCSI300DataProvider.readIndex(csv("date,members\n2010-01-01,1\n"));
    }
    @Test public void missingIndexFailsBeforeConstituents() throws Exception {
        try {
            new LocalCSI300DataProvider("absent-members", "absent-prices", java.nio.file.Paths.get("absent-index.csv"),
                LocalDateTimeInterval.of(LocalDateTime.of(2010,1,1,0,0), LocalDateTime.of(2010,4,1,0,0)));
            fail();
        } catch (IOException ex) { assertTrue(ex.getMessage().contains("csi300.indexCsv")); }
    }
    @Test public void usesValueEqualityAndNeverLooksAhead() throws Exception {
        Path members=csv("2009-12-01,000001.SZ,000002.SZ\n");
        LocalDateTimeInterval interval=LocalDateTimeInterval.of(LocalDateTime.of(2010,1,1,0,0),LocalDateTime.of(2010,4,1,0,0));
        LocalCSI300DataProvider provider=new LocalCSI300DataProvider(members.toString(), "absent-prices",
            csv("date,close\n2009-12-01,3000\n2010-01-04,3100\n2010-01-05,3200\n2010-03-31,3300\n"),interval);
        assertEquals(3100,provider.indexValueAt(LocalDateTime.of(2010,1,5,9,0)),0);
        assertNotNull(provider.getCache(new SimpleStock("000300.SS",Exchange.SS),interval));
        try { provider.indexValueAt(LocalDateTime.of(2010,2,1,0,0)); fail(); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("stale")); }
    }
}
