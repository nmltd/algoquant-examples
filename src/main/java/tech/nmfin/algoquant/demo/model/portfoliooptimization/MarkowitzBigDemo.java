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
package tech.nmfin.algoquant.demo.model.portfoliooptimization;

import dev.nm.algebra.linear.matrix.doubles.Matrix;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.nmutils.NMUtils;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import static tech.nmfin.algoquant.demo.model.portfoliooptimization.PortfolioOptimComparisons1Demo.getMarkowitz;
import static tech.nmfin.algoquant.demo.model.portfoliooptimization.PortfolioOptimComparisons1Demo.randomReturnMatrix;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;

/**
 * This demo shows the performance/speed of running our implementation of
 * Markowitz algorithm on a large pool of assets, e.g., N = 10000.
 *
 *
 * <p><b>Using this example.</b>
 * Benchmarks Markowitz optimization on a large generated asset universe. Increase the asset
 * count only after checking memory and solver runtime; this demonstrates numerical scaling,
 * not historical investment performance.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Related theory and the scope of this example are distinguished below.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Benchmarks the optimizer on generated high-dimensional inputs. Its asset count exercises
 * numerical scaling; no historical strategy is simulated, and runtime or feasibility says
 * nothing about out-of-sample returns.
 * </p>
 * @author Haksun Li
 * @see <a href="https://doi.org/10.1111/j.1540-6261.1952.tb01525.x">H. Markowitz (1952), Portfolio Selection, Journal of Finance 7(1), 77–91</a>
 */
@Demo
public class MarkowitzBigDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final double RISK_ADVERSE = 20.;
//    private static final double CORVALAN_RELAXATION = 1.2;

    public static void main(String[] args) throws Exception {
        MarkowitzBigDemo demo
            = new MarkowitzBigDemo();
        demo.run();
    }

    private void run() throws Exception {
        Matrix returns = randomReturnMatrix(1000, 5000);

        final Vector w0 = new DenseVector(returns.nCols());

        /**
         * This is the most popular portfolio optimizer based of Prof.
         * Markowitz's 1952 paper on modern portfolio theory (MPT). 1000+ papers
         * and 60 years experience demonstrate that this theory has numerious
         * problems. It has no practical value and should not be used for real
         * world asset allocation.
         */
        PortfolioOptimizationAlgorithm model
            = getMarkowitz(RISK_ADVERSE);
        long start = System.nanoTime();
        // The returned vector follows the input asset-column order; these are weights, not orders.
        Vector w1 = model.getOptimalWeights(returns, w0, null, null);
        long end = System.nanoTime();
        long elapsed1 = end - start;
        long elapsed2 = TimeUnit.NANOSECONDS.toMillis(elapsed1);
        LOGGER.info("elapsed = {}ms; Markowitz model weights: {}", elapsed2, w1.toString());
    }

}
