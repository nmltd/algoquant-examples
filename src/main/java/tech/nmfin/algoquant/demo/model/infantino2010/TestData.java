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
 *
 * <p><b>Using this example.</b>
 * Supplies historical example stock sets for the Infantino demos, including explicit
 * exclusions for insufficient data. These lists are not current or point-in-time S&amp;P 500
 * membership. TEN_CHOOSEN_PLUS_NDX currently returns TEN_CHOOSEN.stocks(), so the created
 * Nasdaq index addition is discarded; see the audit notes before using it.
 * </p>
 *
 * <p><b>Model and publication.</b>
 * This enum is example input selection for the PCA demonstrations, not a model described in the
 * thesis.
 * </p>
 * <p><b>Implementation in this tutorial.</b>
 * stocks() supplies each example’s universe. Availability exclusions are historical hand-
 * maintained choices, and the chosen names and sample frequency differ from the thesis. Retain a
 * point-in-time universe when doing a historical study; do not equate this enum with the paper’s
 * 50-stock sample.
 * </p>
 * @author Ken Yiu
 * @see <a href="https://dspace.mit.edu/handle/1721.1/59122">L. R. Infantino and S. Itzhaki (2010), Developing high-frequency equities trading models, MIT MBA thesis</a>
 */
public enum TestData {

    STOCKS_WITH_INSUFFICIENT_DATA { // as of 2005-01-01
        @Override
        public Set<Stock> stocks() {
            /*
             * the followings do not have enough data for the test period
             */
            Set<Stock> stocksWithInsufficientData = new HashSet<>();
            // Match exclusions against available constituents; retired symbols are already absent.
            java.util.Set<String> excluded = new java.util.HashSet<>(java.util.Arrays.asList(
                "ABBV", "AMP", "BF-B", "BWA", "CBS", "CF", "CMG", "DFS", "DISCA", "DPS", "EXPE", "ICE", "XYL", "KMI", "MPC", "MA", "MNK", "NAVI", "PM", "PEG", "PSX", "SNI", "TEL", "TRIP", "VIAB", "V", "WU", "WYN"
            ));
            for (Stock stock : sp500()) {
                if (excluded.contains(stock.symbol())) stocksWithInsufficientData.add(stock);
            }

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
            // Existing limitation: this returns a fresh stock-only set instead of set,
            // so the ^NDX addition above is not present in the returned collection.
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
