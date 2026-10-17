package tech.nmfin.algoquant.web.sma2;

import java.time.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.fxrate.*;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.trade.calculator.SimpleTradeCalculator;
import tech.nmfin.algoquant.execution.performance.measure.*;
import tech.nmfin.algoquant.execution.performance.measure.byperiod.*;
import tech.nmfin.algoquant.execution.performance.measure.ir.*;
import tech.nmfin.algoquant.execution.performance.measure.omega.*;
import tech.nmfin.algoquant.execution.performance.measure.pertradestats.*;
import tech.nmfin.algoquant.execution.performance.measure.transactionfee.*;
import tech.nmfin.algoquant.execution.performance.measure.var.*;
import tech.nmfin.algoquant.execution.performance.rolling.RollingMaxExposure.ExposureType;
import tech.nmfin.returns.ReturnsCalculators;

/** Explicit, reviewable library-measure catalog. No substitute formulas or silent zero fallbacks. */
public final class AllPerformanceMeasures {
    public static final class Metric {
        public String group, label, sourceClass, unit, detail, status, display;
        public Double value;
    }
    public static final class PeriodValue {
        public String start, end, status, display; public Double value;
    }
    public static final class PeriodTable {
        public String label, sourceClass, unit, detail, status = "ok";
        public List<PeriodValue> values = new ArrayList<>();
    }
    public final List<Metric> metrics = new ArrayList<>();
    public final List<PeriodTable> periods = new ArrayList<>();
    private final LocalDateTimeInterval interval;
    private final BufferCache<Double> nav;
    private final OrderBookCaches books;
    private final List<Execution> fills;
    private final ExchangeRateTable rates = new SimpleExchangeRateTable(Currencies.USD);
    private final NoTransactionFee fees = new NoTransactionFee();
    private final BenchmarkLookup benchmark;
    private final double capital;
    private final double[] daily;
    private final String benchmarkError;
    private final SimpleTradeCalculator trades = new SimpleTradeCalculator();
    private static final String PERIOD_NOTE = "Simple returns; calendar buckets with flat values outside partial edge periods. SMA lags, unlike these buckets, count trading sessions.";

    public AllPerformanceMeasures(LocalDateTimeInterval interval, double capital,
            SequentialCache<Double> nav, List<Execution> fills, OrderBookCaches books,
            BenchmarkLookup benchmark, String benchmarkError, double[] daily) {
        this.interval = interval; this.capital = capital; this.books = books;
        this.nav = BufferCache.fromSequentialCache(nav); this.fills = fills;
        this.benchmark = benchmark; this.benchmarkError = benchmarkError; this.daily = daily.clone();
    }

    public void compute() { compute(true); }

