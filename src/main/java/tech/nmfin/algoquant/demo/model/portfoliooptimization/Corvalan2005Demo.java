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
import static dev.nm.algebra.linear.matrix.doubles.operation.MatrixUtils.colSums;
import dev.nm.algebra.linear.vector.doubles.Vector;
import dev.nm.algebra.linear.vector.doubles.dense.DenseVector;
import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import static dev.nm.number.doublearray.DoubleArrayMath.expm1;
import dev.nm.solver.multivariate.constrained.general.penaltymethod.PenaltyMethodMinimizer;
import dev.nm.stat.covariance.LedoitWolf2004;
import java.time.LocalDateTime;
import java.util.List;
import tech.nmfin.algoquant.data.cache.util.FactoryCache2ReturnsMatrix;
import tech.nmfin.algoquant.data.historicaldata.yahoo.YahooOrderBookCacheFactory;
import tech.nmfin.algoquant.execution.datatype.product.ProductUtils;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.Stock;
import tech.nmfin.algoquant.execution.performance.measure.ir.InformationRatio;
import tech.nmfin.algoquant.execution.simulation.template.SimTemplateYahooEOD;
import tech.nmfin.algoquant.model.portfoliooptimization.markowitz.Markowitz1952;
import tech.nmfin.algoquant.util.annotation.Demo;
import tech.nmfin.portfoliooptimization.corvalan2005.Corvalan2005;
import tech.nmfin.portfoliooptimization.corvalan2005.constraint.NoShortSelling;
import tech.nmfin.portfoliooptimization.corvalan2005.diversification.DiversificationMeasure;
import tech.nmfin.portfoliooptimization.corvalan2005.diversification.ProductOfWeights;
import tech.nmfin.returns.Returns;

/**
 * This paper tackles the <em>corner solution problem</em> of many portfolio
 * optimizers, by optimizing the portfolio diversification with some relaxation
 * on the volatility <i>&sigma;</i> and the expected return <i>R</i> of a given
 * optimized (but non-diversified) portfolio. This implementation generalizes
 * this framework as the following optimization problem: \[ \max_{w} D(w) \quad
 * \text{s.t.} \\ \begin{aligned} \sqrt{w' \Sigma w} &amp; \le \sigma^* (1 + \Delta
 * \sigma) \\ R^* (1 - \Delta R) &amp; \le w'r \\ w' 1 &amp; = 1 \\ ... &amp; \text{some
 * extra constraints} \end{aligned} \] where \( ( \sigma^* , R^* ) = (
 * \sqrt{w^{*'} \Sigma w^*} , w^{*'} r ) \), \(w^*\) is the original optimal
 * weights, \(\Delta \sigma, \Delta R\) are the relaxation tolerance parameters,
 * and \(D(w)\) is a defined measure for portfolio diversification
 * ({@linkplain DiversificationMeasure}).
 *
 *
 * <p><b>Using this example.</b>
 * Applies portfolio diversification with return and volatility relaxation around an optimized
 * portfolio. The Yahoo sample supplies estimation data; tolerance parameters alter the
 * feasible portfolio set. The reported weights are allocations, not share quantities or
 * realized backtest returns.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * Corvalán proposes two stages: find a mean–variance reference portfolio, then improve
 * diversification while allowing a neighborhood of return/risk deviations. The paper discusses
 * multiple diversification measures.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * Corvalan2005 in this example exposes the relaxation settings and chosen diversification
 * measure through library classes. Its documented constraints use relative return/volatility
 * relaxations; check that parameterization rather than substituting a tolerance directly from
 * the paper. Weights, estimated risk and forecast return are optimizer outputs, not a realized
 * account-performance report.
 * </p>
 * @see <a href="https://dialnet.unirioja.es/descarga/articulo/1364447.pdf">Well
 * Diversified Efficient Portfolios</a>
 * @see <a href="https://dialnet.unirioja.es/descarga/articulo/1364447.pdf">A. Corvalán (2005), Well Diversified Efficient Portfolios, Central Bank of Chile Working Paper 336</a>
 */
