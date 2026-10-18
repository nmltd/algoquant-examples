# AlgoQuant examples

[AlgoQuant](https://nmfin.tech/products/algoquant) is a framework and host/hub for studying, simulating, researching and applying quantitative strategies. This repository provides usage tutorials, quantitative model references and web analysis applications.

- **[Open the SMA2 strategy lab](https://tools.nmfin.tech/sma2/)** — compare an SMA crossover strategy with buy-and-hold of the selected stock and the S&P 500.
- **[Performance Analyzer](https://tools.nmfin.tech/performance/)** — import a public Google Sheet or CSV of daily NAV/price levels and compare accounts with an input or downloaded S&P 500 benchmark.
- **[Tools directory](https://tools.nmfin.tech/)** — the front page for this and future applications.
- **[Web application setup and deployment](web/README.md)** — local execution, saved reports, exports, shared components and Cloud Run.

The public repository contains the example and application source. The underlying library is supplied by the obfuscated combined Maven artifact `tech.nmfin:algoquant-dist:3.3.0-SNAPSHOT` and requires a valid NM license.

## Learning the library

Browse the [examples and tutorials Javadoc](https://nmltd.github.io/algoquant-examples/) alongside the [AlgoQuant library API](https://nmltd.github.io/algoquant/). Regenerate and publish the examples documentation with the manual **Publish examples Javadoc without tests** workflow; it compiles source and validates documentation without running the test suite.


Start with the [tutorial reading guide](docs/tutorials.md), then follow the class Javadoc and inline comments in each example. The [audit notes](docs/tutorial-audit.md) record known limitations without changing the existing algorithms.

## Tutorials and quantitative model references

Browse the [tutorial-first wiki](https://github.com/nmltd/algoquant-examples/wiki) to learn how to use AlgoQuant, then explore quantitative model implementations for reference, study, further research and actual application. Tutorial results are incidental and irrelevant to their teaching purpose. [Execution records](https://github.com/nmltd/algoquant-examples/wiki/Execution-records) retain measured outputs, charts and diagnostics. Raw output archives are versioned as [GitHub releases](https://github.com/nmltd/algoquant-examples/releases).

The manual **Run model demos and prepare tutorials** workflow compiles and runs demos without running the test suite. Technical examples use their documented synthetic `--quick` mode; other demos use their original defaults. Each page distinguishes completed calculations from failed, resource-limited or timed-out attempts. See [runner setup and limits](scripts/MODEL_DEMOS.md).

## SMA2 web application

Choose a supported US stock, an inclusive backtesting period, and fast/slow SMA periods in **trading days**, such as 10 days and 30 days. Each new backtest simulates three independently funded accounts, each starting with $10,000:

| Account | Simulation |
| --- | --- |
| SMA2 | Full-equity long/short moving-average crossover strategy |
| Underlying buy-and-hold | Invest in fractional shares at the first selected adjusted close and hold |
| S&P 500 | Independently buy and hold hypothetical fractional units of Yahoo `^GSPC` over the requested period |

The report compares 187 configured scalar performance results per account, including returns, drawdowns, volatility, risk-adjusted ratios, CAPM measures, exposures and trade statistics. Summary charts support hover/focus and legend toggles. Price/SMA and portfolio-history charts support zoom, pan and value inspection.

Every successful run has a saved, shareable URL. Reloading a saved report does not rerun the simulation.

### Report exports

- **Excel:** one workbook with **SMA2 statistics**, **Buy and hold statistics**, **S&P 500 statistics**, and **Daily data** tabs.
- **Daily data:** `date`, `adjusted_close`, `fast_sma`, `slow_sma`, `sma2_nav`, `fully_invested_buy_hold_nav`, `sp500_close`, `sp500_nav`.
- **PDF:** click **Export to PDF**, then choose **Save as PDF** in the browser print dialog. The print layout includes expanded statistics and charts.
- **CSV and JSON:** SMA2 and underlying buy-and-hold performance CSVs, plus the complete saved report JSON. The API also offers an S&P 500 CSV.

Excel and webpage values display two decimal places. Excel numeric cells, CSVs and JSON retain full precision. Strategy statistics exports use AlgoQuant's `PerformanceReportCsvExporter`.

The S&P 500 benchmark is a **price index without reinvested dividends**. Stock adjusted prices may include dividend adjustments. The simulations assume zero trading, financing and short-borrow costs; results are educational rather than a representation of executable live performance. See [simulation conventions](web/README.md#simulation-conventions).

## Setup

Use **JDK 8** and **Maven 3.9.9**.

### Maven authentication

Merge this server entry into `~/.m2/settings.xml` (Windows: `%USERPROFILE%\.m2\settings.xml`):

```xml
<settings>
    <servers>
        <server>
            <id>github-nm-packages</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

Use your own GitHub personal access token **(classic)** with `read:packages`. The POM already configures the public distribution repository at `https://maven.pkg.github.com/nmltd/maven-packages`. GitHub Packages requires authentication even for these public Maven packages. No private source access or internal `NM_REPOSITORIES_TOKEN` is needed.

### NM license

Put a valid license at `license/nm.lic` before building and running. A [public starter trial license](https://github.com/nmltd/new-algoquant-project/blob/main/license/nm.lic) is available; the library enforces its validity and entitlements. Licenses are loaded externally and excluded from the examples JAR.

The web server and web tests also accept `NM_LICENSE_FILE=/absolute/path/nm.lic`. The six command-line demos use `-Dnm.license=/absolute/path/nm.lic` to override their default license path.

## Build and run the web app

From the repository root:

```bash
mvn -U clean verify
java -Djava.awt.headless=true -Dlogback.configurationFile=web/logback.xml \
  -cp 'target/classes:target/lib/*' tech.nmfin.algoquant.web.ToolsWeb
```

Open [http://localhost:8080/sma2/](http://localhost:8080/sma2/) for SMA2, or [http://localhost:8080/](http://localhost:8080/) for the tools directory. The command above uses Unix classpath separators; use `;` instead of `:` on Windows.

The web app downloads Yahoo data by default. For explicitly labeled offline verification, set `SMA2_DATA_MODE=synthetic`. There is no automatic synthetic fallback on download failure.

See [the web README](web/README.md) for environment variables, API routes, testing and deployment.

## Performance Analyzer

Open [Performance Analyzer](https://tools.nmfin.tech/performance/) or see [its input format, assumptions and API](web/performance/README.md). Supply a public Google Sheet or direct HTTPS CSV with `Date` followed by one or more NAV/price columns. An S&P 500 column is reused; otherwise Yahoo `^GSPC` is downloaded and simulated. The report aligns common dates and rebases each account to 10,000 normalized units. It does not infer trades or remove deposits/withdrawals. Shared classes support both apps, including AlgoQuant statistics, snapshot storage, CSV/Excel exports and report components.

## Six command-line technical examples

These examples retain their original package names under `tech.nmfin.algoquant.demo.model.technical`.

```bash
mvn exec:java
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.ChaikinMoneyFlowDemo
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2DynamicDemo -Dexec.args="--quick"
```

The default is `SMA2Demo010`.

| Class | Demonstrates |
| --- | --- |
| SMA2Demo010 | Two moving-average crossover simulation |
| SMA2Demo011 | Crossover simulation with currency conversion |
| SMA2Demo020 | The simulation-template API |
| AccumulationDistributionLineDemo | An ADL-based strategy |
| ChaikinMoneyFlowDemo | A CMF-based strategy |
| SMA2DynamicDemo | Periodic parameter calibration and CSV report export |

Unlike the web app, all six demos default to **deterministic synthetic OHLC/volume data** from `DemoSupport`. Prices are keyed by calendar date, so calibration and main-simulation windows see consistent history. These results demonstrate APIs, not historical investment performance.

Options may be combined:

- `--charts`: open desktop charts where a plotter is available; requires a graphical environment.
- `--live`: download Yahoo historical data; requires network access and available symbol history.
- `--quick`: limit dynamic calibration to one parameter combination over three months. The full run searches 16 combinations over several years.

```bash
mvn exec:java -Dexec.args="--charts"
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo011 -Dexec.args="--live --charts"
```

Live examples use META rather than the retired FB ticker and AAPL rather than the historical JWN example. The dynamic example creates fresh simulators and performance measures for each parameter combination. Dynamic CSV outputs are written under `log/`.

After building, run all six offline:

```bash
bash scripts/run-offline.sh
```

The script uses `target/lib/`, disables desktop charts and bounds each run to five minutes. CI also checks the web API, saved-report persistence, Excel/CSV values, Chrome interactions and PDF export, and the production container.

## Repository layout

| Location | Purpose |
| --- | --- |
| [Technical demos](src/main/java/tech/nmfin/algoquant/demo/model/technical/) | Six examples and `DemoSupport` |
| [Shared web code](src/main/java/tech/nmfin/algoquant/web/) | Reusable `BuyAndHoldBenchmark` and `AllPerformanceMeasures` |
| [SMA2 Java code](src/main/java/tech/nmfin/algoquant/web/sma2/) | App, HTTP server, saved-report storage and export adapters |
| [SMA2 frontend](src/main/resources/web/sma2/) | HTML, JavaScript and styles |
| [Shared web resources](src/main/resources/web/) | Tools-directory page and styles |
| [Web tests](src/test/java/tech/nmfin/algoquant/web/) | Shared benchmark and SMA2 tests |
| [Web operations](web/) | Setup/deployment scripts, Dockerfile and browser/API checks |
| [Demo results and Wiki sources](docs/) | Recorded example outputs and explanations |

New applications should have their own package and resource directory under `web/<app>`; reusable simulation/reporting code belongs directly under the shared `web` package.

## Disclaimer

The [tools homepage disclaimer](https://tools.nmfin.tech/#disclaimer) covers all hosted tools, applications, data and outputs: educational/informational use, no warranties, use at your own risk and liability limits subject to applicable law and mandatory rights.

## Further reading

[AlgoQuant Javadoc](https://nmltd.github.io/algoquant/) · [Minimal starter](https://github.com/nmltd/new-algoquant-project) · [Public distributions](https://github.com/nmltd/maven-packages) · [Example results Wiki](https://github.com/nmltd/algoquant-examples/wiki)

The example sources retain their original copyright and license notices. The underlying AlgoQuant/NM Dev libraries require a valid NM license.

## Complete demo collection

The remaining 57 Java demo/support files from `nmltd/algoquant/demo` are now alongside
the existing technical examples under `src/main/java/tech/nmfin/algoquant/demo`.
Their packages and class names are preserved. Configuration, the historical-data
fixtures (about 334 MB) and IB container assets live at `config/`, `test-data/`
and `ibdemo/`, so relative data paths still work from this repository's root.
The old private demo POM is superseded by this repository's standalone public-consumer POM.

Compile and package all examples without running tests:

```bash
mvn -B -ntp -Dmaven.test.skip=true clean package
```

Run a selected class from the repository root with your NM license on the classpath:

```bash
java -cp 'license:target/classes:target/lib/*' tech.nmfin.algoquant.demo.model.tutorial.TutorialStrategyDemo
```

The imported demos cover charts, historical data, IB execution, simulation,
mean reversion, factor models, portfolio optimization and volatility arbitrage.
Unlike the six synthetic technical demos, many need a desktop, network data,
database access, custom file paths or a configured broker. Read each class before
running it; do not run `RunAllDemo` as an offline smoke test.

See [the migration record](docs/demo-migration.md) for provenance and validation.
