# Model literature and implementation guide

These are tutorial adaptations, not automatic replications of the linked papers.
The class Javadoc separates **Model and publication** from **Implementation in this
tutorial**. A reference identifies provenance or related theory; it does not certify
that every equation, estimator, dataset, trading cost or reported result is reproduced.
No original PDFs are redistributed in the repository.

## Publications and what was accessible

Research checked on 18 October 2026. “Full” below means the original text was
accessible and the relevant model sections were reviewed, not that every proof or
empirical table was reimplemented. Access limitations are stated explicitly.

| Publication | Review basis |
| --- | --- |
| [L. R. Infantino and S. Itzhaki (2010), Developing high-frequency equities trading models, MIT MBA thesis](https://dspace.mit.edu/handle/1721.1/59122) | Full thesis retrieved from the MIT repository API; model sections 2.2–2.4 and regime discussion reviewed. Canonical record links to the printable thesis. |
| [M. Dai, Q. Zhang and Q. J. Zhu (2010), Trend Following Trading under a Regime Switching Model, SIAM Journal on Financial Mathematics 1, 780–810](https://doi.org/10.1137/090770552) | Full journal PDF read through SIAM; model, filter, trading-cost assumptions and market-test discussion reviewed. Journal year is 2010. |
| [A. d’Aspremont (2008 preprint; published 2011), Identifying Small Mean Reverting Portfolios, Quantitative Finance 11(3), 351–364](https://www.di.ens.fr/~aspremon/PDF/MeanRevVec.pdf) | Full February 2008 author preprint read, including Sections 2–3; author record verifies the 2011 journal version. |
| [R. J. Elliott, J. van der Hoek and W. P. Malcolm (2005), Pairs trading, Quantitative Finance 5(3), 271–276](https://doi.org/10.1080/14697680500149370) | Original publication identity and abstract checked through ANU/publisher records. ANU marks the file restricted until 2037; full-text review was not available. |
| [T. L. Lai, H. Xing and Z. Chen (2011), Mean–variance portfolio optimization when means and covariances are unknown, Annals of Applied Statistics 5(2A), 798–823](https://doi.org/10.1214/10-AOAS422) | Full 2011 arXiv/journal text read, especially Sections 3–4 on predictive risk and NPEB resampling. API names retain 2010. |
| [A. Corvalán (2005), Well Diversified Efficient Portfolios, Central Bank of Chile Working Paper 336](https://dialnet.unirioja.es/descarga/articulo/1364447.pdf) | Full original working paper retrieved via Dialnet and its two-stage formulation reviewed. Official bank URL returned a webpage during this audit. |
| [H. Markowitz (1952), Portfolio Selection, Journal of Finance 7(1), 77–91](https://doi.org/10.1111/j.1540-6261.1952.tb01525.x) | Full original article retrieved from the Berkeley-hosted scan/text and its expectation/variance formulation reviewed. DOI is the canonical publication link. |
| [F. Black and R. Litterman (1992), Global Portfolio Optimization, Financial Analysts Journal 48(5), 28–43](https://people.duke.edu/~charvey/Teaching/BA453_2006/Black_Litterman_Global_Portfolio_Optimization_1992.pdf) | Original 1992 scanned article retrieved from the Duke course archive; glossary inspected with OCR. Detailed derivations were not independently verified. |
| [J. W. Jurek and H. Yang (2007), Dynamic Portfolio Selection in Arbitrage, working paper, SSRN 882536](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=882536) | Author-submitted SSRN publication record/abstract verified through the public index. Direct SSRN access returned 403; no full-text derivation review claimed. |
| [J. L. Knight, S. E. Satchell and K. C. Tran (1995), Statistical modelling of asymmetric risk in asset returns, Applied Mathematical Finance 2(3), 155–172](https://doi.org/10.1080/13504869500000009) | Title, authors, year, journal and DOI verified against bibliographic records and the author publication list; publisher full text unavailable. API behavior was checked in code. |
| [E. Acar and S. Satchell (1997), A theoretical analysis of trading rules: an application to the moving average case with Markovian returns, Applied Mathematical Finance 4(3), 165–180](https://doi.org/10.1080/135048697334791) | Related 1997 moving-average paper identified; full text restricted. It is explicitly not asserted to be the exact derivation used by GMA12. |
| [E. F. Fama and K. R. French (1993), Common risk factors in the returns on stocks and bonds, Journal of Financial Economics 33(1), 3–56](https://doi.org/10.1016/0304-405X(93)90023-5) | Original publication identity/abstract and Kenneth French’s own factor-definition page checked. Full 1993 article was not independently read; used as related context, not a replication claim. |

## Important distinctions

- **Infantino/Itzhaki:** PCA and accumulated-return regression on an intraday
  quote sample in the thesis; daily Yahoo bars, T=500 and k=2 in these demos.
  The parameter demo disables regime switching and compares H=1,21,…,201.
  The order adapter reads `signal.dReturns()` and uses fixed per-stock notional;
  the application does not inherit full-equity sizing from simulator cash.
- **Dai:** the 2011 package name is retained, while the three-author journal paper
  is from 2010. Bootstrap, calibration, long/short choices and additional stop
  logic are code choices to inspect separately from the stopping theory.
- **d’Aspremont:** the folder uses the author preprint year (2008); publication
  was 2011. Sparse basket search is separate from Kagi trading and S&P 500 data.
- **Lai:** the 2010 API name is retained, while the cited three-author journal
  article is from 2011. This example chooses its own resampler, shrinkage,
  bootstraps, calibration window, risk aversion and rebalance interval.
- **Corvalán:** the two-stage diversification idea is the source; the library’s
  relative relaxation parameters must not be confused with a paper’s numerical
  tolerance or a different constraint normalization.
- **Elliott:** the demo actively selects one customization; posterior-state,
  constant-coefficient and quantile rules should not all be described as identical.
- **KST/GMA:** return generation, fitting and analytical trade quantities are
  different from a funded backtest. Acar–Satchell is related literature, with a
  different stated switching-Gaussian setting; an exact equivalence is not claimed.
- **OU allocation:** the demo fits and evaluates the same generated path and
  allocates using fixed W rather than cumulative W_new. Its original related
  spread-trading reference does not replace the Jurek/Yang provenance link.
- **Factor models:** the economic and fundamental examples choose different
  factors/estimators; neither is asserted to reproduce the 1993 Fama–French study.
- **Technical, grid and event lessons:** no single original publication has been
  established for these specific policies. Their mechanics are documented from
  code instead of attaching a plausible but unverified paper citation.

## Model source coverage

All 48 Java files under `demo/model` now have model/provenance explanations.
Support classes are identified as infrastructure rather than standalone models.

| Source | Learning focus |
| --- | --- |
| [BootstrapSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/dai2011/BootstrapSimulationDemo.java) | dai2011 |
| [Dai2011LongOnlyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/dai2011/Dai2011LongOnlyDemo.java) | dai2011 |
| [KagiMRBasketSimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/KagiMRBasketSimulationDemo.java) | daspremont2008 |
| [MRSimParamSP500](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/MRSimParamSP500.java) | daspremont2008 |
| [MultiPeriodKagiMRStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/daspremont2008/MultiPeriodKagiMRStrategyDemo.java) | daspremont2008 |
| [Elliott2005StrategyDemo1](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/Elliott2005StrategyDemo1.java) | elliott2005 |
| [Elliott2005StrategyDemo2](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/Elliott2005StrategyDemo2.java) | elliott2005 |
| [MeanReversionBasedOnSpreadQuantileDemo](../src/main/java/tech/nmfin/algoquant/demo/model/elliott2005/MeanReversionBasedOnSpreadQuantileDemo.java) | elliott2005 |
| [EconomicModelStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/EconomicModelStrategyDemo.java) | factormodel |
| [FactorModelUtils](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/FactorModelUtils.java) | factormodel |
| [FundamentalModelStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/factormodel/FundamentalModelStrategyDemo.java) | factormodel |
| [Infantino2010ParamAnalysisDemo](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/Infantino2010ParamAnalysisDemo.java) | infantino2010 |
| [Infantino2010PredictionAnalysisDemo](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/Infantino2010PredictionAnalysisDemo.java) | infantino2010 |
| [TestData](../src/main/java/tech/nmfin/algoquant/demo/model/infantino2010/TestData.java) | infantino2010 |
| [JanuaryEffectStrategy](../src/main/java/tech/nmfin/algoquant/demo/model/januaryeffect/JanuaryEffectStrategy.java) | januaryeffect |
| [JanuaryEffectStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/januaryeffect/JanuaryEffectStrategyDemo.java) | januaryeffect |
| [OULogSpreadCRRASignalDemo](../src/main/java/tech/nmfin/algoquant/demo/model/jurek2007/OULogSpreadCRRASignalDemo.java) | jurek2007 |
| [GMA12Demo](../src/main/java/tech/nmfin/algoquant/demo/model/kst1995/GMA12Demo.java) | kst1995 |
| [KnightSatchellTran1995MLEDemo](../src/main/java/tech/nmfin/algoquant/demo/model/kst1995/KnightSatchellTran1995MLEDemo.java) | kst1995 |
| [Corvalan2005Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/Corvalan2005Demo.java) | portfoliooptimization |
| [CovarianceComparisonDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/CovarianceComparisonDemo.java) | portfoliooptimization |
| [Lai2010NPEBModelDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/Lai2010NPEBModelDemo.java) | portfoliooptimization |
| [MarkowitzBigDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/MarkowitzBigDemo.java) | portfoliooptimization |
| [MarkowitzDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/MarkowitzDemo.java) | portfoliooptimization |
| [PerformanceOptimizedYahooEODPortfolioDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PerformanceOptimizedYahooEODPortfolioDemo.java) | portfoliooptimization |
| [PortfolioOptimComparisons1Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimComparisons1Demo.java) | portfoliooptimization |
| [PortfolioOptimComparisons2Demo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimComparisons2Demo.java) | portfoliooptimization |
| [PortfolioOptimizationAlgorithmDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimizationAlgorithmDemo.java) | portfoliooptimization |
| [PortfolioOptimizationBacktestDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/PortfolioOptimizationBacktestDemo.java) | portfoliooptimization |
| [RandomReturns](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/RandomReturns.java) | portfoliooptimization |
| [SOCPMarkowitzComparisonDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/SOCPMarkowitzComparisonDemo.java) | portfoliooptimization |
| [CoinBacktestDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/coin/CoinBacktestDemo.java) | portfoliooptimization |
| [CSI300SimulationDemo](../src/main/java/tech/nmfin/algoquant/demo/model/portfoliooptimization/csi300/CSI300SimulationDemo.java) | portfoliooptimization |
| [AccumulationDistributionLineDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/AccumulationDistributionLineDemo.java) | technical |
| [ChaikinMoneyFlowDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/ChaikinMoneyFlowDemo.java) | technical |
| [DemoSupport](../src/main/java/tech/nmfin/algoquant/demo/model/technical/DemoSupport.java) | technical |
| [SMA2Demo010](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo010.java) | technical |
| [SMA2Demo011](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo011.java) | technical |
| [SMA2Demo020](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2Demo020.java) | technical |
| [SMA2DynamicDemo](../src/main/java/tech/nmfin/algoquant/demo/model/technical/SMA2DynamicDemo.java) | technical |
| [TutorialStrategy](../src/main/java/tech/nmfin/algoquant/demo/model/tutorial/TutorialStrategy.java) | tutorial |
| [TutorialStrategyDemo](../src/main/java/tech/nmfin/algoquant/demo/model/tutorial/TutorialStrategyDemo.java) | tutorial |
| [MRModelFixedGridDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelFixedGridDemo.java) | volarb |
| [MRModelFixedGridOnSGXDataDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelFixedGridOnSGXDataDemo.java) | volarb |
| [MRModelPeriodicDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelPeriodicDemo.java) | volarb |
| [MRModelRanged1Demo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelRanged1Demo.java) | volarb |
| [MRModelRanged2Demo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MRModelRanged2Demo.java) | volarb |
| [MeanReversionPortfolioDemo](../src/main/java/tech/nmfin/algoquant/demo/model/volarb/MeanReversionPortfolioDemo.java) | volarb |

## Reading the Infantino parameter example

Follow `main` → `newParamList` → constructor → `run` → `printReport`.
The constructor loads and synchronizes product caches. The parameter list keeps
T and k fixed while varying H; each analyzer factory call constructs a new
strategy. The report is a sensitivity comparison on that sample, not proof of
future predictive accuracy. Registered commission statistics do not themselves
configure transaction fees in the simulator.

Validation for this documentation pass: compile all 83 production Java sources
against the published distribution with Java 8 source/target settings; generate
Javadoc with strict HTML/reference validation; compare Java tokens to confirm no
algorithm changes. Full tests and external simulations are deliberately skipped.
