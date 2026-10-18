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
package tech.nmfin.algoquant.demo.model.kst1995;

import dev.nm.stat.random.rng.univariate.RandomLongGenerator;
import dev.nm.stat.random.rng.univariate.normal.RandomStandardNormalGenerator;
import dev.nm.stat.random.rng.univariate.normal.Zignor2005;
import dev.nm.stat.random.rng.univariate.uniform.UniformRNG;
import tech.nmfin.algoquant.model.trend.kst1995.GMA12;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.trend.kst1995.KnightSatchellTran1995;
import tech.nmfin.trend.kst1995.KnightSatchellTran1995MLE;

/**
 * Compares the fitted model with the real model.
 *
 *
 * <p><b>Using this example.</b>
 * Generates observations from a configured Knight-Satchell-Tran model and fits its parameters
 * by maximum likelihood. Compare fitted and generating parameters to understand the estimator;
 * finite samples and random draws affect the result.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * The original model concerns asymmetric upward and downward returns. The publication identity
 * was verified, but its full text was unavailable; distributional and estimator details below
 * follow the code/API, not an asserted paper reproduction.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Generate N=100 returns from KnightSatchellTran1995, fit them with
 * KnightSatchellTran1995MLE.fitModel and compare generating versus fitted parameters. The
 * parameters supplied to the constructor govern the return generator; GMA12 then computes model-
 * based holding-time and long-return statistics. These are analytical/simulation diagnostics
 * without a funded trading account.
 * </p>
 * @author Haksun Li
 * @see <a href="https://doi.org/10.1080/13504869500000009">J. L. Knight, S. E. Satchell and K. C. Tran (1995), Statistical modelling of asymmetric risk in asset returns, Applied Mathematical Finance 2(3), 155–172</a>
 */
@Demo
public class KnightSatchellTran1995MLEDemo {

    public static void main(String[] args) {
        int N = 100;

        double mu = 1;
        double q = 0.6;
        double alpha0 = 1;
        double rate0 = 1;
        double p = 0.2;
        double alpha1 = 1;
        double rate1 = 1;
        RandomLongGenerator rlg = new UniformRNG();
        RandomStandardNormalGenerator rnorm = new Zignor2005(rlg);

        double[] Rt = new double[N];
        KnightSatchellTran1995 model0
            = new KnightSatchellTran1995(mu, q, alpha0, rate0, p, alpha1, rate1, rnorm, rlg);
        for (int i = 0; i < N; ++i) {
            Rt[i] = model0.nextDouble();
        }

        KnightSatchellTran1995 model1 = new KnightSatchellTran1995MLE().fitModel(Rt);
        System.out.println(model1);

        GMA12 gma12_0 = new GMA12(model0);
        System.out.printf("the probability of holding till time = 1: %f%n",
            gma12_0.PrHoldingTime(1));
        System.out.printf("the expected pnl for long position: %f%n",
            gma12_0.ERRLong());

        GMA12 gma12_1 = new GMA12(model1);
        System.out.printf("the probability of holding till time = 1: %f",
            gma12_1.PrHoldingTime(1));
        System.out.printf("the expected pnl for long position: %f",
            gma12_1.ERRLong());
    }
}