    /** Scalar comparisons can omit long period tables while using the same calculations. */
    public void compute(boolean includePeriodTables) {
        String g = "Balances and profit";
        measure(g,"Starting balance","USD","",new StartBalance());
        measure(g,"Ending balance","USD","",new EndBalance());
        measure(g,"Profit / loss (including open positions)","USD","",new ProfitLoss());
        measure(g,"Realized profit / loss","USD","Closed trades only.",new ProfitLossRealized());
        measure(g,"Profit before transaction fees","USD","",new ProfitNoTransactionFee());
        measure(g,"Profit after transaction fees","USD","Zero-cost simulation.",new ProfitAfterTransactionFee());
        measure(g,"Cumulative return","fraction","Account return, not an invested-share return.",new CumulativeReturn());
        measure(g,"Sum of simple returns","fraction","Arithmetic sum of successive NAV returns.",new SumReturn());
        unavailable(g,"Dividend-adjusted profit / loss","DividendAdjustedProfitLoss",
            "Not applicable: adjusted-close input already incorporates adjustments; separate dividend cash-flow data is not supplied.");
        g = "Drawdown and exposure";
        measure(g,"Maximum drawdown","USD","",new MaxDrawdown());
        measure(g,"Maximum drawdown","fraction","Relative to running NAV high.",new MaxDrawdownPercentage());
        measure(g,"Maximum drawdown duration","calendar days","Elapsed time, not trading sessions.",new MaxDrawdownDuration(Duration.ofDays(1)));
        for (ExposureType type : ExposureType.values()) {
            measure(g,"Maximum exposure / "+type,"USD","Library cash-flow exposure definition.",new MaxExposure(type));
            measure(g,"Profit / maximum exposure / "+type,"ratio","",new ProfitOverMaxExposure(type));
        }
        g = "Trading and costs";
        measure(g,"Execution count","count","Fills, not completed round trips.",new ExecutionCount());
        measure(g,"Commission","USD","Commission rate = 0.",new Commission(0));
        measure(g,"Commission / profit","ratio","Commission rate = 0; undefined when denominator is zero.",new CommissionProfitRatio(0));
        measure(g,"Transaction fee / percentage","USD","Percentage fee = 0.",new TransactionFeeByPercentage(0));
        measure(g,"Transaction fee / contract","USD","Fee per unit = 0; lot size = 1.",new TransactionFeeByContract(0,1));
        measure(g,"Win / loss count ratio","ratio","Closed trades; zero losing trades can produce infinity.",new WinLossRatio(trades));
        measure(g,"Win / loss size ratio","ratio","Closed trades.",new WinLossSizeRatio(trades));

        LocalDateTimeInterval monthly = MeasureUtils.alignIntervalMonthly(interval);
        LocalDateTimeInterval yearly = MeasureUtils.alignIntervalYearly(interval);
        Period[] lengths = {Period.ofDays(1),Period.ofMonths(1),Period.ofYears(1)};
        String[] names = {"Calendar daily","Monthly","Yearly"};
        LocalDateTimeInterval[] windows = {interval,monthly,yearly};
        for(int i=0;i<lengths.length;i++) {
            Period p=lengths[i]; LocalDateTimeInterval w=windows[i]; g=names[i]+" returns";
            measure(g,"Average return","fraction",PERIOD_NOTE,new PeriodReturnsAverage(w,p));
            measure(g,"Compound average return","fraction",PERIOD_NOTE,new PeriodReturnsCompound(w,p));
            measure(g,"Best return","fraction",PERIOD_NOTE,new PeriodReturnsBest(w,p));
            measure(g,"Worst return","fraction",PERIOD_NOTE,new PeriodReturnsWorst(w,p));
            measure(g,"Return standard deviation","fraction",PERIOD_NOTE,new PeriodReturnsStdev(w,p));
            measure(g,"Positive-return periods","count",PERIOD_NOTE,new PeriodReturnsPositive(w,p));
            measure(g,"Negative-return periods","count",PERIOD_NOTE,new PeriodReturnsNegative(w,p));
            measure(g,"Downside deviation","fraction","Target per-period return = 0. "+PERIOD_NOTE,new DownsideDeviation(w,p,ReturnsCalculators.SIMPLE,0));
            measure(g,"Sortino ratio","ratio","Target per-period return = 0. "+PERIOD_NOTE,new SortinoRatio(0,w,p));
            measure(g,"Information ratio","ratio","Constant per-period benchmark return = 0. "+PERIOD_NOTE,new InformationRatioForPeriods(p,ReturnsCalculators.SIMPLE,0));
            measure(g,"Omega / summation","ratio","Loss threshold = 0. "+PERIOD_NOTE,new OmegaForPeriods(p,ReturnsCalculators.SIMPLE,0,new OmegaBySummation()));
            measure(g,"Omega / option-pricing definition","ratio","Loss threshold = 0. "+PERIOD_NOTE,new OmegaForPeriods(p,ReturnsCalculators.SIMPLE,0,new OmegaByOptionPricing()));
            measure(g,"Sharpe-Omega","ratio","Loss threshold = 0. "+PERIOD_NOTE,new OmegaForPeriods(p,ReturnsCalculators.SIMPLE,0,new SharpeOmega()));
        }
        g="Annual and rolling statistics";
        measure(g,"Average annual rate of return","fraction","Library annual buckets, including flat-padded partial edge years; not elapsed-time CAGR.",new AverageAnnualRateOfReturn(yearly));
        measure(g,"Monthly Sharpe, annualized","ratio","Risk-free rate = 0; monthly ratio times sqrt(12).",new SharpeRatioMonthlyAnnualized(ReturnsCalculators.SIMPLE,0));
        measure(g,"Monthly Sortino, annualized","ratio","Monthly target = 0; monthly ratio times sqrt(12).",new SortinoRatioMonthlyAnnualized(monthly,0));
        measure(g,"Calmar ratio","ratio","Library annual-return numerator / maximum NAV drawdown fraction.",new CalmarRatio(yearly));
        for(int years:new int[]{1,3}) {
            measure(g,"Average rolling annual return / "+years+" years","fraction","Annually sampled "+years+"-year windows; N/A if history is insufficient.",new RollingAnnualReturnAverage(years));
            measure(g,"Minimum rolling annual return / "+years+" years","fraction","Annually sampled "+years+"-year windows.",new RollingAnnualReturnMin(years));
            measure(g,"Maximum rolling annual return / "+years+" years","fraction","Annually sampled "+years+"-year windows.",new RollingAnnualReturnMax(years));
            final int y=years;
            number(g,"Rolling annual-return sample count / "+years+" years","RollingAnnualReturns","count",
                "Number of completed annually sampled windows.",()->new RollingAnnualReturns(y).values(nav).size());
        }
        g="Trade return ratios";
        measure(g,"Information ratio / trades","ratio","Benchmark trade return = 0.",new InformationRatioForTrades());
        measure(g,"Omega / trades / summation","ratio","Loss threshold = 0.",new OmegaForTrades(0,new OmegaBySummation(),trades));
        measure(g,"Omega / trades / option pricing","ratio","Loss threshold = 0.",new OmegaForTrades(0,new OmegaByOptionPricing(),trades));
        measure(g,"Sharpe-Omega / trades","ratio","Loss threshold = 0.",new OmegaForTrades(0,new SharpeOmega(),trades));
        number(g,"Information ratio / trading-day sample","InformationRatio","ratio","Constant benchmark = 0; not annualized.",()->new InformationRatio().valueOf(daily.clone(),0));
        unavailable(g,"Information ratio / zero investment","InformationRatioForZeroInvestment",
            "Not applicable: this strategy has USD 10,000 initial capital; the zero-investment convention assumes zero initial capital.");

        g="Per-trade distribution statistics";
        for(PerTradeValue.Common field:PerTradeValue.Common.values()) {
            String unit=field==PerTradeValue.Common.PNL?"USD":field==PerTradeValue.Common.RETURN?"fraction":field.name().substring("DURATION_IN_".length()).toLowerCase(Locale.ROOT);
            for(StatOnDistribution.Common stat:StatOnDistribution.Common.values()) {
                String u=stat==StatOnDistribution.Common.VARIANCE?unit+" squared":unit;
                measure(g,field+" / "+stat,u,"Closed trades only. Duration units are elapsed/calendar time.",
                    new PerTradeStat(field+" / "+stat,trades,field,stat));
            }
        }
        unavailable(g,"Pair-trade return","PerTradePairReturn","Not applicable: requires two traded products and matched long/short legs.");
        unavailable("Custom measure builders","Filtered performance measure","PartialPerformanceMeasure",
            "Not applicable: a user-defined execution/tag filter is required; this app runs one untagged strategy.");
        unavailable("Custom measure builders","Linear combination of measures","LinearlyCombinedPerformanceMeasure",
            "Not applicable: user-defined measures and weights are required; there is no unique standalone statistic.");

        g="SPY benchmark and CAPM";
        if(benchmark==null) {
            for(String name:new String[]{"AlphaCAPM","BetaCAPM","RSquaredCAPM","BenchmarkCorrelation","TreynorRatio"})
                unavailable(g,name,name,benchmarkError);
            for(Metric m:metrics) if(m.group.equals(g)) m.status="unavailable";
        } else {
            String note="SPY adjusted-close benchmark; monthly simple returns; risk-free rate = 0. "+PERIOD_NOTE;
            measure(g,"CAPM alpha / monthly","fraction",note,new AlphaCAPM(interval,Period.ofMonths(1),benchmark,ReturnsCalculators.SIMPLE));
            measure(g,"CAPM beta","ratio",note,new BetaCAPM(interval,Period.ofMonths(1),benchmark,ReturnsCalculators.SIMPLE));
            measure(g,"CAPM R-squared","ratio",note,new RSquaredCAPM(interval,Period.ofMonths(1),benchmark,ReturnsCalculators.SIMPLE));
            measure(g,"Portfolio / benchmark level correlation","ratio","Correlation of sampled NAV and SPY price levels, not return correlation.",new BenchmarkCorrelation(benchmark,Period.ofDays(1)));
            measure(g,"Treynor ratio / annual samples","ratio","SPY benchmark; risk-free rate = 0; library annual sampling convention.",new TreynorRatio(t->0.,benchmark,ReturnsCalculators.SIMPLE,yearly));
        }
        g="Value at risk and expected shortfall";
        for(double alpha:new double[]{.05,.01}) {
            String level=(alpha==.05?"95%":"99%");
            String note="Signed lower-tail trading-day account RETURN at alpha="+alpha+"; not positive USD loss. No clamping or sign reversal.";
            number(g,"Historical VaR / "+level,"HistoricalVaR","fraction",note,()->new HistoricalVaR(alpha,capital,ReturnsCalculators.SIMPLE).valueOf(daily.clone()));
            number(g,"Analytic normal VaR / "+level,"AnalyticVaR","fraction",note,()->new AnalyticVaR(alpha,capital,ReturnsCalculators.SIMPLE).valueOf(daily.clone()));
            number(g,"Conditional VaR / "+level,"ConditionalVaR","fraction",note,()->new ConditionalVaR(alpha,capital,ReturnsCalculators.SIMPLE).valueOf(daily.clone()));
        }
        if(includePeriodTables) for(int i=0;i<lengths.length;i++) {
            String prefix=names[i]; Period p=lengths[i]; LocalDateTimeInterval w=windows[i];
            periodTable(prefix+" returns","fraction",w,p,new PeriodByPeriodReturns(capital,ReturnsCalculators.SIMPLE));
            periodTable(prefix+" trading volume","USD",w,p,new PeriodByPeriodTradingVolume());
            periodTable(prefix+" information ratio","ratio",w,p,new PeriodByPeriodInformationRatio(capital,Period.ofDays(1),ReturnsCalculators.SIMPLE,0));
            if(benchmark!=null) periodTable(prefix+" excess returns vs SPY","fraction",w,p,new PeriodByPeriodExcessReturns(capital,benchmark,ReturnsCalculators.SIMPLE));
            else { PeriodTable table=new PeriodTable();table.label=prefix+" excess returns vs SPY";table.sourceClass="PeriodByPeriodExcessReturns";table.status="unavailable";table.detail=benchmarkError;periods.add(table); }
        }
    }

