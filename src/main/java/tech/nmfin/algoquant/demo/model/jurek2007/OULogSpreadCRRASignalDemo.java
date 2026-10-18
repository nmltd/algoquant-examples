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
package tech.nmfin.algoquant.demo.model.jurek2007;

import dev.nm.misc.ArgumentAssertion;
import dev.nm.stat.descriptive.moment.Mean;
import dev.nm.stat.descriptive.moment.Variance;
import dev.nm.stat.descriptive.rank.Max;
import dev.nm.stat.descriptive.rank.Min;
import dev.nm.stat.random.rng.RNGUtils;
import dev.nm.stat.random.rng.univariate.normal.RandomStandardNormalGenerator;
import dev.nm.stat.random.rng.univariate.normal.StandardNormalRNG;
import dev.nm.stat.stochasticprocess.univariate.sde.process.ou.*;
import static java.lang.Math.*;
import tech.nmfin.algoquant.model.meanreversion.jurek2007.OUSpreadCRRASignal;
import tech.nmfin.algoquant.model.meanreversion.jurek2007.OUSpreadCRRASignal.Allocation;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 * This demonstrates the P&amp;L when applying the optimal mean reversion trading
 * strategy to an OU asset.
 *
 *
 * <p><b>Using this example.</b>
 * Illustrates the OU log-spread trading signal and its P/L calculation using the parameters in
 * main. Follow the modeled spread and risk-aversion inputs separately from any market-data
 * assumptions; the theoretical model does not establish that a real pair follows an OU
 * process.
 * </p>
 * @author Kevin Sun
 * @see
 * <a href="http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.217.6021">Kim,
 * S. J., Primbs, J., Boyd, S., Dynamic Spread Trading, 2008</a>
 */
@Demo
public class OULogSpreadCRRASignalDemo {

    public static void main(String[] args) {
        OULogSpreadCRRASignalDemo instance = new OULogSpreadCRRASignalDemo(1234567890L);

        double gamma = 1;
        System.out.printf("simulating with gamma = %f%n", gamma);
        instance.run(gamma, true);

        gamma = 5;
        System.out.printf("simulating with gamma = %f%n", gamma);
        instance.run(gamma, true);

        double[] gammas = new double[]{0.8, 1., 5., 50., 10000.};
        int N = 10000;
        for (int g = 1; g <= gammas.length; ++g) {
            gamma = gammas[g - 1];
            double[] sharpes = new double[N];

            for (int i = 0; i < N; ++i) {
                double sharpe = instance.run(gamma, false);
                sharpes[i] = sharpe;
            }

            double mean_sharpe = new Mean(sharpes).value();
            double max_sharpe = new Max(sharpes).value();
            double min_sharpe = new Min(sharpes).value();
            System.out.printf(
                "gamma = %f, number of sims = %d, min Sharpe = %.3f, mean Sharpe = %.3f, max Sharpe = %.3f%n",
                gamma, N, min_sharpe, mean_sharpe, max_sharpe
            );
        }
    }

    private final RandomStandardNormalGenerator rnorm;

    public OULogSpreadCRRASignalDemo(long seed) {
        this.rnorm = new StandardNormalRNG();
        this.rnorm.seed(seed);
    }

    /**
     * Gets the Sharpe ratio.
     *
     * @param gamma   the risk aversion rate
     * @param isPrint to print logging info
     * @return the Sharpe ratio
     */
    public double run(double gamma, boolean isPrint) {
        double W0 = 1000.; // initial wealth
        double r = 0.05; // the risk-free rate

        double delta = 1. / 250.; // 250 trading days
        double T = 1.; // number of years of data
        int n = (int) round(T / delta); // number of data points

        // the following parameters are from Kim, Primbs and Boyd (2008)
        double mu0 = 0.1111;
        double theta_0 = 3.5519;
        double sigma_0 = sqrt(128.7793);
        OUSim ouSim = new OUSim(new OrnsteinUhlenbeckProcess(mu0, theta_0, sigma_0), delta, mu0, rnorm); // generate an OU process
        double[] prices = RNGUtils.nextN(ouSim, n); // price series
        OUFittingMLE fit = new OUFittingMLE();
        OUProcess ou = fit.getFittedOU(prices, delta); // fit the OU process

        double mu = ou.mu(); // fitted parameters
        double theta = ou.theta();
        double sigma = ou.sigma();
        if (isPrint) {
            System.out.printf("mu (estimate) = %.3f%n", mu);
            System.out.printf("theta / delta (estimate) = %.3f%n", theta / delta);
            System.out.printf("sigma / sqrt(delta) (estimate) = %.3f%n", sigma / sqrt(delta));
        }

        double[] PnL = new double[n]; // P&L
        double[] W_t = new double[n]; // wealth process
        double[] N_t = new double[n]; // number of units invested in the risky asset (spread)
        double[] R_t = new double[n]; // wealth invested in risk-free asset

        double W = W0;
        double N = 0;
        double R = W0;
        double total_PnL = 0.;

        for (int t = 1; t <= n; ++t) {
            double tau = T - (delta * t); // time remaining
            double price = prices[t - 1]; // P_t
            double price_change = (t == 1) ? 0. : price - prices[t - 2];
            double profit = N * price_change + R * (exp(r * delta) - 1.);
            PnL[t - 1] = profit;
            total_PnL += profit;
            double W_new = W + total_PnL;
            W_t[t - 1] = W_new;

            OUSpreadCRRASignal signal = new OUSpreadCRRASignal(ou, gamma, r * delta);
//            OUSpreadCRRASignal signal = new OUSpreadCRRASignal(ou, gamma, r);
            Allocation allocation = signal.getOptimalAllocation(price, W, tau); // fixed capital; change W to W_new if we want to invest new capital
            N = allocation.optimalSpreadQuantity();
            R = allocation.optimalRiskFreeAssetValue();
            N_t[t - 1] = N;
            R_t[t - 1] = R;
            if (isPrint) {
                System.out.printf(
                    "t = %d, price = %.3f, price change = %.3f, N = %.3f, R = %.3f, profit_{t-1} = %.3f, cumulative wealth = %.3f%n",
                    t, price, price_change, N, R, profit, W_new
                );
            }
        }

        Variance var = new Variance(PnL);
        double mean = var.mean();
        double sd = sqrt(var.value());
        double sharpe = mean / sd * sqrt(1. / delta);
        if (isPrint) {
            System.out.println("========================================================================================================================");
            System.out.printf(
                "gamma = %.3f, initial wealth = %.3f, mean = %.3f, SD = %.3f, Sharpe = %.3f, terminal wealth = %.3f%n",
                gamma, W0, mean, sd, sharpe, W_t[n - 1]
            );
            System.out.println("========================================================================================================================\n");

            ArgumentAssertion.assertTrue(sharpe > 0., "Sharpe ratio is positive.");
        }

        return sharpe;
    }
}
