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
package tech.nmfin.algoquant.demo.model.daspremont2008;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;
import tech.nmfin.algoquant.execution.datatype.product.fx.Currencies;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500;
import tech.nmfin.algoquant.model.meanreversion.daspremont2008.simulation.MRSimParam;
import tech.nmfin.algoquant.model.util.price.PriceMatrixFactory;
import tech.nmfin.algoquant.model.util.price.PriceMatrixFactoryYahooEOD;

public class MRSimParamSP500 implements MRSimParam {

    @Override
    public List<String> symbolPool() {
        return SP500.stocks().stream()
            .map(stock -> stock.symbol())
            .collect(Collectors.toList());
    }

    @Override
    public int basketCount() {
        return 1;
    }

    @Override
    public int basketSize() {
        return 5;
    }

    @Override
    public Currency currency() {
        return Currencies.USD;
    }

    @Override
    public LocalDateTimeInterval tradingInterval() {
        return LocalDateTimeInterval.of(
            LocalDateTime.of(2013, 1, 1, 0, 0),
            LocalDateTime.of(2015, 1, 1, 0, 0)
        );
    }

    @Override
    public Period basketRenewalPeriod() {
        return Period.ofMonths(6);
    }

    @Override
    public Period inSamplePeriodForClustering() {
        return Period.ofYears(10);
    }

    @Override
    public Period inSamplePeriodForBasketSelection() {
        return Period.ofYears(5);
    }

    @Override
    public double covPenalty() {
        return 1.5e-4;
    }

    @Override
    public PriceMatrixFactory<String> priceMatrixFactory() {
        return new PriceMatrixFactoryYahooEOD(
            LocalDateTimeInterval.of(
                tradingInterval().start().minus(inSamplePeriodForClustering()),
                tradingInterval().end()
            ),
            exchange(),
            currency()
        );
    }

    @Override
    public Exchange exchange() {
        return Exchange.NEW_YORK;
    }
}
