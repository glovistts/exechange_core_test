package com.exechange_test;

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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@SpringBootApplication
public class ExechangeTestApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ExechangeTestApplication.class, args);
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
                .symbolId(symbolXbtLtc)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(currencyCodeXbt)
                .quoteCurrency(currencyCodeLtc)
                .baseScaleK(1_000_000L)
                .quoteScaleK(10_000L)
                .takerFee(1900L)
                .makerFee(700L)
                .build();

        future = api.submitBinaryDataAsync(new BatchAddSymbolsCommand(symbolSpecXbtLtc));
        //System.out.println("BatchAddSymbolsCommand result: " + future.get());


        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(301L)
                .build());
//        System.out.println("ApiAddUser 1 result: " + future.get());
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(302L)
                .build());
//        System.out.println("ApiAddUser 2 result: " + future.get());



        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(301L)
                .currency(currencyCodeLtc)
                .amount(2_000_000_000L)
                .transactionId(1L)
                .build());
//        System.out.println("ApiAdjustUserBalance 1 result: " + future.get());

        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(302L)
                .currency(currencyCodeXbt)
                .amount(10_000_000L)
                .transactionId(2L)
                .build());
//        System.out.println("ApiAdjustUserBalance 2 result: " + future.get());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5001L)
                .price(15_400L)
                .reservePrice(15_600L)
                .size(12L)
                .action(OrderAction.BID)
                .orderType(OrderType.GTC)
                .symbol(symbolXbtLtc)
                .build());

//        System.out.println("ApiPlaceOrder 1 result: " + future.get());
        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(5002L)
                .price(15_250L)
                .size(10L)
                .action(OrderAction.ASK)
                .orderType(OrderType.IOC)
                .symbol(symbolXbtLtc)
                .build());

//        System.out.println("ApiPlaceOrder 2 result: " + future.get());

        CompletableFuture<L2MarketData> orderBookFuture = api.requestOrderBookAsync(symbolXbtLtc, 10);
//        System.out.println("ApiOrderBookRequest result: " + orderBookFuture.get());
        future = api.submitCommandAsync(ApiMoveOrder.builder()
                .uid(301L)
                .orderId(5001L)
                .newPrice(15_300L)
                .symbol(symbolXbtLtc)
                .build());
//        System.out.println("ApiMoveOrder 2 result: " + future.get());
//        future = api.submitCommandAsync(ApiCancelOrder.builder()
//                .uid(301L)
//                .orderId(5001L)
//                .symbol(symbolXbtLtc)
//                .build());
//        System.out.println("ApiCancelOrder 2 result: " + future.get());
//        Future<SingleUserReportResult> report1 = api.processReport(new SingleUserReportQuery(301), 0);
//        System.out.println("SingleUserReportQuery 1 accounts: " + report1.get().getAccounts());
//        Future<SingleUserReportResult> report2 = api.processReport(new SingleUserReportQuery(302), 0);
//        System.out.println("SingleUserReportQuery 2 accounts: " + report2.get().getAccounts());
//        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
//                .uid(301L)
//                .currency(currencyCodeXbt)
//                .amount(-10_000_000L)
//                .transactionId(3L)
//                .build());
//        System.out.println("ApiAdjustUserBalance 1 result: " + future.get());
//        Future<TotalCurrencyBalanceReportResult> totalsReport = api.processReport(new TotalCurrencyBalanceReportQuery(), 0);
//        System.out.println("LTC fees collected: " + totalsReport.get().getFees().get(currencyCodeLtc));
    }


}
