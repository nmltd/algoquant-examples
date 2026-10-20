# Performance Analyzer

[Open the app](https://tools.nmfin.tech/performance/) · [Tools directory](https://tools.nmfin.tech/) · [App source](../../src/main/java/tech/nmfin/algoquant/web/performance/PerformanceAnalyzer.java)

Import a **public Google Sheet or direct HTTPS CSV** of daily NAV/price levels. Compare multiple accounts using AlgoQuant, with an S&P 500 benchmark reused from the input or independently downloaded/simulated from Yahoo `^GSPC`.

## Input

The first column must be `Date`, with ISO `YYYY-MM-DD` values. Remaining columns contain **finite NAV or price levels** (the first common value must be positive), not percentage returns. Use distinct column names. Quoted thousands separators are supported.

```csv
Date,My portfolio,S&P 500
2020-01-02,100.00,"3,257.85"
2020-01-03,101.50,"3,234.85"
```

Google Sheets edit links are converted to CSV exports using their tab `gid`. The sheet must be anonymously readable. The form is prefilled with the [example Google Sheet](https://docs.google.com/spreadsheets/d/1pODA-aiWNfXLEGmscO8MB6zkKSrjmnXq0dAtJITXrDs/edit?usp=drivesdk), so users can click **Analyze performance** immediately. The URL remains editable for users' own public sheets or CSV files. Requests are limited to 2 MB, 10 input series, 10,000 rows, bounded redirects and download timeouts. Private, loopback and metadata-network destinations, HTTP, nonstandard ports and embedded credentials are rejected.

Optional start/end dates filter the input. Only completed dates before today in New York are accepted. Observations are sorted. Repeated date rows are merged when their values agree; conflicting duplicates are rejected. Comparisons use the intersection of dates observed for every account. Missing values are **not** forward-filled. The report states how many input dates were omitted.

Recognized benchmark headers include `S&P 500`, `SP500`, `SPX` and `^GSPC`. Only one benchmark column is supported. If absent, the shared `BuyAndHoldBenchmark` downloads and independently simulates Yahoo's S&P 500 price index for the period. That index excludes reinvested dividends. Supplied benchmark conventions depend on their source.

## Meaning of the results

Each series is normalized to **10,000 units at the first common observation**. Absolute-value statistics therefore describe a normalized account, not the source currency balance. Original values remain available in the report and Excel Daily data tab.

**Use unitized or cash-flow-adjusted NAV.** Deposits, withdrawals, corporate-action unit changes and other funding changes are not inferred or removed. A change exceeding 30% is flagged for review but is not altered. Zero/negative observations are retained and flagged; return-based statistics are marked not applicable for those series, while balance/P&L/drawdown measures remain available. A total account balance that includes external cash flows is not a cash-flow-adjusted investment return.

All accounts use the shared AlgoQuant scalar catalog in NAV-only mode. Returns, drawdowns, periodic ratios, CAPM, rolling statistics and applicable risk measures are computed from NAV. Trades, fees, exposures and other execution-dependent measures are marked not applicable when they cannot be inferred from levels. No trade or zero fee is invented.

Daily headline volatility/Sharpe use sample observations and sqrt(252), assuming trading-session frequency. Irregular or non-trading-day inputs require care with that convention. CAGR uses actual elapsed calendar days. Undefined statistics retain their status. Scalar comparison charts do not imply that a positive difference is always better.

## Reports and exports

- Saved pages: `/performance/reports/<id>`. Each analysis saves an immutable snapshot in the same private GCS/local store as the other apps. A source update does not change an old report.
- Interactive comparison cards/charts, full scalar tables and normalized NAV history with hover, zoom, pan and legends.
- Excel: one statistics tab per account, Daily data with original levels/normalized NAV, and Analysis notes. Values retain full precision with two-decimal display formatting.
- CSV: select an account; shared `PerformanceCsv` uses AlgoQuant's `PerformanceReportCsvExporter`.
- JSON: complete snapshot. PDF: browser print dialog with a report layout; choose Save as PDF.

Anyone with a report URL can read the saved data. Only submit data you intend to make viewable through a shareable report. [Tools disclaimer](https://tools.nmfin.tech/#disclaimer).

## Routes

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/performance/` | App |
| POST | `/performance/api/analyze` | Form fields `url`, optional `start`, `end` |
| GET | `/performance/api/reports/<id>` | Saved JSON |
| GET | `/performance/api/reports/<id>/report.xlsx` | Excel |
| GET | `/performance/api/reports/<id>/series-<zero-based-index>.csv` | Account CSV |

## Shared implementation

Both apps use `PerformanceStatistics`, `AllPerformanceMeasures`, `SnapshotStore`, `HttpSupport`, `PerformanceCsv`, `SpreadsheetWorkbook` and `BuyAndHoldBenchmark` in `tech.nmfin.algoquant.web`. Imported NAV calculations live in `NavSeriesAnalysis`; URL loading is isolated in `PublicCsvSource`. `ToolsWeb` hosts both apps through `ToolRoutes`; shared `SavedReport` and `ReportStore` assign metadata and isolate saved reports by app. App-specific `PerformanceWorkbook` classes choose sheets using the shared writer. Existing routes, snapshot fields and export layouts are preserved.

Frontend report components and theme live in `src/main/resources/web/shared/`. Performance Analyzer lives in `src/main/java/tech/nmfin/algoquant/web/performance/` and `src/main/resources/web/performance/`.

Run/build/deploy through the existing service and [instructions](../README.md). The public URL importer adds no credentials or service-account keys. Both apps share the simulation capacity gate. Unit/HTTP tests exercise normalization, duplicate handling, benchmark reuse/fallback, URL restrictions and immutable exports; Chrome tests exercise both apps.
