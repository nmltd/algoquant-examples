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
package tech.nmfin.algoquant.demo.model.daspremont2008;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.strategy.MRBasketProvider;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.strategy.SimpleMRBasketProvider;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.strategy.MRBasket;
import dev.nm.nmutils.NMUtils;
import java.util.Arrays;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.execution.datatype.product.stock.SimpleStock;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.simulation.KagiMRBasketSimulation;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.simulation.MRSimParam;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * <p><b>Using this example.</b>
 * Creates mean-reverting baskets with SimpleMRBasketProvider and evaluates a basket through
 * KagiMRBasketSimulation. MRSimParamSP500 supplies the data and interval conventions; the
 * plotted P/L belongs to the selected basket and trading period.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * The paper searches for sparse portfolios with low autoregressive predictability, using
 * generalized eigenvalue formulations and sparse approximations. Basket selection and profitable
 * execution are separate questions.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * SimpleMRBasketProvider uses the configured price matrix, covariance penalty, clustering and
 * basket-selection windows. The demo selects the first basket and passes it to
 * KagiMRBasketSimulation for a trading interval. The Kagi simulation and S&amp;P 500 settings are
 * implementation choices; the example does not demonstrate every greedy/semidefinite method in
 * the paper.
 * </p>
 * @see <a href="https://www.di.ens.fr/~aspremon/PDF/MeanRevVec.pdf">A. d’Aspremont (2008 preprint; published 2011), Identifying Small Mean Reverting Portfolios, Quantitative Finance 11(3), 351–364</a>
 */
@Demo
public class KagiMRBasketSimulationDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        MRSimParam param = new MRSimParamSP500();
        KagiMRBasketSimulation simulation = new KagiMRBasketSimulation(param);

        MRBasketProvider basketProvider
            = new SimpleMRBasketProvider(
                param.priceMatrixFactory(),
                (s) -> new SimpleStock(s, param.currency(), param.exchange()),
                param.symbolPool(),
                param.covPenalty(),
                param.inSamplePeriodForClustering(),
                param.inSamplePeriodForBasketSelection(),
                param.basketCount(),
                param.basketSize()
            );

        LocalDateTimeInterval tradingInterval
            = LocalDateTimeInterval.of(
                param.tradingInterval().start(),
                param.basketRenewalPeriod()
            );
        MRBasket basket
            = basketProvider.basketsAtTime(tradingInterval.start()).get(0);

        double[] pnl = simulation.simulateBasket(basket, tradingInterval);

        LOGGER.info("pnl series = {}", Arrays.toString(pnl));
        ChartUtils.plotXYSeries(
            "Kagi Mean Reversion",
            "time",
            "profit",
            new String[]{"P/L"},
            new double[][]{pnl}
        ).show();
    }
}
