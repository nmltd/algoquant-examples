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
 * model, e.g., the expected P&amp;L for playing the GMA(1, 2) strategy.
 *
 *
 * <p><b>Using this example.</b>
 * Loads Yahoo prices, fits a Knight-Satchell-Tran model and evaluates GMA(1,2) trend-following
 * properties. The output describes the fitted model; it is not automatically the return of a
 * fully invested trading account.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Knight–Satchell–Tran is the fitted return-model reference. Acar–Satchell is related moving-
 * average analysis, not a claim that its switching-Gaussian derivation is identical to this
 * GMA12 implementation; both originals were unavailable in full during this audit.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Convert Yahoo adjusted closes to log returns, call KnightSatchellTran1995MLE.fitModel, then
 * construct GMA12. PrHoldingTime(1) and ERRLong() are model-derived quantities, not realized
 * Sharpe ratios or total portfolio returns. Fitting an asymmetric return law does not by itself
 * establish stable out-of-sample trend profitability.
 * </p>
 * @author Haksun Li
 * @see <a href="https://doi.org/10.1080/13504869500000009">J. L. Knight, S. E. Satchell and K. C. Tran (1995), Statistical modelling of asymmetric risk in asset returns, Applied Mathematical Finance 2(3), 155–172</a>
 * @see <a href="https://doi.org/10.1080/135048697334791">E. Acar and S. Satchell (1997), A theoretical analysis of trading rules: an application to the moving average case with Markovian returns, Applied Mathematical Finance 4(3), 165–180</a>
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
