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
package tech.nmfin.algoquant.demo.chart;

import dev.nm.nmutils.plot.api.Chart;
import dev.nm.stat.random.rng.RNGUtils;
import dev.nm.stat.timeseries.datastructure.univariate.realtime.inttime.SimpleTimeSeries;
import dev.nm.stat.timeseries.linear.univariate.stationaryprocess.garch.GARCHModel;
import dev.nm.stat.timeseries.linear.univariate.stationaryprocess.garch.GARCHSim;
import java.io.File;
import java.io.IOException;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.util.annotation.Demo;

@Demo
public class TimeSeriesChartDemo {

    public static void main(String[] args) throws IOException {
        GARCHSim ts1
            = new GARCHSim(
                new GARCHModel(
                    0.2,
                    new double[]{0.3},
                    new double[]{0.6}
                )
            );
        GARCHSim ts2
            = new GARCHSim(
                new GARCHModel(
                    0.4,
                    new double[]{0.3},
                    new double[]{0.6}
                )
            );

        Chart chart
            = ChartUtils.plotTimeSeries(
                "TimeSeries Chart Demo",
                new String[]{"Garch (0.2, 0.3, 0.6)", "Garch (0.4, 0.3, 0.6)"},
                new SimpleTimeSeries[]{
                    new SimpleTimeSeries(RNGUtils.nextN(ts1, 1000)),
                    new SimpleTimeSeries(RNGUtils.nextN(ts2, 1000))
                }
            );
        chart.show();

        File dir = new File("log");
        dir.mkdir();
        chart.saveAsPNG(new File(dir, "ts.png"), 400, 300);
    }
}
