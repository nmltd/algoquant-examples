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
package tech.nmfin.algoquant.demo.model.infantino2010;

import dev.nm.algebra.linear.matrix.MatrixAccessException;
import dev.nm.algebra.linear.matrix.doubles.Matrix;
import dev.nm.algebra.linear.matrix.doubles.operation.MatrixFactory;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.misc.parallel.*;
import dev.nm.nmutils.NMUtils;
import dev.nm.nmutils.csvzip.CsvZipFileWriter;
import static dev.nm.number.DoubleUtils.collection2DoubleArray;
import dev.nm.stat.descriptive.covariance.Covariance;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.*;
import tech.nmfin.algoquant.data.cache.processor.filter.CacheFilter;
import tech.nmfin.algoquant.data.cache.processor.filter.NoNullDataFilter;
import tech.nmfin.algoquant.data.historicaldata.yahoo.MultipleYahooEOD;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.model.signal.infantino2010.DynamicInfantino2010PCA;
import tech.nmfin.algoquant.model.util.movingwindow.MovingWindowOfReturns;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.returns.ReturnsCalculators;

/**
 * Analyzes the correlation between predicted returns and realized returns to
 * check if the prediction algorithm has any prediction power.
 *
 * <p><b>Using this example.</b>
 * Compares predicted and realized returns for the Infantino model and writes correlation
 * analysis to log/pca-corr.csv.zip. This is forecast analysis rather than a funded portfolio
 * simulation; a prediction correlation is not an account return.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * PCA supplies a low-dimensional systematic-return representation for an accumulated-return
 * regression. This example studies the forecasts on daily data, unlike the thesis’s intraday
 * experiment.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * DynamicInfantino2010PCA receives T=500, k=2 and H=120 by default; N follows setupStocks().
 * MovingWindowOfReturns creates realized log-return comparisons and Covariance accumulates
 * prediction/realization diagnostics. The CSV is a forecast-diagnostic output, not simulated
 * account NAV. Overlapping H-period observations can be serially dependent; correlation alone
 * does not establish tradable performance.
 * </p>
 * @see <a href="https://dspace.mit.edu/handle/1721.1/59122">L. R. Infantino and S. Itzhaki (2010), Developing high-frequency equities trading models, MIT MBA thesis</a>
 */
@Demo
public class Infantino2010PredictionAnalysisDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final String OUTPUT_FILE = "./log/pca-corr.csv.zip";

    public static void main(
        String[] args
    ) throws MultipleExecutionException, IOException {
        LocalDateTime begin = LocalDateTime.of(2009, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2012, 1, 1, 0, 0);
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        Infantino2010PredictionAnalysisDemo analysis
            = new Infantino2010PredictionAnalysisDemo();
        analysis.run(setupStocks(), interval);
    }

    public static List<Stock> setupStocks() {
        List<Stock> stocks;
        /*
         * 10 choosen stocks
         */
        stocks = new ArrayList<>(TestData.TEN_CHOOSEN.stocks());

        /*
         * stocks in IT sector
         */
//        stocks = TestData.SP500_IT.getStocks();

        /*
         * all SP500 stocks
         */
//        stocks = TestData.SP500.getStocks();

        /*
         * all SP500 stocks + the index
         */
//        stocks = new ArrayList<>(TestData.SP500_PLUS_GSPC.stocks());
        return stocks;
    }

    public void run(
        List<Stock> stocks,
        LocalDateTimeInterval interval
    ) throws MultipleExecutionException, IOException {
        MultipleYahooEOD eod
            = new MultipleYahooEOD(
                stocks.toArray(new Stock[stocks.size()]),
                interval,
                "./log",
                "./log"
            );
        CacheFilter<VectorCache.Vector<Double>> filter
            = new NoNullDataFilter<>();
        SequentialCache<VectorCache.Vector<Double>> cache
            = filter.process(eod);

        final int T = 500;
        final int N = stocks.size();
        final int k = 2;
//        int H = 120;
        int Hstart = 120;
        int Hend = 121; // exclusive
        int Hincrement = 10;

        final Map<Integer, List<Covariance>> H2Covariances
            = new ConcurrentSkipListMap<>();
        new ParallelExecutor("test-correlation").forLoop(Hstart, Hend, Hincrement, new LoopBody() {
            @Override
            public void run(int H) throws Exception {
                LOGGER.info("H = {}", H);
                try {
                    DynamicInfantino2010PCA pca
                        = new DynamicInfantino2010PCA(T, N, H, k, true);

                    List<Vector> S_hat_series = new LinkedList<>();
                    MovingWindowOfReturns realizedReturns
                        = new MovingWindowOfReturns(
                            ReturnsCalculators.LOG,
                            N
                        );
                    int k = 0;
                    for (TimedEntry<VectorCache.Vector<Double>> entry : cache) {
                        LOGGER.info("[{}] {}", ++k, entry.toString());
                        double[] prices = collection2DoubleArray(entry.data().asList());
                        realizedReturns.addPrices(prices);
                        DynamicInfantino2010PCA.Signal results = pca.update(prices);

                        if (results == null) {
                            continue;
                        }
                        Vector S_hat = results.S_t_hat(T - H); // the predicted cumulative returns for the next H-period
                        S_hat_series.add(S_hat);
                    }

                    Matrix logReturnMatrix = realizedReturns.toMatrix();

                    // find the correlation between the realized and the predicted H-period cumulative returns
                    Matrix cumLogReturnSeries
                        = sumUpLastRows(
                            logReturnMatrix,
                            T + H,
                            logReturnMatrix.nRows(),
                            H
                        );
                    Matrix expCumLogReturnSeries
                        = MatrixFactory.rbind(
                            S_hat_series.subList(
                                0,
                                S_hat_series.size() - H
                            )
                        );

                    List<Covariance> covariances = new ArrayList<>(N);
                    for (int i = 1; i <= N; ++i) {
                        double[] realized
                            = cumLogReturnSeries.getColumn(i).toArray();
                        double[] predicted
                            = expCumLogReturnSeries.getColumn(i).toArray();

                        Covariance covariance = new Covariance(realized, predicted);
                        covariances.add(covariance);
                    }

                    H2Covariances.put(H, covariances);
                } catch (MatrixAccessException ex) {
                    ex.printStackTrace();
                }
            }
        });

        writeCorrelationToFile(H2Covariances, stocks);
    }

    private Matrix sumUpLastRows(
        Matrix A,
        int startRow,
        int endRow,
        int nRows
    ) {
        List<Vector> rows = new ArrayList<>(endRow - startRow + 1);
        for (int i = startRow; i <= endRow; ++i) {
            rows.add(DynamicInfantino2010PCA.sumUpLastRows(A, i, nRows));
        }
        return MatrixFactory.rbind(rows);
    }

    private void writeCorrelationToFile(
        Map<Integer, List<Covariance>> H2Covariances,
        List<Stock> equities
    ) throws IOException {
        String[] headers = {"H", "stock", "covariance", "correlation", "N"};
        CsvZipFileWriter out
            = new CsvZipFileWriter(
                OUTPUT_FILE,
                headers,
                Charset.forName("UTF-8").newEncoder(),
                null
            );

        for (Map.Entry<Integer, List<Covariance>> entry : H2Covariances.entrySet()) {
            int H = entry.getKey();
            int i = 0;
            for (Covariance covariance : entry.getValue()) {
                out.writeValues(
                    H,
                    equities.get(i++),
                    covariance.value(),
                    covariance.correlation(),
                    covariance.N()
                );
            }
        }

        out.close();
    }
}
