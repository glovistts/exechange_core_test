package com.exechange_test.core.my;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.ExchangeCore;
import com.exechange_test.core.IEventsHandler;
import com.exechange_test.core.SimpleEventsProcessor;
import com.exechange_test.core.common.config.ExchangeConfiguration;
import com.exechange_test.core.processors.journaling.DummySerializationProcessor;
import com.exechange_test.core.processors.journaling.ISerializationProcessor;
import lombok.Getter;

import java.util.function.Supplier;

public class AppConfig {
    @Getter
    private static final ExchangeApi exchangeApi;

    static {
        // Default exchange configuration
        ExchangeConfiguration conf = ExchangeConfiguration.defaultBuilder().build();

        // Example events processor
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

        // No serialization
        Supplier<ISerializationProcessor> serializationProcessorFactory = () -> DummySerializationProcessor.INSTANCE;

        // Build exchange core
        ExchangeCore exchangeCore = ExchangeCore.builder()
                .resultsConsumer(eventsProcessor)
                .exchangeConfiguration(conf)
                .build();

        // Start up disruptor threads
        exchangeCore.startup();

        // Get exchange API for publishing commands
        exchangeApi = exchangeCore.getApi();
    }

}

