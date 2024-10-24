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
import net.openhft.chronicle.core.util.Time;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;


@SpringBootApplication
@EnableScheduling
public abstract class ExechangeTestApplication  implements CommandLineRunner {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ExecutorService executorService = Executors.newFixedThreadPool(10);
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
        long userId=1;
        long mid=80_000L;
        ExecutorService executorService = Executors.newFixedThreadPool(1000); // You can adjust the pool size as needed
        while(userId < mid){
            long finalUserId = userId;
            api.submitCommandAsync(ApiAddUser.builder()
                    .uid(finalUserId)
                    .build());

            api.submitCommandAsync(ApiAddUser.builder()
                    .uid(finalUserId + 1)
                    .build());

            api.submitCommandAsync(ApiAdjustUserBalance.builder()
                    .uid(finalUserId)
                    .currency(currencyCodeLtc)
                    .amount(2_000_000_000L)
                    .transactionId(1L)
                    .build());

            api.submitCommandAsync(ApiAdjustUserBalance.builder()
                    .uid(finalUserId + 1)
                    .currency(currencyCodeXbt)
                    .amount(20_000_000L)
                    .transactionId(2L)
                    .build());
            userId += 2;
        }
        userId=1;
        while (userId < mid) {
            long finalUserId = userId;

            executorService.submit(() -> api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(finalUserId + 1)
                    .orderId(finalUserId*100000+finalUserId + 6002L)
                    .price(13_150L)
                    .stopPrice(13_300L)
                    .size(1L) // order size is 10 lots
                    .action(OrderAction.ASK)
                    .orderType(OrderType.STOP_LOSS) // stop_loss
                    .symbol(symbolXbtLtc)
                    .build()));

            executorService.submit(() -> api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(finalUserId + 1)
                    .orderId(finalUserId*100000+finalUserId + 6001L)
                    .price(13_150L)
                    .stopPrice(13_300L)
                    .size(1L) // order size is 10 lots
                    .action(OrderAction.ASK)
                    .orderType(OrderType.STOP_LOSS) // stop_loss
                    .symbol(symbolXbtLtc)
                    .build()));

            executorService.submit(() -> api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(finalUserId)
                    .orderId(finalUserId*100000+finalUserId + 5001L)
                    .price(15_070L)
                    .reservePrice(15_600L) // can move bid order up to the 1.56 LTC, without replacing it
                    .size(1L) // order size is 35 lots
                    .action(OrderAction.BID)
                    .orderType(OrderType.GTC) // Good-till-Cancel
                    .symbol(symbolXbtLtc)
                    .build()));

            executorService.submit(() -> api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(finalUserId + 1)
                    .orderId(finalUserId*100000+finalUserId + 6000L)
                    .price(14_950L)
                    .stopPrice(0L)
                    .size(2L) // order size is 10 lots
                    .action(OrderAction.ASK)
                    .orderType(OrderType.GTC) // stop_loss
                    .symbol(symbolXbtLtc)
                    .build()));

            executorService.submit(() -> api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(finalUserId)
                    .orderId(finalUserId*100000+finalUserId + 5002L)
                    .price(13_280L)
                    .reservePrice(15_600L)
                    .size(2L)
                    .action(OrderAction.BID)
                    .orderType(OrderType.GTC) // Good-till-Cancel
                    .symbol(symbolXbtLtc)
                    .build()));

            userId += 2;
        }

        executorService.shutdown();


        CompletableFuture<L2MarketData> orderBookFuture2 = api.requestOrderBookAsync(symbolXbtLtc, 10);
        System.out.println("ApiOrderBookRequest result: " + orderBookFuture2.get());
        scheduler.schedule(() -> {
            CompletableFuture<L2MarketData> orderBookFuture = api.requestOrderBookAsync(symbolXbtLtc, 10);
            try {
                System.out.println("orderBookFuture: " + orderBookFuture.get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, 5, TimeUnit.SECONDS);
    }
}
