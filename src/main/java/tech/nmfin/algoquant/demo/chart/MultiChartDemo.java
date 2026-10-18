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
import java.io.File;
import java.io.IOException;
import tech.nmfin.algoquant.execution.component.chart.ChartUtils;
import tech.nmfin.algoquant.util.annotation.Demo;

@Demo
public class MultiChartDemo {

    public static void main(String[] args) throws IOException {
        String title = "Multi-Chart Demo";
        String x = "x";

        double[] xAxis = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[][] xySeries
            = new double[][]{
                {1.0, 2.0, 3.0, 4.0, 5.0},
                {100.0, 300.0, 200.0, 400.0, 200.0},
                {1000, 5000, 2000, 2000, 2000}
            };

        Chart chart
            = ChartUtils.plotMultiChartAuto(
                title,
                x,
                new String[]{"a", "b", "c"},
                xAxis,
                xySeries
            );
        chart.show();

        File dir = new File("log");
        dir.mkdir();
        chart.saveAsPNG(new File(dir, "multi.png"), 400, 300);
    }
}
