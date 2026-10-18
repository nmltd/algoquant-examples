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
public class XYSeriesChartDemo {

    public static void main(String[] args) throws IOException {
        Chart chart
            = ChartUtils.plotXYSeries(
                "XY Plot Demo",
                "x",
                "y",
                new String[]{"series 1", "series 2", "series 3", "series 4"},
                new double[][]{
                    {1, 3, 4, 6, 2, 3, 1, 3, 5, 3, 4, 6, 8, 3, 2, 4},
                    {4, 6, 8, 3, 2, 4, 1, 3, 4, 6, 2, 3, 1, 3, 5, 3},
                    {6, 5, 2, 3, 1, 3, 5, 4, 6, 8, 3, 2, 4, 1, 3, 2},
                    {3, 1, 3, 5, 4, 6, 8, 3, 2, 4, 1, 3, 2, 6, 5, 2}
                }
            );
        chart.show();

        File dir = new File("log");
        dir.mkdir();
        chart.saveAsPNG(new File(dir, "xy.png"), 400, 300);
    }
}
