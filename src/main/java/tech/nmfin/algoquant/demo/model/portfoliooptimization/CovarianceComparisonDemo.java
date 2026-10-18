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
import dev.nm.algebra.linear.matrix.doubles.factorization.eigen.EigenDecomposition;
import dev.nm.algebra.linear.matrix.doubles.operation.MatrixFactory;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.nmutils.NMUtils;
import dev.nm.stat.covariance.LedoitWolf2004;
import dev.nm.stat.covariance.nlshrink.LedoitWolf2016;
import dev.nm.stat.descriptive.covariance.SampleCovariance;
import dev.nm.stat.random.rng.univariate.RandomLongGenerator;
import dev.nm.stat.random.rng.univariate.RandomNumberGenerator;
import dev.nm.stat.random.rng.univariate.beta.Cheng1978;
import dev.nm.stat.random.rng.univariate.exp.Ziggurat2000Exp;
import dev.nm.stat.random.rng.univariate.gamma.XiTanLiu2010b;
import dev.nm.stat.random.rng.univariate.normal.NormalRNG;
import dev.nm.stat.random.rng.univariate.uniform.UniformRNG;
import java.util.Arrays;
import org.slf4j.Logger;

/**
 *
 *
 * <p><b>Using this example.</b>
 * Compares covariance calculations for the data assembled in main. Keep the observations-by-
 * assets orientation consistent across estimators; covariance estimates are inputs to
 * portfolio optimization, not strategy performance measures.
 * </p>
 * @author haksunli
 */
public class CovarianceComparisonDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    private final RandomNumberGenerator rnorm1 = new NormalRNG(0., 1.);
    private final RandomLongGenerator uniform1 = new UniformRNG();

    public CovarianceComparisonDemo(long seeds) {
        rnorm1.seed(seeds);
        uniform1.seed(seeds);
    }

    public static void main(String[] args) throws Exception {
        new CovarianceComparisonDemo(1234567890L).run();
    }

    /**
     * Expected returns in terms of estimation error are:
     *
     * sample covariance > linear shrunk covariance > non linear shrunk
     * covariance
     */
    private void run() {
        double[] distance = compare(10, 10, new NormalRNG(0., 30.)); // normal distribution
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(10, 10, new Cheng1978(2.5, 4.5 / 30, uniform1)); // beta distibution 
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(10, 30, new NormalRNG(0., 300.));
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(10, 30, new Cheng1978(2.5, 4.5 / 300, uniform1));
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(30, 30, new NormalRNG(0., 30.));
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(30, 60, new XiTanLiu2010b(0.9, uniform1)); // gamma distribution
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(30, 100, new NormalRNG(0., 300.));
        LOGGER.info("distances/errors: " + Arrays.toString(distance));

        distance = compare(30, 100, new Ziggurat2000Exp()); // exponential distribution
        LOGGER.info("distances/errors: " + Arrays.toString(distance));
    }

    private double[] compare(int N, int T, RandomNumberGenerator rng1) {
//        Matrix Cov1 = new DenseMatrix(N, N).ONE(); // uncorrelated
        Matrix Cov1 = MatrixFactory.randomPositiveDefiniteMatrix(N, rnorm1);
//        System.out.println(Cov1);
        Matrix returns = randomReturnMatrix(Cov1, T, rng1);

        // in sample covariance matrix
        Matrix S = new SampleCovariance(returns);
//        System.out.println(S);
        double distance1 = distance(Cov1, S);
//        LOGGER.info(String.format("error using sample covariance matrix = %f", distance1));

        // linear shrunk covariance matrix
        Matrix lS = new LedoitWolf2004().compute(returns).getCovarianceMatrix();
//        System.out.println(lS);
        double distance2 = distance(Cov1, lS);
//        LOGGER.info(String.format("error using linear shrunk covariance matrix = %f", distance2));

        // nonlinear shrunk covariance matrix
        Matrix nlS = new LedoitWolf2016().estimate(returns).getShrunkCovarianceMatrix();
//        System.out.println(nlS);
        double distance3 = distance(Cov1, nlS);
//        LOGGER.info(String.format("error using nonlinear shrunk covariance matrix = %f", distance3));

        return new double[]{distance1, distance2, distance3};
    }

    private double distance(Matrix Cov, Matrix S) {
//        return MatrixMeasure.Frobenius(Cov.minus(S));

        EigenDecomposition eigen1 = new EigenDecomposition(Cov);
        double[] d1 = eigen1.D().getDiagonal().toArray();
        Arrays.sort(d1);

        EigenDecomposition eigen2 = new EigenDecomposition(S);
        double[] d2 = eigen2.D().getDiagonal().toArray();
        Arrays.sort(d2);

        Vector v1 = new DenseVector(d1);
        Vector v2 = new DenseVector(d2);

        double distance = v1.minus(v2).norm();
        return distance;
    }

    /**
     * Generates a random return matrix.
     *
     * @param Cov the true covariance matrix
     * @param T the number of observations
     * @param var a variance scaling factor
     * @return a random return matrix
     */
    private Matrix randomReturnMatrix(Matrix Cov, int T, RandomNumberGenerator rng1) {
        RandomReturns randomReturns = new RandomReturns(rng1);
        randomReturns.seed(12345678902L, 12345678902L);
        Matrix sample = randomReturns.getRandomReturns(Cov, T);
        return sample;
    }

}
