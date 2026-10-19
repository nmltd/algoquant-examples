package tech.nmfin.algoquant.web;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODCacheFactory;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;

/**
 * Shared Yahoo history source for all web applications.
 * Use this source rather than constructing a raw web downloader in a new app.
 * The factory validates local coverage before downloading and writes CSV ZIP archives.
 */
public final class YahooPriceSource implements BuyAndHoldBenchmark.PriceSource {
    private YahooPriceSource() {}

    private static final class Shared {
        static final YahooPriceSource INSTANCE = new YahooPriceSource();
        static final YahooEODCacheFactory CACHE =
                new YahooEODCacheFactory(directory());
    }

    public static YahooPriceSource shared() {
        return Shared.INSTANCE;
    }

    /** Defaults to writable temporary storage, including in the non-root Cloud Run container. */
    private static String directory() {
        String configured = System.getenv("ALGOQUANT_DATA_CACHE_DIRECTORY");
        if (configured != null && !configured.trim().isEmpty()) return configured.trim();
        return Paths.get(System.getProperty("java.io.tmpdir"), "algoquant-market-data").toString();
    }

    @Override
    public List<TimedEntry<StockEOD>> load(Stock stock, LocalDate start, LocalDate end) {
        List<TimedEntry<StockEOD>> bars = new ArrayList<>();
        // The Yahoo factory normalizes the inclusive end date to the exchange close.
        // Its disk layer logs file/web selection, coverage, archive paths and save outcomes.
        for (TimedEntry<StockEOD> bar : Shared.CACHE.newInstance(stock,
                LocalDateTimeInterval.of(start.atStartOfDay(), end.atTime(23, 59)))) {
            if (!bar.time().toLocalDate().isAfter(end)) bars.add(bar);
        }
        return Collections.unmodifiableList(bars);
    }
}
