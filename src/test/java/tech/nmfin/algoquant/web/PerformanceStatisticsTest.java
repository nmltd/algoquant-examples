package tech.nmfin.algoquant.web;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PerformanceStatisticsTest {
    @Test
    public void sharedConventionsMatchKnownNavPath() {
        List<Double> nav = Arrays.asList(100., 110., 99.);
        List<Double> returns = PerformanceStatistics.dailyReturns(nav, 100);
        assertEquals(0., returns.get(0), 1e-12);
        assertEquals(.1, returns.get(1), 1e-12);
        assertEquals(-.1, returns.get(2), 1e-12);
        PerformanceStatistics.Values s =
                PerformanceStatistics.compute(
                        Arrays.asList("2020-01-01", "2020-01-02", "2020-01-03"),
                        nav,
                        100,
                        new PerformanceStatistics.Values());
        assertEquals(99, s.finalNav, 1e-12);
        assertEquals(-1, s.pnl, 1e-12);
        assertEquals(-.01, s.totalReturn, 1e-12);
        assertEquals(.1, s.maxDrawdown, 1e-12);
        assertEquals(.1 * Math.sqrt(252), s.annualizedVolatility, 1e-12);
        assertEquals(0, s.sharpe, 1e-12);
    }

    @Test
    public void nonpositivePathsKeepLevelsAndSuppressReturnStatistics() {
        PerformanceStatistics.Values s =
                PerformanceStatistics.compute(
                        Arrays.asList("2020-01-01", "2020-01-02", "2020-01-03"),
                        Arrays.asList(100., 0., 110.),
                        100,
                        new PerformanceStatistics.Values());
        assertEquals(110, s.finalNav, 1e-12);
        assertEquals(1, s.maxDrawdown, 1e-12);
        assertNull(s.totalReturn);
        assertNull(s.sharpe);
        assertNull(s.cagr);
        assertNull(s.annualizedVolatility);
    }
}
