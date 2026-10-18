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
import dev.nm.algebra.linear.matrix.doubles.factorization.triangle.cholesky.Chol;
import dev.nm.algebra.linear.matrix.doubles.factorization.triangle.cholesky.Cholesky;
import dev.nm.algebra.linear.matrix.doubles.matrixtype.dense.DenseMatrix;
import dev.nm.algebra.linear.matrix.doubles.operation.MatrixFactory;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.stat.random.Seedable;
import dev.nm.stat.random.rng.univariate.RandomNumberGenerator;
import dev.nm.stat.random.rng.univariate.normal.NormalRNG;

/**
 * Generates a random return matrix.
 *
 * @author Haksun Li
 */
public class RandomReturns implements Seedable {

    private final RandomNumberGenerator rng2 = new NormalRNG(0., 1.);
    private RandomNumberGenerator rng1; // returns distribution

    public RandomReturns(
            RandomNumberGenerator rng1 // TODO: returnsRNG
    ) {
        this.rng1 = rng1;
    }

    // returns follow a Normal distribution
    public RandomReturns() {
        this(new NormalRNG(0., 1.)); // TOOD: use rng2?
    }

    public Matrix getRandomCovariance(int N) {
        Matrix S = MatrixFactory.randomPositiveDefiniteMatrix(N, rng2);
        return S;
    }

    public Matrix getRandomReturns(Matrix S, int T) {
        final int N = S.nRows();

        final Cholesky chol = new Chol(S, 1e-8);
        final Matrix L = chol.L();

        Matrix returns = new DenseMatrix(T, N);
        for (int i = 1; i <= T; i++) {
            Vector r0 = new DenseVector(N);
            for (int j = 1; j <= N; j++) {
                r0.set(j, rng1.nextDouble() * Math.signum(rng2.nextDouble())); // uncorrelated
            }

            Vector r1 = L.multiply(r0); // correlated

            for (int j = 1; j <= N; j++) {
                returns.set(i, j, r1.get(j));
            }
        }

        return returns;
    }

    public Matrix getRandomReturns(int N, int T) {
        return getRandomReturns(getRandomCovariance(N), T);
    }

    @Override
    public final void seed(long... seeds) {
        if (seeds.length == 1) {
            rng1.seed(seeds[0]);
            rng2.seed(seeds[0]);
        }

        rng1.seed(seeds[0]);
        rng2.seed(seeds[1]);
    }

}
