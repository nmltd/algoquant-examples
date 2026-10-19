# CSI300 simulation tutorial

This tutorial demonstrates AlgoQuant's historical-universe and simulation APIs. Its outputs illustrate the workflow and do not establish investment performance.

## Index sources and verified coverage

The demo uses `EastmoneyIndexEODWebDownloader` by default. Select `SinaIndexEODWebDownloader` with `-Dcsi300.indexSource=sina`. Both are Java HTTPS clients provided by the matching AlgoQuant library change. There is no Yahoo index dependency, Python process, cookie or API token in this path.

Before implementation, live requests returned **1,944** CSI300 sessions from **2010-01-04** through **2017-12-29** from each provider, with identical date sets and at most **0.005** index-point close difference. The 2008–2017 Eastmoney response contained 2,434 observations for pre-simulation calibration. Evidence: https://github.com/nmltd/algoquant/actions/runs/39127258230 . The library diagnostic workflow additionally checks the actual Java HTTP clients.

The original demo dates remain 2010-01-01 to 2017-01-01 (exclusive). The separate coverage check includes all of calendar 2017. The demo downloads calibration history starting one year and two weeks before the simulation and validates range endpoints before running any strategy.

Constituent membership and adjusted-price ZIPs still come from `test-data/china/constituent_index_300.csv` and `test-data/china/20070104-20171027/adjust_prices/`. Provider symbols are mapped from the canonical `000300.SS`; the index remains the first product, as required by `IndexAwareOptimizer`. The report benchmark uses these same CSI300 levels. Risk-free rate is explicitly assumed zero. Index close is a price-index level, not a total-return series.

## Run

```sh
mvn -Dexec.mainClass=tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300.CSI300SimulationDemo compile exec:java

# Alternative index source:
mvn -Dexec.mainClass=tech.nmfin.algoquant.demo.model.portfoliooptimization.csi300.CSI300SimulationDemo \
  -Dcsi300.indexSource=sina compile exec:java
```

Override local constituent paths with `-Dcsi300.constituents=/data/membership.csv` and `-Dcsi300.prices=/data/adjust_prices`. The default performs two simulations: buy-and-hold index and monthly equal-weight constituents. Opt into the original **990 optimizer simulations** with `-Dcsi300.fullGrid=true`. The grid runs sequentially to bound memory and preserve ordering. Reports and a PNG are saved under `log/`; add `-Dcsi300.showCharts=true` to open a window.

The two-argument Java constructor accepts a caller-supplied `SequentialCacheFactory<ListedProduct, StockEOD>` to replay a saved authentic index response or use another feed for reproducible tests.

## Dependency

This branch targets the new `tech.nmfin:algoquant-dist:3.3.2-SNAPSHOT` library development version. The new downloaders are introduced by https://github.com/nmltd/algoquant/pull/7 and are not present in 3.3.1. Publish that library build before merging this consumer change. Local verification compiles against the changed library classes plus the existing combined runtime; it does not claim that this new development artifact is already published.

### Reusing index downloads

The demo uses `ChinaIndexEODCacheFactory` for both providers. Completed index histories are stored in `./cache/china-index/eastmoney` or `./cache/china-index/sina` and reused across runs for the same or contained date range. Override the root with `-Dcsi300.indexCache=/path/to/cache`. Wider requests fetch a new history; invalid cache files trigger a download. Empty/failed responses and unfinished current-day histories are not saved. Delete the provider cache directory when you want to pick up historical vendor revisions. The raw `*WebDownloader` classes remain network-only, just like Yahoo's web downloader; Yahoo's disk-first behavior is in its cache factory.
