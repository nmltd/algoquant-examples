/*
 * Copyright (c) Numerical Method Inc.
 * http://www.numericalmethod.com/
 *
 * THIS SOFTWARE IS LICENSED, NOT SOLD.
 *
 * YOU MAY USE THIS SOFTWARE ONLY AS DESCRIBED IN THE LICENSE.
 * IF YOU ARE NOT AWARE OF AND/OR DO NOT AGREE TO THE TERMS OF THE LICENSE,
 * DO NOT USE THIS SOFTWARE.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITH NO WARRANTY WHATSOEVER,
 * EITHER EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION,
 * ANY WARRANTIES OF ACCURACY, ACCESSIBILITY, COMPLETENESS,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABILITY, NON-INFRINGEMENT,
 * TITLE AND USEFULNESS.
 *
 * IN NO EVENT AND UNDER NO LEGAL THEORY,
 * WHETHER IN ACTION, CONTRACT, NEGLIGENCE, TORT, OR OTHERWISE,
 * SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIMS, DAMAGES OR OTHER LIABILITIES,
 * ARISING AS A RESULT OF USING OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tech.nmfin.algoquant.demo.model.factormodel;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import tech.nmfin.algoquant.data.cache.BufferCache;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODCacheFactory;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.execution.Execution;
import tech.nmfin.algoquant.execution.datatype.fxrate.ExchangeRateTable;
import tech.nmfin.algoquant.execution.datatype.fxrate.NoRateTable;
import tech.nmfin.algoquant.execution.datatype.orderbook.cache.OrderBookCaches;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HKEXStock;
import tech.nmfin.algoquant.execution.performance.measure.BenchmarkLookup;
import tech.nmfin.algoquant.execution.performance.measure.byperiod.IntervalValue;
import tech.nmfin.algoquant.execution.performance.measure.byperiod.PeriodByPeriodMeasure;

/**
 * <p><b>Using this example.</b>
 * Supports factor-model demonstrations with benchmark and report utilities. Read the callers
 * for the chosen currency and measurement period; a benchmark lookup is a comparison series,
 * not an independently simulated strategy account.
 * </p>
 */
public class FactorModelUtils {

    public static BenchmarkLookup newBenchmark(String symbol, LocalDateTimeInterval interval) {
        BufferCache<StockEOD> cache
            = BufferCache.fromSequentialCache(
                new YahooEODCacheFactory().newInstance(
                    HKEXStock.newInstance(symbol),
                    LocalDateTimeInterval.of(
                        interval.start().minusMonths(1),
                        interval.end()
                    )
                )
            );
        return (LocalDateTime t) -> cache.get(t).data().adjClose();
    }

    public static Map<PeriodByPeriodMeasure, List<IntervalValue>> byPeriodMeasures(
        List<Execution> executions,
        OrderBookCaches orderBookCaches,
        LocalDateTimeInterval interval,
        Period period,
        List<PeriodByPeriodMeasure> byPeriodMeasures
    ) {
        ExchangeRateTable rates = new NoRateTable();
        Map<PeriodByPeriodMeasure, List<IntervalValue>> measureValues
            = new LinkedHashMap<>();
        for (PeriodByPeriodMeasure measure : byPeriodMeasures) {
            measureValues.put(
                measure,
                measure.values(
                    interval,
                    period,
                    executions,
                    orderBookCaches,
                    rates
                )
            );
        }
        return measureValues;
    }
}
