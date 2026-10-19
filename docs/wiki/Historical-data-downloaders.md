# Yahoo, Sina and Eastmoney stock/index history — AlgoQuant 3.3.2

## Released usage and disk lookup

Available in [AlgoQuant 3.3.2](https://github.com/nmltd/algoquant/releases/tag/v3.3.2), public coordinate `tech.nmfin:algoquant-dist:3.3.2`. These are general stock/index downloaders. For example, use `new SimpleStock("600000.SS", Exchange.SS)` for a Shanghai stock or `new SimpleStock("000300.SS", Exchange.SS)` for CSI300, and supply the requested date interval to `newInstance(product, dates)`.

Wrap a feed with `new DiskCachedEODFactory("./cache/eod", new EastmoneyEODWebDownloader())`. Substitute Sina or Yahoo to select another provider. The web downloaders themselves do not own the disk cache.

For each request the shared factory:

1. Selects the provider/price-mode and product/exchange directory.
2. Reads range-specific `.properties` sidecars and looks for one requested interval that contains the new request.
3. Checks provider/product identity, archive name, row count and SHA-256, then reads the existing CSV ZIP and filters it to the requested interval.
4. Returns the local observations without HTTP when that archive is usable.
5. Otherwise downloads the complete requested range, logs URLs/status, and writes an immutable, readable CSV ZIP followed by its atomic sidecar.

An expanded request is downloaded afresh; overlapping archives are not merged and only missing gaps are not downloaded separately. Failed/empty downloads and unfinished current-day ranges are not persisted. One sidecar belongs to one requested range, rather than being shared across all stocks/downloads. Concurrent identical requests use distinct archive names and atomically replace the range sidecar; its referenced archive always matches its metadata. The checksum is metadata only, never part of filenames.

[CSI300 tutorial](https://github.com/nmltd/algoquant-examples/wiki/Model-CSI300SimulationDemo) · [Compatibility report](https://github.com/nmltd/algoquant/blob/main/docs/eod-examples-compatibility.md). The completed CSI300 run had missing constituent data and NaN correlation warnings; provider coverage does not guarantee a complete or numerically valid model result.

`SinaEODWebDownloader` and `EastmoneyEODWebDownloader` implement the same `SequentialCacheFactory<ListedProduct, StockEOD>` contract as `YahooEODWebDownloader`.

```java
ListedProduct csi300 = new SimpleStock("000300.SS", Exchange.SS);
LocalDateTimeInterval dates = LocalDateTimeInterval.of(
    LocalDateTime.of(2010, 1, 1, 0, 0),
    LocalDateTime.of(2017, 12, 31, 0, 0));
SequentialCache<StockEOD> history =
    new EastmoneyEODWebDownloader().newInstance(csi300, dates);
// Alternative: new SinaEODWebDownloader().newInstance(csi300, dates)
```

Imports are under `tech.nmfin.algoquant.data.historicaldata.china`, plus the existing cache/product/time packages.

Both classes use HTTPS, bounded connect/read timeouts, no API token, and no Python or JavaScript runtime. They accept mainland stocks and price indices; individual-stock prices are unadjusted (not dividend/split-adjusted). For indices, their `adjClose` is explicitly the index close, not an equity dividend adjustment or a total-return index. Eastmoney requests `fqt=0` and splits the requested dates into calendar-year calls. Sina requests up to 10,000 daily bars and filters to the requested interval; older history may be unavailable or limited by the provider. Neither class silently fills missing sessions or writes local files. Dates use the supplied exchange closing time and include the requested end trading date, matching the Yahoo downloader's documented daily convention.

Sina identifies Shanghai indices as `sh000300` and Shenzhen indices as `sz399300`; public callers retain the existing `000300.SS` / `399300.SZ` symbols. Eastmoney maps these to `1.000300` / `0.399300`. Eastmoney volume is converted from lots to shares (100 per lot); Sina's share volume is retained. Vendor volume values can differ slightly; the index level is the benchmark input.

The new six-argument `CSI300DataProvider` constructor accepts either index downloader while retaining local constituent prices. The original five-argument constructor keeps its Yahoo behavior. Successful index histories are materialized once and expanded on demand; errors preserve the original cause and do not poison the cache.

## Coverage verified before implementation

Live probe: https://github.com/nmltd/algoquant/actions/runs/39127258230

- Both sources returned exactly **1,944** CSI300 sessions for 2010-01-01 through 2017-12-31.
- First session: **2010-01-04**. Last session: **2017-12-29**.
- Identical date sets; maximum absolute close difference **0.005000000000109139** index points, consistent with Sina's three-decimal and Eastmoney's two-decimal output.
- Eastmoney's wider 2008–2017 response contained 2,434 rows, providing calibration history before the simulation.
- The Sina quarterly HTML pages ignored the old date parameters in this probe. The implementation uses the verified JSON daily-bar service instead.

Raw responses are retained in the diagnostic run artifact. Three genuine sample rows from each source are retained as offline parser fixtures, with 14 offline regression tests. Java parsing of the full captured histories independently reproduced the 1,944-session/0.005-point cross-check. Run `scripts/ChinaIndexCoverageCheck.java` for an opt-in live check using the actual Java transports; the diagnostic workflow compiles it and saves normalized CSVs.

The source discovery reference is AKShare's maintained implementation: https://github.com/akfamily/akshare/blob/main/akshare/index/index_stock_zh.py . The Java adapters use the provider HTTP services directly, with independently written parsers. Public availability does not grant redistribution rights for a complete vendor dataset; full histories remain generated artifacts rather than source fixtures.

The Java live-client cross-check passed in https://github.com/nmltd/algoquant/actions/runs/39127789885 . Full core verification also passed in run 39127789822 before the additional holiday-only empty-response regression was added.

## Persistent local cache

As with Yahoo, the `*WebDownloader` classes perform HTTP requests; caching belongs to a separate factory. Use `new DiskCachedEODFactory("./cache/china-index", new EastmoneyEODWebDownloader())` or wrap `new SinaEODWebDownloader()` in place of the web downloader. The CSI300 demo now does this by default, with `-Dcsi300.indexCache=/path/to/cache` to change the directory.

The factory checks disk first and reuses a successfully saved historical interval for identical or contained requests across program runs. Provider directories and product/exchange identities are separate. Dates are recorded explicitly rather than inferred from the first/last trading session, so weekends do not cause needless downloads. Wider intervals are downloaded afresh. Missing or corrupt files fall back to HTTP; downloads are materialized and validated before atomic publication of a CSV ZIP archive and its metadata sidecar. Failed or empty responses, and intervals extending through today, are not persisted. Completed historical data is reused until its cache directory is deleted to refresh provider revisions. Disk-write failures log a warning and still return the downloaded history.

Seven additional cache regression tests cover both providers, reuse with the network disabled, contained requests, provider isolation, expansion, corrupt files, failure/empty retries, holidays and current-date freshness. All 19 parser/cache tests passed on Java 8; the updated demo compiled against the changed classes and released 3.3.1 runtime.

## Shared downloader and disk-cache hierarchy

`AbstractEODWebDownloader` implements `SequentialCacheFactory<ListedProduct, StockEOD>` and owns bounded HTTP connections, request headers, response reading, cleanup and contextual failures. `YahooEODWebDownloader`, `SinaEODWebDownloader` and `EastmoneyEODWebDownloader` inherit it. Each request supplies its product and interval. URL/symbol construction, price parsing and Yahoo's two-host retry policy remain provider-specific. The general mainland classes are under `historicaldata.china`. There are no separate index downloader or index-cache wrappers.

`DiskCachedEODFactory` wraps any of these feeds. It preserves all six StockEOD fields, including separate Yahoo adjusted close and unknown volume/adjusted-close values. Its source identifier includes the price mode so providers and future adjustment modes cannot contaminate one another. Source identifiers are `yahoo`, `sina-unadjusted`, and `eastmoney-unadjusted`.

`YahooEODCacheFactory` keeps its existing constructors and filtering, delegates cache decisions to `DiskCachedEODFactory`, and supplies an adapter for legacy `<symbol>.csv.zip` files. Existing Yahoo ZIP input/output remains available. Legacy ZIPs lack requested-range metadata, so they must conservatively cover both weekday boundaries; a holiday boundary can require one fresh download before the new metadata-based cache avoids repeats. The compatibility reader uses existing local holiday archives when present and performs no holiday-calendar HTTP request. Explicit input ZIPs take priority over previously downloaded output histories. The managed cache does not persist unfinished current-day histories; Yahoo's legacy ZIP export retains its existing behavior.

Mainland stock requests use `600000.SS` / `000001.SZ`, just like the index examples supply `000300.SS`. Sina uses daily raw OHLCV; Eastmoney uses `fqt=0`. For these stock feeds, `adjClose()` equals unadjusted close. Use Yahoo's actual adjusted-close series or a separately implemented adjustment source when corporate-action-adjusted returns are required. The live diagnostic now tests both Shanghai and Shenzhen stocks for 2010–2017 and verifies all downloaded fields are reused in a second JVM with web requests forbidden.

The refactor passed 39 focused Java 8 regressions, plus the two original Yahoo archive regression cases that exercise explicit input precedence and a holiday boundary. Live verification run https://github.com/nmltd/algoquant/actions/runs/39182124204 confirmed 2,013 AAPL sessions and identical 1,897 Shanghai / 1,874 Shenzhen session histories from both mainland providers over 2010–2017. A separate JVM reloaded all five saved stock histories with HTTP forbidden and every field unchanged. CSI300 coverage remained 1,944 identical session dates. Eastmoney stock requests include its public web-client routing parameter; transient connection/server/rate-limit failures receive bounded retries, while parsing/provider errors fail immediately.

## On-disk format

Price data uses the existing CSV ZIP reader/writer and the exact header `timestamp,open,high,low,close,volume,adjClose`; timestamps are trading dates, restored at the supplied exchange closing time. Each archive has a `.properties` sidecar containing schema version, provider/price mode, product/exchange identity, requested start/end, archive filename, row count, and SHA-256. No price rows are stored in JSON or in the sidecar. Missing, corrupt or mismatched data/metadata triggers a fresh download. Archive names are immutable; the sidecar is published atomically last so interrupted or concurrent writes cannot pair metadata with different prices. The raw provider responses remain JSON in transit only.

Cache directory and filenames are human-readable, without hashes or UUIDs. For example, under `eastmoney-unadjusted/000300.SS__SS/`:

- `000300.SS__2010-01-01T00-00__2017-12-31T00-00__downloaded-2026-10-19T03-25-10.123456789Z.csv.zip`
- `000300.SS__2010-01-01T00-00__2017-12-31T00-00.properties`

The ZIP name includes the stock symbol, requested start/end and UTC download timestamp. The sidecar references that exact ZIP. Concurrent downloads reserve distinct archive names atomically; a numeric suffix resolves a timestamp collision. The final sidecar is replaced atomically. Unsafe filename characters in unusual symbols are escaped reversibly; ordinary stock/index symbols remain unchanged. SHA-256 is used only inside metadata to verify archive integrity, never in directory or file names.

## Logging

The feeds and shared disk cache use the existing SLF4J/logback configuration and appenders; no separate log files or logging configuration are introduced. INFO reports file versus web selection, provider, product, requested/covered intervals, complete web request URLs, actual HTTP response status, completion/empty result, row counts, first/last observations, elapsed milliseconds, and saved CSV ZIP/sidecar paths. WARN reports retry attempt/limit/backoff, failed HTTP attempts, invalid/missing cache components and failed cache writes. Final download failures are logged at ERROR with the original exception. DEBUG reports cache lookup paths and why persistence was skipped. Response bodies and individual price rows are not logged.

Logs can therefore distinguish `source=file ... status=success` from `source=web ... status=selected`, follow each URL through retries and HTTP status, and locate the resulting cache files. Tests capture the existing logback events to verify file hits make no HTTP request and success/failure/retry records contain the expected diagnostic fields.
