import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.ExchangeCore;
import com.exechange_test.core.IEventsHandler;
import com.exechange_test.core.SimpleEventsProcessor;
import com.exechange_test.core.common.*;
import com.exechange_test.core.common.api.*;
import com.exechange_test.core.common.api.binary.BatchAddSymbolsCommand;
import com.exechange_test.core.common.api.reports.SingleUserReportQuery;
import com.exechange_test.core.common.api.reports.SingleUserReportResult;
import com.exechange_test.core.common.api.reports.TotalCurrencyBalanceReportQuery;
import com.exechange_test.core.common.api.reports.TotalCurrencyBalanceReportResult;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.common.config.ExchangeConfiguration;
import com.exechange_test.core.orderbook.IOrderBook;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public class ITCoreExample {
    private IOrderBook orderBook;

    @Test
    public void sampleTest() throws Exception {

        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(new IEventsHandler() {
            @Override
            public void tradeEvent(TradeEvent tradeEvent) {
                System.out.println("Trade event: " + tradeEvent);
            }

            @Override
            public void reduceEvent(ReduceEvent reduceEvent) {
                System.out.println("Reduce event: " + reduceEvent);
            }

            @Override
            public void rejectEvent(RejectEvent rejectEvent) {
                System.out.println("Reject event: " + rejectEvent);
            }

            @Override
            public void commandResult(ApiCommandResult commandResult) {
                System.out.println("Command result: " + commandResult);
            }

            @Override
            public void orderBook(OrderBook orderBook) {
                System.out.println("OrderBook event: " + orderBook);
            }
        });

        ExchangeConfiguration conf = ExchangeConfiguration.defaultBuilder().build();

        ExchangeCore exchangeCore = ExchangeCore.builder()
                .resultsConsumer(eventsProcessor)
                .exchangeConfiguration(conf)
                .build();

        exchangeCore.startup();

        ExchangeApi api = exchangeCore.getApi();

        final int currencyCodeXbt = 11;
        final int currencyCodeLtc = 15;

        final int symbolXbtLtc = 241;

        Future<CommandResultCode> future;

        CoreSymbolSpecification symbolSpecXbtLtc = CoreSymbolSpecification.builder()
                .symbolId(symbolXbtLtc)         // symbol id
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(currencyCodeXbt)    // base = satoshi (1E-8)
                .quoteCurrency(currencyCodeLtc)   // quote = litoshi (1E-8)
                .baseScaleK(1_000_000L) // 1 lot = 1M satoshi (0.01 BTC)
                .quoteScaleK(10_000L)   // 1 price step = 10K litoshi
                .takerFee(1900L)        // taker fee 1900 litoshi per 1 lot
                .makerFee(700L)         // maker fee 700 litoshi per 1 lot
                .build();

        future = api.submitBinaryDataAsync(new BatchAddSymbolsCommand(symbolSpecXbtLtc));
        System.out.println("BatchAddSymbolsCommand result: " + future.get());


        // create user uid=301
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(301L)
                .build());

        System.out.println("ApiAddUser 1 result: " + future.get());


        // create user uid=302
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(302L)
                .build());

        System.out.println("ApiAddUser 2 result: " + future.get());

        // first user deposits 20 LTC
        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(301L)
                .currency(currencyCodeLtc)
                .amount(2_000_000_000L)
                .transactionId(1L)
                .build());

        System.out.println("ApiAdjustUserBalance 1 result: " + future.get());


        // second user deposits 0.10 BTC
        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(302L)
                .currency(currencyCodeXbt)
                .amount(20_000_000L)
                .transactionId(2L)
                .build());

        System.out.println("ApiAdjustUserBalance 2 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6002L)
                .price(15_050L)
                .stopPrice(15_300L)
                .size(1L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.GTC) // stop_loss
                .symbol(symbolXbtLtc)
                .build());

        System.out.println("ApiPlaceOrder 1 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5001L)
                .price(15_070L)
                .reservePrice(15_600L) // can move bid order up to the 1.56 LTC, without replacing it
                .size(2L) // order size is 35 lots
                .action(OrderAction.BID)
                .orderType(OrderType.GTC) // Good-till-Cancel
                .symbol(symbolXbtLtc)
                .build());
        System.out.println("ApiPlaceOrder 2 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6000L)
                .price(14_950L)
                .stopPrice(0L)
                .size(1L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.IOC) // stop_loss
                .symbol(symbolXbtLtc)
                .build());
        System.out.println("ApiPlaceOrder 5 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6001L)
                .price(14_850L)
                .stopPrice(15_300L)
                .size(1L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.IOC) // stop_loss
                .symbol(symbolXbtLtc)
                .build());

        System.out.println("ApiPlaceOrder 5 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5002L)
                .price(15_080L)
                .reservePrice(15_600L) // can move bid order up to the 1.56 LTC, without replacing it
                .size(2L) // order size is 35 lots
                .action(OrderAction.BID)
                .orderType(OrderType.GTC) // Good-till-Cancel
                .symbol(symbolXbtLtc)
                .build());
        System.out.println("ApiPlaceOrder 2 result: " + future.get());


        // request order book
        CompletableFuture<L2MarketData> orderBookFuture = api.requestOrderBookAsync(symbolXbtLtc, 10);
        System.out.println("ApiOrderBookRequest result: " + orderBookFuture.get());
//
//
//        // first user moves remaining order to price 1.53 LTC
//        future = api.submitCommandAsync(ApiMoveOrder.builder()
//                .uid(301L)
//                .orderId(5001L)
//                .newPrice(15_300L)
//                .symbol(symbolXbtLtc)
//                .build());
//
//        System.out.println("ApiMoveOrder 2 result: " + future.get());
//
//        // first user cancel remaining order
//        future = api.submitCommandAsync(ApiCancelOrder.builder()
//                .uid(301L)
//                .orderId(5001L)
//                .symbol(symbolXbtLtc)
//                .build());
//
//        System.out.println("ApiCancelOrder 2 result: " + future.get());

         //check balances
//        Future<SingleUserReportResult> report1 = api.processReport(new SingleUserReportQuery(301), 0);
//        System.out.println("SingleUserReportQuery 1 accounts: " + report1.get().getAccounts());

        Future<SingleUserReportResult> report2 = api.processReport(new SingleUserReportQuery(302), 0);
        System.out.println("SingleUserReportQuery 2 accounts: " + report2.get().getAccounts());

        // first user withdraws 0.10 BTC
//        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
//                .uid(301L)
//                .currency(currencyCodeXbt)
//                .amount(-10_000_000L)
//                .transactionId(3L)
//                .build());
//
//        System.out.println("ApiAdjustUserBalance 1 result: " + future.get());



        // check fees collected
//        Future<TotalCurrencyBalanceReportResult> totalsReport = api.processReport(new TotalCurrencyBalanceReportQuery(), 0);
//        System.out.println("LTC balance: " + totalsReport.get().getGlobalBalancesSum());
//        System.out.println("non cache");


    }
}
