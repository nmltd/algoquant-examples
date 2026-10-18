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
import dev.nm.stat.random.rng.univariate.RandomNumberGenerator;
import dev.nm.stat.random.rng.univariate.beta.Cheng1978;
import dev.nm.stat.random.rng.univariate.uniform.UniformRNG;
import org.slf4j.Logger;
import tech.nmfin.algoquant.model.portfoliooptimization.corvalan2005.Corvalan2005Diversifier;
import tech.nmfin.algoquant.model.portfoliooptimization.MarkowitzOptimizationAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.SOCPOptimizationAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.blacklitterman.BlackLittermanOptimizationAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.EqualCorAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.EqualCorEstimator;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.EqualMeanAlgorithm;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.EqualMeanEstimator;
import tech.nmfin.algoquant.model.portfoliooptimization.smartbeta.SmartBetaAlgorithm;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.portfoliooptimization.Lai2010OptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.corvalan2005.Corvalan2005;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.GARCHResamplerFactory2;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.ReturnsResamplerFactory;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizer;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerLongOnly;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerShrankMean;
import tech.nmfin.returns.moments.MomentsEstimatorLedoitWolf;
import tech.nmfin.returns.moments.ReturnsMoments;

/**
 * This demo shows how to build customized portfolio optimization algorithms and
 * call these optimizers for the same data set.
 *
 * AQ has a suite of tools to do asset allocation/portfolio optimization. It
 * comes with out-of-box ready to use tools and users can also put together
 * different models to customize their own optimizers. This demo shows a few
 * simple out-of-box optimizers.
 *
 * The simulated returns are from beta distribution and are correlated so the
 * regular smart beta algorithms don't exhibit usual behaviors when uncorrelated
 * normal distributions are assumed.
 *
 * @author Haksun Li
 */
