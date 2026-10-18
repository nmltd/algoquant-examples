# Tutorial audit

This pass documents all 83 production Java source files: 64 demo/support sources
and 19 web application/shared sources. It adds class Javadoc, targeted method
contracts, comments at library call boundaries and a [reading guide](tutorials.md).
Java executable tokens are unchanged; the findings below remain existing behavior.

## Findings to account for when reusing examples

| Location | Finding and consequence | Tutorial guidance |
| --- | --- | --- |
| `model/portfoliooptimization/RandomReturns.seed(long...)` | After its single-seed branch, the method still reads `seeds[1]`. Zero or one seed can throw an array-index exception. | Supply two seeds with the current implementation. The class and method docs now say so. |
| `model/infantino2010/TestData.TEN_CHOOSEN_PLUS_NDX` | Builds an index-inclusive set but returns a fresh `TEN_CHOOSEN.stocks()` instead. | The returned universe omits `^NDX`; do not assume the constant name describes its actual result. |
| `model/tutorial/TutorialStrategy` | Equal prices take the long branch (`>=`). State is retained in `previousPrice`, and the event handler does not filter by product. | Use a fresh instance per run; its example feed is single-product. Add a product filter when adapting to multiple instruments. |
| Built-in `SMA2` examples | Fixed-unit long/short holdings; increasing simulator cash does not resize them. | Interpret account returns using the actual cash and exposure. The web app's custom full-equity implementation is different. |
| `model/portfoliooptimization/PortfolioOptimComparisons2Demo` | Some branches reference an absolute Windows Compustat path and local datasets. | Select and configure the desired dataset branch; not all inputs are publicly downloadable. |
| Factor-model examples | Depend on configured accounting, benchmark and sometimes SQL data beyond Yahoo prices. | Inspect the configuration and estimation windows before running. |
| `model/tutorial/TutorialStrategyDemo` | Reads the first sampled price without checking for empty history. | Validate data availability when adapting the example. Sampling is monthly but registered return measures are annual. |
| `model/jurek2007/OULogSpreadCRRASignalDemo` | Fits an OU process on the generated path it subsequently evaluates, and uses fixed `W` for allocation instead of cumulative `W_new`. | Treat this as a model illustration, not a holdout backtest or a compounding account. Its main also performs many Monte Carlo runs. |
| `misc/MCSimulationDemo` | Uses `AlwaysFillModel`; its own TODO notes a trade-definition mismatch with the model. | Execution assumptions and the definition of a trade affect reported results. |
| `ib/IBStrategyServerDemo` | Requires a broker connection and Google Sheets configuration; historical Docker instructions use the former demo module paths. | It is a service integration example capable of submitting orders, not an offline historical simulation. Review infrastructure and paper-account configuration separately. |
| `RunAllDemo` | Discovers a heterogeneous set of annotated demos. | Run individual lessons; this is not a bounded offline test suite. |

Paths in the table's `model/`, `misc/` and `ib/` rows are relative to
`src/main/java/tech/nmfin/algoquant/demo/`.

## Comment corrections

- Clarified observation-count lags, signed-unit order quantities, target-position
  deltas, mutable strategy state and execution-versus-submission semantics.
- Distinguished optimizer weights from executable orders and documented
  observations-by-assets matrices and one-based NM indices.
- Explained report measure registration, period/trade sampling, return thresholds,
  unavailable statistics, independent benchmark accounts and NAV-only limitations.
- Replaced an unsupported categorical claim about Markowitz optimization with an
  explanation of estimation sensitivity and the limits of the synthetic comparison.
- Escaped Javadoc HTML entities, including existing raw ampersands, so strict
  documentation generation succeeds.

## Verification

- Compiled all 83 production sources with Java 8 source/target settings against the
  published obfuscated AlgoQuant distribution and Gson 2.13.2. Compilation passed
  with 14 existing/compiler-version warnings (including varargs and deprecated API use).
  The available local compiler is JDK 17; this was not a JDK 8 Maven packaging run.
- Generated documentation for all 83 sources with Javadoc
  `-Xdoclint:all,-missing -source 8`; no errors or warnings.
- Compared token type and value for every Java source against the base commit,
  ignoring comments and whitespace: all 83 match.
- Checked repository-relative links in the tutorial guide and whitespace errors.
- Full tests, external downloads, databases, broker integrations and deployment
  were not run in this documentation-only audit.

## Research model documentation

The follow-up [model literature guide](model-literature.md) records publication
provenance, access limitations and paper-versus-implementation differences for
all 48 files under `demo/model`. These comments are included in the same review PR.