@Demo
public class Corvalan2005Demo {

    public static void main(String[] args) throws Exception {
        /*
         * list the stocks to be traded
         */
        List<Stock> stocks
            = ProductUtils.newStockList(
                new String[]{"ORCL", "AAPL", "AMZN", "IBM", "MSFT"},
                Exchange.NASDAQ,
                Currencies.USD
            );

        /*
         * specify the time period of the simulation
         */
        LocalDateTime begin = LocalDateTime.of(2011, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2012, 1, 1, 0, 0);
        LocalDateTimeInterval interval = LocalDateTimeInterval.of(begin, end);

        YahooOrderBookCacheFactory<Stock> yahoo
            = new YahooOrderBookCacheFactory<>(
                SimTemplateYahooEOD.DEFAULT_DATA_FOLDER
            );
        Matrix returns
            = new FactoryCache2ReturnsMatrix<>(
                yahoo,
                stocks,
                interval
            );
        System.out.println("returns matrix: " + returns);
        double deltaSigma = 0.05; // relax sigma by 5%
        double deltaR = 0.10; // relax returns by 10%

        Corvalan2005Demo demo = new Corvalan2005Demo(deltaSigma, deltaR);
        demo.run(stocks, returns);
    }
    private final DiversificationMeasure measure;
    private final Corvalan2005 corvalan2005;

    public Corvalan2005Demo(double deltaSigma, double deltaR) {
        this.measure = new ProductOfWeights(); // other measure can be used
        PenaltyMethodMinimizer solver = new PenaltyMethodMinimizer(); // other solver can be used
        this.corvalan2005 = new Corvalan2005(solver, measure, deltaSigma, deltaR);
    }

    private void run(List<Stock> stocks, Matrix returns) throws Exception {
        Matrix sigma = new LedoitWolf2004().compute(returns).getCovarianceMatrix();
        System.out.println("covariance matrix (sigma): " + sigma);

        Vector mu = Returns.getMeanReturns(returns);
        System.out.println("avg. returns (mu): " + mu);

        Vector R = new DenseVector(expm1(colSums(returns)));
        System.out.println("period returns (R): " + R);

        /*
         * optimize the portfolio by Markowitz model
         */
        Markowitz1952 markowitz = new Markowitz1952(0.5);
        Vector w = markowitz.getOptimalWeights(returns, null, null, null);
        System.out.println("optimal weights: " + w);

        /*
         * diversify the porfolio by Corvalan's framework
         */
        Vector diversifiedWeights
            = corvalan2005.getDiversifiedWeights(
                new NoShortSelling(w.size()),
                w,
                sigma,
                R
            );
        System.out.println("diversified weights: " + diversifiedWeights);

        System.out.println("diversification(optimal): "
            + measure.value(w.toArray()));
        System.out.println("diversification(diversified): "
            + measure.value(diversifiedWeights.toArray()));

        double rOptimal = R.innerProduct(w);
        double rDiversified = R.innerProduct(diversifiedWeights);
        System.out.println("returns(optimal): " + rOptimal);
        System.out.println("returns(diversified): " + rDiversified);

        double sigmaOptimal = stdev(sigma, w);
        double sigmaDiversified = stdev(sigma, diversifiedWeights);
        System.out.println("sigma(optimal): " + sigmaOptimal);
        System.out.println("sigma(diversified): " + sigmaDiversified);

        double irOptimal = ir(returns, w);
        double irDiversified = ir(returns, diversifiedWeights);
        System.out.println("ir(optimal): " + irOptimal);
        System.out.println("ir(diversified): " + irDiversified);
    }

    private double stdev(Matrix sigma, Vector weights) {
        return Math.sqrt(weights.innerProduct(sigma.multiply(weights)));
    }

    private double ir(Matrix returns, Vector weights) {
        return new InformationRatio().valueOf(returns.multiply(weights).toArray(), 0.);
    }

}
