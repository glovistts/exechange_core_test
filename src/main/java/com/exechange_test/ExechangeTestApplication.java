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

    public static void main(String[] args) {}


}
