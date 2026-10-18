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
