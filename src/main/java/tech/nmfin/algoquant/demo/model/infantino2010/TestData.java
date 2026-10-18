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
package tech.nmfin.algoquant.demo.model.infantino2010;

import dev.nm.nmutils.NMUtils;
import java.util.*;
import org.slf4j.Logger;
import static tech.nmfin.algoquant.demo.model.infantino2010.TestData.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.*;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.GSPC;
import static tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500.sectorStocks;
import tech.nmfin.algoquant.execution.datatype.product.stock.usa.USDStock;

/**
 *
 * @author Ken Yiu
 */
public enum TestData {

    STOCKS_WITH_INSUFFICIENT_DATA { // as of 2005-01-01
        @Override
        public Set<Stock> stocks() {
            /*
             * the followings do not have enough data for the test period
             */
            Set<Stock> stocksWithInsufficientData = new HashSet<>();
            stocksWithInsufficientData.add(sp500("ABBV"));
            stocksWithInsufficientData.add(sp500("AMP"));
            stocksWithInsufficientData.add(sp500("BF-B"));
            stocksWithInsufficientData.add(sp500("BWA"));
            stocksWithInsufficientData.add(sp500("CBS"));
            stocksWithInsufficientData.add(sp500("CF"));
            stocksWithInsufficientData.add(sp500("CMG"));
            stocksWithInsufficientData.add(sp500("DFS"));
            stocksWithInsufficientData.add(sp500("DISCA"));
            stocksWithInsufficientData.add(sp500("DPS"));
            stocksWithInsufficientData.add(sp500("EXPE"));
            stocksWithInsufficientData.add(sp500("ICE"));
            stocksWithInsufficientData.add(sp500("XYL"));
            stocksWithInsufficientData.add(sp500("KMI"));
            stocksWithInsufficientData.add(sp500("MPC"));
            stocksWithInsufficientData.add(sp500("MA"));
            stocksWithInsufficientData.add(sp500("MNK"));
            stocksWithInsufficientData.add(sp500("NAVI"));
            stocksWithInsufficientData.add(sp500("PM"));
            stocksWithInsufficientData.add(sp500("PEG"));
            stocksWithInsufficientData.add(sp500("PSX"));
            stocksWithInsufficientData.add(sp500("SNI"));
            stocksWithInsufficientData.add(sp500("TEL"));
            stocksWithInsufficientData.add(sp500("TRIP"));
            stocksWithInsufficientData.add(sp500("VIAB"));
            stocksWithInsufficientData.add(sp500("V"));
            stocksWithInsufficientData.add(sp500("WU"));
            stocksWithInsufficientData.add(sp500("WYN"));

            LOGGER.info(
                "{} stocks have insufficient data",
                stocksWithInsufficientData.size()
            );

            return stocksWithInsufficientData;
        }
    },
    SP500 {
        @Override
        public Set<Stock> stocks() {
            Set<Stock> allStocks = sp500();
            int sectorSize = allStocks.size();
            allStocks.removeAll(STOCKS_WITH_INSUFFICIENT_DATA.stocks());

            LOGGER.info(
                "using {} ({}) equities in S&P500 (minus stocks that have insufficient data)",
                allStocks.size(),
                sectorSize
            );

            return allStocks;
        }
    },
    SP500_PLUS_GSPC { // GSPC = S&P 500 index
        @Override
        public Set<Stock> stocks() {
            Set<Stock> set = new HashSet<>(SP500.stocks());
            set.add(GSPC.getInstance());
            return set;
        }
    },
    SP500_IT {
        @Override
        public Set<Stock> stocks() {
            Set<Stock> allITStocks = new HashSet<Stock>(sectorStocks(GICSSector.INFORMATION_TECHNOLOGY));
            int sectorSize = allITStocks.size();
            allITStocks.removeAll(STOCKS_WITH_INSUFFICIENT_DATA.stocks());

            LOGGER.info(
                "using {} ({}) IT equities in S&P500 (minus stocks that have insufficient data)",
                allITStocks.size(),
                sectorSize
            );

            return allITStocks;
        }
    },
    TEN_CHOOSEN {
        @Override
        public Set<Stock> stocks() {
            Set<Stock> choosen = new HashSet<>();
            choosen.add(sp500("GOOGL"));
            choosen.add(sp500("AAPL"));
            choosen.add(sp500("AMZN"));
            choosen.add(sp500("IBM"));
            choosen.add(sp500("HPQ"));
            choosen.add(sp500("MSFT"));
            choosen.add(sp500("CSCO"));
            choosen.add(sp500("ORCL"));
            choosen.add(sp500("INTC"));
            choosen.add(sp500("ADBE"));
            choosen.removeAll(STOCKS_WITH_INSUFFICIENT_DATA.stocks());
            return choosen;
        }
    },
    TEN_CHOOSEN_PLUS_NDX {
        @Override
        public Set<Stock> stocks() {
            Set<Stock> set = new HashSet<>(TEN_CHOOSEN.stocks());
            set.add(USDStock.newInstance("^NDX", Exchange.NASDAQ));
            return TEN_CHOOSEN.stocks();
        }
    };

    private static final Logger LOGGER = NMUtils.getClassLogger();

    private static Set<Stock> sp500() {
        return new LinkedHashSet<Stock>(tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500.stocks());
    }

    private static Stock sp500(String symbol) {
        return tech.nmfin.algoquant.execution.datatype.product.stock.usa.SP500.stock(symbol);
    }

    public abstract Set<Stock> stocks();
}
