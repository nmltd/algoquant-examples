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
package tech.nmfin.algoquant.demo.model.kst1995;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.nmutils.NMUtils.getClassLogger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooEODCacheFactory;
import tech.nmfin.algoquant.execution.datatype.StockEOD;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.datatype.product.stock.hkex.HSI;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.model.trend.kst1995.GMA12;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;
import tech.nmfin.trend.kst1995.KnightSatchellTran1995;
import tech.nmfin.trend.kst1995.KnightSatchellTran1995MLE;

/**
 * Demonstrates Acar's framework of a trend following strategy. We first fit the
 * Acar's model with some real price data. Then we compute the properties of the
 * model, e.g., the expected P&L for playing the GMA(1, 2) strategy.
 *
 * @author Haksun Li
 */
@Demo
public class GMA12Demo {

    private static final Logger LOGGER = getClassLogger();

    public static void main(String[] args) {
        // trading horizon
        LocalDateTime begin = LocalDateTime.of(2009, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2010, 12, 31, 0, 0);
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        // setup data source
        Stock stock = HSI.getInstance();
        YahooEODCacheFactory yahoo
            = new YahooEODCacheFactory(SimTemplateYahooEOD.DEFAULT_DATA_FOLDER);

        List<Double> px = new ArrayList<>();
        SequentialCache<StockEOD> cache = yahoo.newInstance(stock, interval);
        for (TimedEntry<StockEOD> entry : cache) {
            px.add(entry.data().adjClose());
        }

        double[] Rt = new double[px.size() - 1];
        for (int i = 0; i < Rt.length; ++i) {
            Rt[i] = ReturnsCalculators.LOG.computeReturn(px.get(i), px.get(i + 1));
        }

        KnightSatchellTran1995 model1
            = new KnightSatchellTran1995MLE().fitModel(Rt);
        LOGGER.info(model1.toString());

        GMA12 gma12 = new GMA12(model1);
        LOGGER.info(
            "the probability of holding time = 1: {}",
            gma12.PrHoldingTime(1)
        );
        LOGGER.info(
            "the expected pnl for a long position: {}",
            gma12.ERRLong()
        );
    }
}
