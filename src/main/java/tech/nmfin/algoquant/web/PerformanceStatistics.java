package tech.nmfin.algoquant.web;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Shared headline conventions for simulated and imported NAV series. */
public final class PerformanceStatistics {
    public static class Values {
        public double finalNav, pnl, maxDrawdown;
        public Double totalReturn, annualizedVolatility, sharpe, cagr;
    }

    public static <T extends Values> T compute(
            List<String> dates, List<Double> nav, double capital, T stats) {
        boolean positive = nav.stream().allMatch(v -> v > 0);
        double previous = capital, peak = capital, mean = 0, sum = 0;
        List<Double> returns = dailyReturns(nav, capital);
        for (double value : nav) {
            peak = Math.max(peak, value);
            stats.maxDrawdown = Math.max(stats.maxDrawdown, (peak - value) / peak);
            previous = value;
        }
        stats.finalNav = previous;
        stats.pnl = previous - capital;
        stats.totalReturn = positive ? previous / capital - 1 : null;
        for (double r : returns) mean += r;
        mean /= returns.size();
        for (double r : returns) sum += (r - mean) * (r - mean);
        double sd = Math.sqrt(sum / (returns.size() - 1));
        if (positive && Double.isFinite(sd)) stats.annualizedVolatility = sd * Math.sqrt(252);
        if (positive && sd > 0 && Double.isFinite(mean / sd))
            stats.sharpe = mean / sd * Math.sqrt(252);
        long days =
                ChronoUnit.DAYS.between(
                        LocalDate.parse(dates.get(0)),
                        LocalDate.parse(dates.get(dates.size() - 1)));
        if (positive && days > 0 && previous > 0) {
            double annual = Math.pow(previous / capital, 365.2425 / days) - 1;
            if (Double.isFinite(annual)) stats.cagr = annual;
        }
        return stats;
    }

    /**
     * Observation returns include the first NAV relative to the starting capital, matching
     * simulation reports.
     */
    public static List<Double> dailyReturns(List<Double> nav, double capital) {
        List<Double> returns = new ArrayList<>();
        double previous = capital;
        for (double value : nav) {
            returns.add(value / previous - 1);
            previous = value;
        }
        return returns;
    }
}
