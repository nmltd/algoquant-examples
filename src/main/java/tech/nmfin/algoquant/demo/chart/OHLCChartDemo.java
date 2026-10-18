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
import dev.nm.nmutils.plot.api.ChartFactory;
import dev.nm.nmutils.plot.api.data.DataSeriesCollection;
import dev.nm.nmutils.plot.api.data.OhlcSeries;
import dev.nm.nmutils.plot.binder.ChartFactoryBinder;
import dev.nm.stat.timeseries.linear.univariate.arima.ARIMAModel;
import dev.nm.stat.timeseries.linear.univariate.arima.ARIMASim;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import tech.nmfin.algoquant.execution.component.chart.OHLCSeriesAdaptor;
import tech.nmfin.algoquant.model.signal.technical.ohlc.OHLCBars;
import tech.nmfin.algoquant.model.util.frequency.Minute;
import tech.nmfin.algoquant.util.annotation.Demo;

@Demo
public class OHLCChartDemo {

    public static void main(String[] args) throws IOException {
        ARIMASim ts1
            = new ARIMASim(
                new ARIMAModel(
                    new double[]{0.3, -0.02},
                    1,
                    new double[]{0.6}
                )
            );
        ARIMASim ts2
            = new ARIMASim(
                new ARIMAModel(
                    new double[]{0.3, -0.02},
                    1,
                    new double[]{0.6}
                )
            );

        ChartFactory factory = ChartFactoryBinder.getInstance().getChartFactory();
        OHLCBars ohlcBars1 = new OHLCBars(new Minute());
        OHLCBars ohlcBars2 = new OHLCBars(new Minute());
        LocalDateTime t0 = LocalDateTime.now();

        for (int i = 1; i <= 10000; ++i) {
            ohlcBars1.update(t0.plusSeconds(i), ts1.nextDouble());
            ohlcBars2.update(t0.plusSeconds(i), ts2.nextDouble());
        }

        DataSeriesCollection<OhlcSeries> dataset = new DataSeriesCollection<>();
        dataset.add("ARIMA 1", new OHLCSeriesAdaptor(ohlcBars1));
        dataset.add("ARIMA 2", new OHLCSeriesAdaptor(ohlcBars2));

        Chart chart = factory.createOHLCChart(dataset);
        chart.setTitle("OHLC Demo");
        chart.addAnnotation("label @ (1hr, 100)", 3600000, 100);
        chart.show();

        File dir = new File("log");
        dir.mkdir();
        chart.saveAsPNG(new File(dir, "ohlc.png"), 400, 300);
    }
}
