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
import dev.nm.algebra.linear.matrix.doubles.matrixtype.dense.DenseMatrix;
import dev.nm.algebra.linear.matrix.doubles.operation.MatrixUtils;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.analysis.function.rn2r1.QuadraticFunction;
import dev.nm.misc.PrecisionUtils;
import dev.nm.nmutils.NMUtils;
import dev.nm.number.DoubleUtils;
import dev.nm.solver.multivariate.constrained.constraint.linear.LinearConstraints;
import dev.nm.solver.multivariate.constrained.constraint.linear.LinearEqualityConstraints;
import dev.nm.solver.multivariate.constrained.convex.sdp.socp.problem.portfoliooptimization.PortfolioRiskExactSigma;
import dev.nm.solver.multivariate.constrained.convex.sdp.socp.problem.portfoliooptimization.SOCPRiskConstraint;
import dev.nm.solver.multivariate.constrained.convex.sdp.socp.qp.*;
import dev.nm.stat.descriptive.covariance.SampleCovariance;
import dev.nm.stat.random.rng.univariate.RandomNumberGenerator;
import dev.nm.stat.random.rng.univariate.normal.NormalRNG;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import tech.nmfin.algoquant.model.portfoliooptimization.socp.SimpleSOCPPortfolio;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * We compare the results from a QP solver (Markowitz) and from an SOCP solver.
 * They should give more or less the same results given only QP constraints,
 * subject to numerical differences.
 *
 * The portfolio optimization problem is:
 * <blockquote><pre>
 * max r*w-q*w'*Sigma*w
 * s.t. sum(w)=1, w_i=1 for i in the black list.
 * </pre></blockquote>
 *
 * We assume that the last index of w is in the black list.
 */
// TODO (haksunli): why the two approaches only agree when the returns are independent normals?
@Demo
public class SOCPMarkowitzComparisonDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();

    public static void main(String[] args) throws Exception {
        final int n = 5; // the number of stocks
        final int T = 1000;

        // parameter set up
        Matrix returns = randomNormalReturnMatrix(n, T);
        Vector r_bar = MatrixUtils.colMeanVector(returns);
        Matrix Sigma = new SampleCovariance(returns);
        double riskAverseCoeff = 0.2;

        SOCPMarkowitzComparisonDemo instance
                = new SOCPMarkowitzComparisonDemo(n, r_bar, Sigma, riskAverseCoeff);

        // Compute the optimal weights by an SOCP solver.
        Vector x1 = instance.getSOCPSolution();

        // Computes the optimal weights by an QP solver.
        Vector x2 = instance.getMarkowitzSolution();

        // Compute the utility functions.
        double obj1 = instance.getUtilityValue(x1);
        double obj2 = instance.getUtilityValue(x2);

        // Check results.
        LOGGER.info("x1: {}", x1);
        LOGGER.info("x2: {}", x2);
        LOGGER.info("obj1 {} vs. obj2 {}", obj1, obj2);
    }

    private final int n;
    private final Vector r_bar;
    private final Matrix Sigma;
    private final double riskAverseCoeff;

    public SOCPMarkowitzComparisonDemo(
            int n,
            Vector r_bar,
            Matrix Sigma,
            double riskAverseCoeff
    ) {
        this.n = n;
        this.r_bar = r_bar;
        this.Sigma = Sigma;
        this.riskAverseCoeff = riskAverseCoeff;
    }

    public Vector getSOCPSolution() throws Exception {
        double lambda_r = riskAverseCoeff; // the portfolio risk parameter

        int k = 1; // the number of sectors
        List<Integer> blacklist = new ArrayList<>();
        for (int i = 1; i <= k; i++) {
            blacklist.add(i * n / k); // the last stock in each sector is black listed
        }

        SOCPRiskConstraint risk = new PortfolioRiskExactSigma(Sigma);

        SimpleSOCPPortfolio socp
                = new SimpleSOCPPortfolio(r_bar, lambda_r, risk, blacklist);
        Vector x = socp.getOptimalWeights();
        return x;
    }

    public Vector getMarkowitzSolution() throws QPInfeasible {
        Matrix A = new DenseMatrix(1, n);
        A.set(1, n, 1.); // the black list constraint, only last index is in the black list
        LinearEqualityConstraints blackList
                = new LinearEqualityConstraints(A, new DenseVector(0.));

        // w' 1 = 1
        LinearEqualityConstraints unity
                = new LinearEqualityConstraints(
                        new DenseMatrix(
                                DoubleUtils.rep(1.0, r_bar.size()),
                                1,
                                r_bar.size()),
                        new DenseVector(1.));

        LinearEqualityConstraints equalities
                = (LinearEqualityConstraints) LinearConstraints.concat(unity, blackList);
        Matrix H1 = Sigma.scaled(2. * riskAverseCoeff);
        Vector p = r_bar.scaled(-1.);
        QuadraticFunction f = new QuadraticFunction(H1, p);

        double epsilon = Math.sqrt(PrecisionUtils.autoEpsilon(H1));
        QPSolution soln = QPSimpleMinimizer.solve(f, equalities, epsilon);
        Vector x = soln.minimizer();
        return x;
    }

    /**
     * Computes the utility value given the optimal weights.
     *
     * @param x the optimal weights
     * @return the value of the utility function
     */
    public double getUtilityValue(Vector x) {
        double ret = x.innerProduct(r_bar);
        double risk = x.innerProduct(Sigma.multiply(x));
        return ret - riskAverseCoeff * risk;
    }

    /**
     * Generates a random return matrix, whose entries follow the standard
     * Normal distribution.
     *
     * @param N the number of stocks
     * @param T the number of observations
     * @return a random return matrix
     */
    public static Matrix randomNormalReturnMatrix(int N, int T) {
        RandomNumberGenerator rng = new NormalRNG(0., 1.);
        rng.seed(1234567890L);

        Matrix returns = new DenseMatrix(T, N);
        for (int i = 1; i <= T; i++) {
            Vector r0 = new DenseVector(N);
            for (int j = 1; j <= N; j++) {
                returns.set(i, j, rng.nextDouble());
            }
        }
        return returns;
    }

}
