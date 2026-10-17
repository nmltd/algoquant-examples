# SMA2Demo011

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo011.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Fixed SMA crossover; fast lag 1, slow lag 5. Demonstrates an explicit USD exchange-rate table with a fixed HKD conversion of 1/7.8. The USD stock itself does not require that extra conversion.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | META |
| Requested interval (end excluded) | 2012-01-01 to 2015-03-31 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 846 |
| Executions | 35 |
| P&L | 531.903015 |
| InformationRatioForTrades | 5.120161 |
| OmegaForTrades | Infinity |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo011 -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].
