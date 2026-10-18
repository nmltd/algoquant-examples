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

import dev.nm.nmutils.NMUtils;
import static java.util.Collections.singletonList;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.execution.performance.report.PerformanceReport;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.simulation.MRSimParam;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.simulation.MultiPeriodKagiMRStrategySim;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.strategy.MRBasket;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.util.BasketAnalysisUtils;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * <p><b>Using this example.</b>
 * Runs MultiPeriodKagiMRStrategySim using MRSimParamSP500 and inspects the resulting basket
 * analysis and performance report. Basket construction and subsequent trading are separate
 * stages; review their data windows before adapting the example.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Sparse mean-reverting portfolio construction motivates the basket search. The paper’s
 * predictability criterion is not itself a trading return.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * MultiPeriodKagiMRStrategySim repeatedly combines selection and trading according to
 * MRSimParamSP500, then exposes a PerformanceReport and basket diagnostics. Basket renewal, Kagi
 * execution and the chosen universe are application settings; compare estimation windows with
 * trade dates to avoid future information.
 * </p>
 * @see <a href="https://www.di.ens.fr/~aspremon/PDF/MeanRevVec.pdf">A. d’Aspremont (2008 preprint; published 2011), Identifying Small Mean Reverting Portfolios, Quantitative Finance 11(3), 351–364</a>
 */
@Demo
public class MultiPeriodKagiMRStrategyDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) {
        MRSimParam param = new MRSimParamSP500();

        MultiPeriodKagiMRStrategySim simulation
            = new MultiPeriodKagiMRStrategySim(param);

        List<MRBasket> baskets
            = simulation.preComputeBaskets(singletonList(param));
        new BasketAnalysisUtils()
            .saveBasketSeries(
                baskets,
                param.basketRenewalPeriod(),
                param.priceMatrixFactory()
            );

        PerformanceReport report = simulation.run(param);
        LOGGER.info(report.toString());
    }

}