@Demo
public class PortfolioOptimComparisons1Demo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final double RISK_AVERSE = 20.;
    private static final double CORVALAN_RELAXATION = 1.2;

    public static void main(String[] args) throws Exception {
        PortfolioOptimComparisons1Demo demo
            = new PortfolioOptimComparisons1Demo();
        demo.run();
    }

    private void run() throws Exception {
        Matrix returns = randomReturnMatrix(10, 500);

        final Vector w0 = new DenseVector(returns.nCols());

        /**
         * This is the most popular portfolio optimizer based of Prof.
         * Markowitz's 1952 paper on modern portfolio theory (MPT). 1000+ papers
         * and 60 years experience demonstrate that this theory has numerious
         * problems. It has no practical value and should not be used for real
         * world asset allocation.
         */
        PortfolioOptimizationAlgorithm model
            = getMarkowitz(RISK_AVERSE);
        Vector w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("Markowitz model weights: {}", w1.toString());

        /**
         * This is the Black-Litterman's 1992 extension of MPT. It addresses the
         * corner solution problem of MPT and gives out diversified portfolio
         * based on market equilibrium. It also allows subjective views and
         * opinions on the market to be incorporated into the model.
         */
        model = getBlackLitterman(RISK_AVERSE);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("BlackLitterman model weights: {}", w1.toString());

        /**
         * This is another solution to the corner portfolio problem of MPT.
         * Instead of finding the best portfolio on the efficient frontier. It
         * find the near-best portfolios in the neighbourhood of (but not on)
         * the efficient frontier.
         */
        model = getCorvalan2005(RISK_AVERSE, CORVALAN_RELAXATION);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("Corvalan2005 model weights: {}", w1.toString());

        /**
         * This is an extension of MPT that allows specification of non-linear
         * constraints, e.g., market impact.
         */
        model = getSOCP(RISK_AVERSE);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("SOCP model weights: {}", w1.toString());

        /**
         * This is a popular smart beta algorithm that assumes all assets have
         * equal correlations (but not variances).
         */
        model = getEqualCor(RISK_AVERSE);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("EqualCor model weights: {}", w1.toString());

        /**
         * This is a popular smart beta algorithm that assumes all assets have
         * equal expected returns.
         */
        model = getEqualMean(RISK_AVERSE);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("EqualMean model weights: {}", w1.toString());

        /**
         * This is a popular smart beta algorithm that assumes all assets have
         * equal correlations and expected returns.
         */
        model = getEqualWeights(RISK_AVERSE);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("EqualWeights model weights: {}", w1.toString());

        /**
         * This is a ground breaking model that solves the fundamental problems
         * with MPT. It does not assume the foreknowledge of future covariances
         * and means. Instead, it assumes that they are unknown, hence random.
         * It is the best asset allocation model that based on stochastic
         * optimization.
         */
        model = getSAAM();
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("SAAM model weights: {}", w1.toString());

        /**
         * This combines the SAAM model with the Corvalan diversifier.
         */
        model = getCorvalanSAAM(CORVALAN_RELAXATION);
        w1 = model.getOptimalWeights(returns, w0, null, null);
        LOGGER.info("CorvalanSAAM model weights: {}", w1.toString());
    }

    public static PortfolioOptimizationAlgorithm getMarkowitz(double riskAverse) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new MarkowitzOptimizationAlgorithm(riskAverse);
        return model;
    }

    public static PortfolioOptimizationAlgorithm getCorvalan2005(
        double riskAverse,
        double relaxation
    ) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new Corvalan2005Diversifier(
                new MarkowitzOptimizationAlgorithm(riskAverse),
                new Corvalan2005(relaxation, relaxation)
            );
        return model;
    }

    public static PortfolioOptimizationAlgorithm getSOCP(double riskAverse) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new SOCPOptimizationAlgorithm(riskAverse);
        return model;
    }

    public static PortfolioOptimizationAlgorithm getEqualCor(double riskAverse) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new EqualCorAlgorithm(riskAverse);
        return model;
    }

    public static PortfolioOptimizationAlgorithm getEqualMean(double riskAverse) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new EqualMeanAlgorithm(riskAverse);
        return model;
    }

    public static PortfolioOptimizationAlgorithm getEqualWeights(double riskAverse) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new SmartBetaAlgorithm(
                new EqualMeanEstimator(),
                new EqualCorEstimator(),
                riskAverse);
        return model;
    }

    public static PortfolioOptimizationAlgorithm getSAAM() throws Exception {
        MVOptimizer mvOptimizer
            = new MVOptimizerShrankMean(new MVOptimizerLongOnly(), 0.5);
//                MVOptimizer mvOptimizer = new MVOptimizerLongOnly();
        ReturnsMoments.Estimator moments
            = new MomentsEstimatorLedoitWolf(); // how we estimate means and covariances
        double riskParameter = 1000;
        ReturnsResamplerFactory resampler = new GARCHResamplerFactory2();
//                ReturnsResamplerFactory resampler = new GroupResamplerFactory();
        PortfolioOptimizationAlgorithm model
            = new Lai2010OptimizationAlgorithm(
                mvOptimizer,
                moments,
                resampler,
                50,
                riskParameter
            );
        return model;
    }

    public static PortfolioOptimizationAlgorithm getCorvalanSAAM(
        double relaxation
    ) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new Corvalan2005Diversifier(
                getSAAM(),
                new Corvalan2005(relaxation, relaxation)
            );
        return model;
    }

    public static PortfolioOptimizationAlgorithm getBlackLitterman(
        double riskAverse
    ) throws Exception {
        PortfolioOptimizationAlgorithm model
            = new BlackLittermanOptimizationAlgorithm(riskAverse);

        return model;
    }

    /**
     * Generates a random return matrix.
     *
     * @param N the number of stocks
     * @param T the number of observations
     * @return a random return matrix
     */
    public static Matrix randomReturnMatrix(int N, int T) {
        RandomNumberGenerator rng1 = new Cheng1978(2.5, 4.5, new UniformRNG()); // beta distibution
        RandomReturns randomReturns = new RandomReturns(rng1);
        randomReturns.seed(12345678901L, 12345678901L);
        Matrix sample = randomReturns.getRandomReturns(N, T);
        return sample;
    }

}
