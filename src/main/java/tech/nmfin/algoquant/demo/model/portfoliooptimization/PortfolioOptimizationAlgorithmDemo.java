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
import dev.nm.stat.descriptive.moment.Mean;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tech.nmfin.algoquant.execution.datatype.product.Product;
import tech.nmfin.algoquant.execution.datatype.product.SimpleProduct;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.portfolio.weighting.PortfolioWeights;
import tech.nmfin.portfoliooptimization.Lai2010OptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.PortfolioOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.TopNOptimizationAlgorithm;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.GARCHResamplerFactory2;
import tech.nmfin.portfoliooptimization.lai2010.ceta.npeb.resampler.GroupResamplerFactory;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerLongOnly;
import tech.nmfin.portfoliooptimization.lai2010.optimizer.MVOptimizerShrankMean;
import tech.nmfin.returns.moments.MomentsEstimatorLedoitWolf;

/**
 * Shows how to compute the asset weights using a portfolio optimization
 * algorithm.
 *
 * @author Haksun Li
 */
public class PortfolioOptimizationAlgorithmDemo {

    public static void main(String[] args) throws IOException, Exception {
        double riskParameter = 10000.;
        double shrinkage = 1.;

        final int nProducts = 10;
        List<Product> products = new ArrayList<>(); // dummy products
        for (int i = 0; i < nProducts; ++i) {
            products.add(new SimpleProduct(new Integer(i).toString(), Currencies.USD));
        }

        Matrix returns
                = PortfolioOptimComparisons1Demo.randomReturnMatrix(nProducts, 500);

        GARCHResamplerFactory2 f = new GARCHResamplerFactory2();

        PortfolioOptimizationAlgorithm model
                //            = new MarkowitzOptimizationAlgorithm(riskParameter);
                = new Lai2010OptimizationAlgorithm(
                        new MVOptimizerShrankMean(new MVOptimizerLongOnly(), shrinkage),
                        //                new MVOptimizerLongOnly(),
                        new MomentsEstimatorLedoitWolf(),
                        new GroupResamplerFactory(),
                        //                new SimpleGARCHMoments2(f),
                        //                f,
                        50,
                        riskParameter);

        Vector w0 = new DenseVector(nProducts);
        Vector w1 = model.getOptimalWeights(returns, w0, null, null);
        Vector w2 = TopNOptimizationAlgorithm.getTopN(w1, 5, 0.8);
//        Vector w2 = w1;
        PortfolioWeights w3 = new PortfolioWeights(products, w2);

        System.out.println(w3);
//        System.out.println("all zero? " + w1.isAllZero());
        System.out.println("full w: " + w1);
        System.out.println("w: " + w3.toVector());
        System.out.println("w_size: " + w3.size());
        System.out.println("w_mean: " + new Mean(w3.toVector().toArray()));
        System.out.println("sum: " + new DenseVector(w3.size(), 1.).innerProduct(w3.toVector()));
    }

}
