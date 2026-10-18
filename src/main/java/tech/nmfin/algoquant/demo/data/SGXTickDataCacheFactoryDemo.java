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
package tech.nmfin.algoquant.demo.data;

import dev.nm.misc.datastructure.time.LocalDateTimeInterval;
import dev.nm.nmutils.NMUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import tech.nmfin.algoquant.data.cache.SequentialCache;
import tech.nmfin.algoquant.data.cache.TimedEntry;
import tech.nmfin.algoquant.data.historicaldata.sgx.SGXTickDataCacheFactory;
import tech.nmfin.algoquant.execution.datatype.orderbook.OrderBook;
import static tech.nmfin.algoquant.execution.datatype.product.derivative.futures.sgx.SGXFuturesFactories.SiMSCI_FUTURES;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.util.annotation.Demo;

/**
 *
 * @author Haksun Li
 */
@Demo
public class SGXTickDataCacheFactoryDemo {

    private static final Logger LOGGER = NMUtils.getClassLogger();
    private static final String DATA_FOLDER = "./test-data/sgx/SiMSCI";

    public static void main(String[] args) {
        SGXTickDataCacheFactory source = new SGXTickDataCacheFactory(DATA_FOLDER);
        LocalDateTime begin = LocalDateTime.of(2011, 1, 1, 0, 0);
        LocalDateTime end
            = LocalDate.of(2011, 2, 28).atTime(Exchange.SGX.closeTime());
        SequentialCache<OrderBook> cache
            = source.newInstance(
                SiMSCI_FUTURES.newContract(2000, 1),
                LocalDateTimeInterval.of(begin, end)
            );

        int day = 0;

        int count = 0;
        for (TimedEntry<OrderBook> depth : cache) {
            if (depth.time().getDayOfMonth() != day) {
                LOGGER.info(depth.toString());
                day = depth.time().getDayOfMonth();
                count++;
            }
        }

        LOGGER.info("count = " + count);
    }
}
