# Data and methodology

## Synthetic inputs

`DemoSupport` generates one OHLCV bar per weekday at the instrument exchange's closing time. Exchange holidays are not modeled. Requested intervals include their start and exclude their end. The calendar-date formula is identical for every stock:

```text
d = calendar days since 2009-01-01
close = 100 + 0.02*d + 8*sin(d/11) + 3*cos(d/29)
high = close + 1.5 + 0.4*sin(d/7)
low = close - 1.5 + 0.4*cos(d/13)
open = close - 0.2
volume = 1,000,000 + 1,000*(d mod 200)
adjusted close = close
```

Using calendar dates means overlapping calibration windows get identical prices for the same date. The generated trend and oscillations exercise strategy logic; they do not model real assets, realistic cross-asset correlations, market microstructure, or corporate actions.

SMA examples consume order books adapted from adjusted closes. The ADL and CMF examples also consume full OHLCV events for their volume indicators. `--live` selects cached Yahoo downloads instead, and is not used in this report.

## What was verified

All six executable demos compiled against the public obfuscated combined JAR and ran in separate JVMs without desktop charts or a market-data service. Each returned a nonempty NAV series containing only finite values. Each process had a five-minute timeout. Seven Java source files include the shared helper; there are six simulations.

## Interpretation limits

P&L is a monetary amount rather than a return percentage. Trade-level information ratios and Omega are recorded with the library's measure names. `Infinity`, `NaN`, and the negative CMF Omega are preserved as emitted, rather than silently normalized or interpreted as conventional portfolio statistics. Read the implementation/API definition before comparing such measures with other systems.

Executions are fills, not completed trades. The dynamic quick run's one execution is insufficient to infer a distribution of completed-trade returns. These smoke runs do not verify realistic costs, slippage, shorting constraints, index tradability, execution timing or absence of look-ahead bias in a real-market research setup. Synthetic results are API examples, not investment backtests.

[[Home]]
