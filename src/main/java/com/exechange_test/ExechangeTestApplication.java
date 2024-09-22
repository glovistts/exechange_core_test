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
import com.exechange_test.core.common.cmd.OrderCommand;
import com.exechange_test.core.common.config.ExchangeConfiguration;
import com.exechange_test.core.orderbook.IOrderBook;
import com.exechange_test.core.orderbook.OrderBookNaiveImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.exechange_test.core.common.OrderAction.ASK;
import static com.exechange_test.core.common.OrderType.IOC;

@SpringBootApplication
public abstract class ExechangeTestApplication {
    IOrderBook orderBook;
    protected abstract IOrderBook createNewOrderBook();

    public static void main(String[] args) {

        ExchangeConfiguration conf = ExchangeConfiguration.defaultBuilder().build();
        ExchangeCore exchangeCore = ExchangeCore.builder()
                .exchangeConfiguration(conf)
                .build();
        exchangeCore.startup();
        Future<CommandResultCode> future;
        ExchangeApi api = exchangeCore.getApi();
        future = api.submitCommandAsync(ApiAddUser.builder()
                .uid(301L)
                .build());
        OrderCommand.newOrder(
                OrderType.STOP_LOSS,
                1L,
                1L,
                39_000L,
                40_000L,
                0L,
                6L,
                ASK
        );
        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE);

        long bidSum = Arrays.stream(snapshot.bidVolumes).sum();

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(IOC, 100000000001L, -2, 1, 0, 0L,bidSum, ASK));

    }


}