    private void measure(String group,String label,String unit,String note,PerformanceMeasure m) {
        number(group,label,m.getClass().getSimpleName(),unit,note,()->m instanceof NavMeasure
            ? ((NavMeasure)m).valueOf(nav)
            : m.valueOf(interval,capital,fees,fills,books,rates));
    }
    private void number(String group,String label,String source,String unit,String note,DoubleSupplier calc) {
        Metric m=new Metric();m.group=group;m.label=label;m.sourceClass=source;m.unit=unit;m.detail=note;
        try {
            double v=calc.getAsDouble();
            m.status=Double.isFinite(v)?"ok":"undefined";
            m.value=Double.isFinite(v)?v:null;
            m.display=Double.isNaN(v)?"N/A":v==Double.POSITIVE_INFINITY?"+Infinity":v==Double.NEGATIVE_INFINITY?"-Infinity":Double.toString(v);
            if(!Double.isFinite(v)) m.detail+=" Undefined/non-finite library output: insufficient samples, no losing observations, zero variance or zero denominator.";
        } catch(RuntimeException ex) {
            m.status="unavailable";m.display="N/A";m.detail+=" Library calculation unavailable ("+ex.getClass().getSimpleName()+").";
            System.err.println("Measure unavailable: "+source+" / "+label+" / "+ex.getClass().getSimpleName());
        }
        metrics.add(m);
    }
    private void unavailable(String group,String label,String source,String reason) {
        Metric m=new Metric();m.group=group;m.label=label;m.sourceClass=source;m.status="not_applicable";m.display="N/A";m.detail=reason; m.unit="—";metrics.add(m);
    }
    private void periodTable(String label,String unit,LocalDateTimeInterval w,Period p,PeriodByPeriodMeasure measure) {
        PeriodTable t=new PeriodTable();t.label=label;t.unit=unit;t.sourceClass=measure.getClass().getSimpleName();t.detail=PERIOD_NOTE;
        try {
            for(IntervalValue v:measure.values(w,p,fills,books,rates)) {
                PeriodValue row=new PeriodValue();row.start=v.interval().start().toString();row.end=v.interval().end().toString();
                row.status=Double.isFinite(v.value())?"ok":"undefined";row.value=Double.isFinite(v.value())?v.value():null;
                row.display=Double.isNaN(v.value())?"N/A":Double.toString(v.value());t.values.add(row);
            }
        } catch(RuntimeException ex) {
            t.status="unavailable";t.detail+=" Library period calculation unavailable ("+ex.getClass().getSimpleName()+").";
        }
        periods.add(t);
    }
}
