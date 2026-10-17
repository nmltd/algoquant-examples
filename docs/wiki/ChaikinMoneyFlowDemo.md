# ChaikinMoneyFlowDemo

**Synthetic-data demonstration.** [Source](https://github.com/nmltd/algoquant-examples/blob/b55a1e37dee29fdd1d71854434a288b72a8664b8/src/main/java/tech/nmfin/algoquant/demo/model/technical/ChaikinMoneyFlowDemo.java) · [Verified execution](https://github.com/nmltd/algoquant-examples/actions/runs/38997285630) · [[Data-and-methodology]] · [[Reproduce-the-results]]

## What this example does

Uses a twenty-observation price-change average and twenty-observation Chaikin Money Flow. Targets +1 unit when smoothed price changes are positive and CMF negative, -1 for the reverse signs, and zero otherwise. OHLCV events supply the indicator inputs; order-book events supply execution prices.

## Configuration and verified results

| Item | Value |
|---|---|
| Instrument label | FCX |
| Requested interval (end excluded) | 2010-02-01 to 2010-12-22 |
| Data | Generated weekday OHLCV bars |
| Mode | Offline, headless; script passes `--quick` (only dynamic demo changes its workload) |
| NAV observations | 232 |
| Executions | 16 |
| P&L | -12.479865 |
| InformationRatioForTrades | -0.328490 |
| OmegaForTrades | -0.562450 |

The run passed the nonempty/finite NAV check. Execution count counts fills; it is not necessarily a count of completed round-trip trades. NAV observations count the report's valuations.

## Run it

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.ChaikinMoneyFlowDemo -Dexec.args="--quick"
```

A valid external NM license and Maven authentication are required; see [[Reproduce-the-results]].
