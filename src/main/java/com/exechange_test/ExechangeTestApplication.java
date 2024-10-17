package com.exechange_test;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.*;
import com.exechange_test.core.common.api.ApiAddUser;
import com.exechange_test.core.common.api.ApiAdjustUserBalance;
import com.exechange_test.core.common.api.ApiPlaceOrder;
import com.exechange_test.core.common.api.binary.BatchAddSymbolsCommand;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.my.AppConfig;
import com.exechange_test.core.my.StopLossCheckThread;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


@SpringBootApplication
@EnableScheduling
public abstract class ExechangeTestApplication  implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(ExechangeTestApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        StopLossCheckThread stopLossThread = new StopLossCheckThread();
        stopLossThread.start();

        ExchangeApi api = AppConfig.getExchangeApi();

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


        // create user uid=301
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(301L)
                .build());



        // create user uid=302
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(302L)
                .build());


        // first user deposits 20 LTC
        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(301L)
                .currency(currencyCodeLtc)
                .amount(2_000_000_000L)
                .transactionId(1L)
                .build());



        // second user deposits 0.10 BTC
        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(302L)
                .currency(currencyCodeXbt)
                .amount(20_000_000L)
                .transactionId(2L)
                .build());


        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6002L)
                .price(15_050L)
                .stopPrice(15_300L)
                .size(1L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.STOP_LOSS) // stop_loss
                .symbol(symbolXbtLtc)
                .build());


        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5001L)
                .price(15_070L)
                .reservePrice(15_600L) // can move bid order up to the 1.56 LTC, without replacing it
                .size(1L) // order size is 35 lots
                .action(OrderAction.BID)
                .orderType(OrderType.GTC) // Good-till-Cancel
                .symbol(symbolXbtLtc)
                .build());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6000L)
                .price(14_950L)
                .stopPrice(0L)
                .size(2L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.GTC) // stop_loss
                .symbol(symbolXbtLtc)
                .build());

        future = api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(302L)
                .orderId(6001L)
                .price(14_850L)
                .stopPrice(15_300L)
                .size(1L) // order size is 10 lots
                .action(OrderAction.ASK)
                .orderType(OrderType.STOP_LOSS) // stop_loss
                .symbol(symbolXbtLtc)
                .build());


        api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5002L)
                .price(15_080L)
                .reservePrice(15_600L)
                .size(3L)
                .action(OrderAction.BID)
                .orderType(OrderType.GTC) // Good-till-Cancel
                .symbol(symbolXbtLtc)
                .build());



        CompletableFuture<L2MarketData> orderBookFuture = api.requestOrderBookAsync(symbolXbtLtc, 10);
    }
}
