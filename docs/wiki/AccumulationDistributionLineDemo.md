# AccumulationDistributionLineDemo

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/AccumulationDistributionLineDemo.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Uses ten-observation arithmetic moving averages of daily price changes and ADL increments (money-flow volume). The implemented rule targets +1 unit when price changes are positive and ADL increments negative, -1 for the reverse signs, and zero otherwise. Orders are target minus current position. This is the actual implemented direction; it should not be read as the conventional bullish-divergence rule.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | AAPL |
| Requested interval (end excluded) | 2010-01-01 to 2015-01-01 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 1304 |
| Executions | 103 |
| P&L | 320.358462 |
| InformationRatioForTrades | 1.018946 |
| OmegaForTrades | 20.224689 |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.AccumulationDistributionLineDemo -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].
