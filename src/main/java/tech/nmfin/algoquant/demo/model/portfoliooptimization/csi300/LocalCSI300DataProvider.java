package tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import tech.nmfin.algoquant.data.cache.BufferCache;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.historicaldata.china.zqf.stock.csi300.CSI300DataProvider;
import tech.nmfin.algoquant.data.historicaldata.china.zqf.stock.csi300.CsvzipDataCacheProvider;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.SimpleStock;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;

/** Local historical inputs for the CSI300 tutorial; never falls back to a web service. */
final class LocalCSI300DataProvider extends CSI300DataProvider {
    private static final Stock INDEX = new SimpleStock("000300.SS", Exchange.SS);
    private final CsvzipDataCacheProvider prices;
    private final NavigableMap<LocalDateTime, Double> index;

    LocalCSI300DataProvider(String constituents, String priceFolder, Path indexCsv,
                           LocalDateTimeInterval interval) throws IOException {
        this(constituents, priceFolder, interval, readIndex(indexCsv));
    }

    private LocalCSI300DataProvider(String constituents, String priceFolder,
                                   LocalDateTimeInterval interval,
                                   NavigableMap<LocalDateTime, Double> index) throws IOException {
        super(constituents, priceFolder, true, interval, false);
        this.prices = new CsvzipDataCacheProvider(priceFolder);
        this.index = index;
        LocalDateTime requiredStart = Boolean.getBoolean("csi300.fullGrid")
            ? interval.start().minusYears(1).minusWeeks(2)
            : interval.start().minusWeeks(3);
        if (index.firstKey().isAfter(requiredStart.plusDays(10))
                || index.lastKey().isBefore(interval.end().minusDays(10))) {
            throw new IOException("CSI300 index CSV must cover calibration and simulation: "
                + requiredStart + " to " + interval.end() + "; actual "
                + index.firstKey() + " to " + index.lastKey());
        }
    }

    static NavigableMap<LocalDateTime, Double> readIndex(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing genuine CSI300 index history: " + file
                + ". Supply -Dcsi300.indexCsv=/path/to/index.csv (date,close; ISO dates)."
                + " Constituent membership is not index price data.");
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !"date,close".equals(lines.get(0).replace("\uFEFF", "").trim())) {
            throw new IOException("CSI300 index CSV requires header date,close: " + file);
        }
        NavigableMap<LocalDateTime, Double> result = new TreeMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) continue;
            try {
                String[] cells = lines.get(i).split(",", -1);
                if (cells.length != 2) throw new IllegalArgumentException("expected two columns");
                LocalDateTime time = LocalDate.parse(cells[0].trim()).atTime(Exchange.SS.closeTime());
                double close = Double.parseDouble(cells[1].trim());
                if (!Double.isFinite(close) || close <= 0) throw new IllegalArgumentException("invalid close");
                if (!result.isEmpty() && !time.isAfter(result.lastKey())) {
                    throw new IllegalArgumentException("dates must be strictly increasing");
                }
                result.put(time, close);
            } catch (RuntimeException ex) {
                throw new IOException("Invalid CSI300 index CSV row " + (i + 1) + " in " + file, ex);
            }
        }
        if (result.size() < 2) throw new IOException("CSI300 index CSV needs at least two observations: " + file);
        return result;
    }

    double indexValueAt(LocalDateTime time) {
        Map.Entry<LocalDateTime, Double> entry = index.floorEntry(time);
        if (entry == null || entry.getKey().isBefore(time.minusDays(10))) {
            throw new IllegalStateException("Missing or stale CSI300 index observation at " + time);
        }
        return entry.getValue();
    }

    @Override
    public SequentialCache<Double> getCache(Stock product, LocalDateTimeInterval interval) {
        if (!INDEX.equals(product)) return prices.getCache(product, interval);
        BufferCache<Double> cache = new BufferCache<>();
        index.subMap(interval.start(), true, interval.end(), true).forEach(cache::add);
        if (cache.size() < 2) throw new IllegalStateException("Insufficient CSI300 index history for " + interval);
        return cache;
    }
}
