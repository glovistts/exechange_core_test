package InvidualTest;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.*;
import com.exechange_test.core.common.api.*;
import com.exechange_test.core.common.api.binary.BatchAddSymbolsCommand;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.my.AppConfig;
import com.exechange_test.core.my.StopLossCheckThread;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class ThreadTest {
    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    public void sampleTestWithThread() throws Exception {
//        StopLossCheckThread stopLossThread = new StopLossCheckThread();
//        stopLossThread.start();
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
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(301L)
                .build());
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(302L)
                .build());
        future = api.submitCommandAsync(ApiAdjustUserBalance.builder()
                .uid(301L)
                .currency(currencyCodeLtc)
                .amount(2_000_000_000L)
                .transactionId(1L)
                .build());
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
                .uid(302L)
                .orderId(6001L)
                .price(14_850L)
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
        api.submitCommandAsync(ApiPlaceOrder.builder()
                .uid(301L)
                .orderId(5002L)
                .price(15_080L)
                .reservePrice(15_600L)
                .size(2L)
                .action(OrderAction.BID)
                .orderType(OrderType.GTC) // Good-till-Cancel
                .symbol(symbolXbtLtc)
                .build());

        Thread.sleep(1200);

        CompletableFuture<L2MarketData> orderBookFuture = api.requestOrderBookAsync(symbolXbtLtc, 10);
        long[] expectedBidVolumes = {1L};
        //long[] expectedBidPrices = {14850};
        int expectedBidSize = 1;
        L2MarketData orderBook = null;
        try {
            orderBook = orderBookFuture.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        assertArrayEquals(expectedBidVolumes, orderBook.askVolumes);
        //assertArrayEquals(expectedBidPrices, orderBook.askPrices);
        assertEquals(expectedBidSize, orderBook.askSize);

    }
}
