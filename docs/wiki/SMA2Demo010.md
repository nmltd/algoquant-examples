# SMA2Demo010

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo010.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Fixed SMA crossover; fast lag 1, slow lag 2. Uses the simulator builder and an HKD-denominated stock descriptor.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | ^HSCE |
| Requested interval (end excluded) | 2010-01-01 to 2016-01-22 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 1580 |
| Executions | 65 |
| P&L | 1015.941925 |
| InformationRatioForTrades | 4.671747 |
| OmegaForTrades | Infinity |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo010 -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].
