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
package tech.nmfin.algoquant.demo.ib;

import com.google.api.client.auth.oauth2.Credential;
import java.util.function.DoubleFunction;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tech.nmfin.algoquant.data.google.GoogleSheetsUtils;
import tech.nmfin.algoquant.execution.datatype.product.derivative.ContractType;
import tech.nmfin.algoquant.execution.datatype.product.stock.Exchange;
import tech.nmfin.algoquant.execution.datatype.product.stock.SimpleStock;
import tech.nmfin.algoquant.execution.ib.IBConnection;
import tech.nmfin.algoquant.execution.ib.IBConnectionConfig;
import tech.nmfin.algoquant.execution.ib.IBStrategyExecutor;
import tech.nmfin.algoquant.execution.ib.IBStrategyExecutorConfig;
import tech.nmfin.algoquant.execution.ib.monitor.publisher.GoogleSheetsLiveMonitorPublisher;
import tech.nmfin.algoquant.execution.ib.server.IBStrategyControlServer;
import tech.nmfin.algoquant.execution.strategy.NamedParam;
import tech.nmfin.algoquant.execution.strategy.resumable.StrategyStateManagerByGoogleSheets;

/**
 * Runs the demo strategy with Spring Boot control web server.
 *
 * Note: Please update 'accountId' and 'spreadsheetId' appropriately before
 * running this application.
 *
 * To ship this trading application as a docker image that runs this server with
 * TWS and IBController, follow the steps below:
 * <ol>
 * <li>Enter IB account ID (accountId) in this source file.
 * <li>Create a service account in a Google Cloud project, save the project's
 * private key in JSON format (name it 'private_key.json').
 * <li>Make a private copy of the Google spreadsheet template
 * (https://docs.google.com/spreadsheets/d/10pQYVj2jaAgDSHsIUNMKQSatk7xJz70INiSjYkfoBxQ/edit?usp=sharing
 * ), share it with the service account created in the previous step, and enter
 * the spreadsheet ID (spreadsheetId) in this source code.
 * <li>Change the web server 'username' and 'password' in this source file.
 * <li>Replace the demo strategy with your own strategy in this source file.
 * <li>Enter your IB login/password in
 * 'ibdemo/resources/IBController/IBController.ini'.
 * <li>Make sure 'nm.lic' and 'private_key.json' (for Google service account)
 * are in the project ('demo') folder.
 * <li>Update the script 'ibdemo/resources/run.sh' if needed (e.g., vnc
 * password, default is 'changeme').
 * <li>Open command prompt, change the working directory to folder
 * 'demo/ibdemo', and run 'build-docker-image.bat'.
 * <li>The image named 'ibdemo' is created. (Check with command 'docker images
 * ibdemo'.)
 * </ol>
 *
 * @author Ken Yiu
 */
@SpringBootApplication
public class IBStrategyServerDemo extends IBStrategyControlServer {

    private final String accountId = "YOUR_IB_ACCOUNT_ID";
    private final String spreadsheetId = "YOUR_GOOGLE_SPREADSHEET_ID";
    private final Credential credential
        = GoogleSheetsUtils.getCredentialForServiceAccount(
            "./private_key.json" // Google credentials file for service account
        );
    /*
     * Login for the control server.
     */
    private final String username = "ibdemo";
    private final String password = "passwd";

    @Override
    protected IBStrategyExecutor executor() {
        return new IBStrategyExecutor(
            new IBDemoStrategy(
                new NamedParam()
                    //                    .set("underlying", new SimpleIndex("SPX", Exchange.CBOE))
                    .set("underlying", new SimpleStock("FB", Exchange.NASDAQ))
                    .set("option-type", ContractType.CALL_OPTIONS)
                    .set("option-strike-function", (DoubleFunction<Double>) p -> p)
                    .set("size", 5)
            ),
            new IBStrategyExecutorConfig(
                new IBConnection(
                    // IBConnectionConfig.forDefaultPaperTradingViaLocalGateway() // Note: use Gateway if the strategy needs to run for multiple days
                    IBConnectionConfig.forDefaultPaperTradingViaLocalTWS()
                ))
                .account(accountId)
                .initialCash(1e3)
                .orderBookDataContainsSizes(false)
                .strategyStateManager(
                    new StrategyStateManagerByGoogleSheets(
                        credential,
                        spreadsheetId
                    )
                )
                .liveMonitorPublisher(
                    new GoogleSheetsLiveMonitorPublisher(
                        credential,
                        spreadsheetId
                    )
                )
        );
    }

    @Override
    protected String username() {
        return username;
    }

    @Override
    protected String password() {
        return password;
    }

    public static void main(String[] args) {
        SpringApplication.run(IBStrategyServerDemo.class, args);
    }
}
