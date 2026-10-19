# AlgoQuant 3.3.2

Add general stock/index EOD downloaders for Sina and Eastmoney and share transport and disk caching with Yahoo.

- Accept the requested product and date range in all three downloaders.
- Reuse validated local historical archives before requesting the web, including contained ranges.
- Store prices in the existing CSV ZIP format with a separate range-specific .properties sidecar. Readable filenames show stock, exchange, dates and download time.
- Publish archives and metadata atomically, validate checksums/row counts and keep concurrent downloads consistent.
- Preserve Yahoo cache constructors, filters, adjusted close and legacy archive compatibility. Mainland feeds expose raw/unadjusted prices.
- Log file/web selection, URLs, HTTP status, retries, outcomes, rows, paths and timing through the existing logging system.
- Remove the unreleased index-only wrappers and document cache lookup and provider behavior.

Validation includes 47 focused tests, full unobfuscated/core/Javadoc checks and isolated tests against the obfuscated customer JAR. The examples compatibility survey compiled all 84 main sources and passed all 31 tests. Across 57 entry points, 49 demos completed cleanly, four returned with logged errors/warnings, one failed, one needed credentials, one aggregate timed out, and the web app passed dedicated API/browser/container checks. CSI300 completed both default baselines but logged missing constituent data and NaN correlations. See docs/eod-examples-compatibility.md for details; this release does not claim all model examples produce clean numerical results. Public-provider historical coverage and availability vary.

Java 8 compatibility is retained. Release assets contain the tested obfuscated combined JAR, matching Javadoc JAR, standalone POM and SHA-256 checksums.

Public Maven coordinates: `tech.nmfin:algoquant-dist:3.3.2` from `https://maven.pkg.github.com/nmltd/maven-packages`. Internal coordinates: `tech.nmfin:algoquant:3.3.2`.
