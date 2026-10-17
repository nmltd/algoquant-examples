# SMA2Demo020

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo020.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Fixed SMA crossover; fast lag 1, slow lag 2. Uses SimTemplateYahooEOD with an overridden data factory, so the default remains synthetic. The template specifies products, dates, strategy, currency and measures. Maximum long exposure reported: 84.987209.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | XOM |
| Requested interval (end excluded) | 2010-01-01 to 2011-01-03 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 263 |
| Executions | 11 |
| P&L | 168.880699 |
| InformationRatioForTrades | 4.956692 |
| OmegaForTrades | Infinity |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo020 -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].
