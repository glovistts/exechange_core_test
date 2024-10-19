package com.exechange_test.core.my;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.MatcherTradeEvent;
import com.exechange_test.core.common.Order;
import com.exechange_test.core.common.OrderAction;
import com.exechange_test.core.common.OrderType;
import com.exechange_test.core.common.api.ApiPlaceOrder;
import com.exechange_test.core.common.config.PerformanceConfiguration;
import com.exechange_test.core.orderbook.OrderBookNaiveImpl;
import com.exechange_test.core.orderbook.OrdersBucketNaive;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StopLossCheckThread implements Runnable {

    PerformanceConfiguration perfConfig;

    private final int BUCKET_SIZE=100000;
    private volatile boolean running = true;
    private final ExchangeApi api;

    public StopLossCheckThread() {
        this.api = AppConfig.getExchangeApi();
    }

    public void start() {
        new Thread(this).start();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                checkSLBuckets();
            } catch (Exception ignored) {
            }
        }
    }

    private void checkSLBuckets() {
        List<OrdersBucketNaive> buckets = new ArrayList<>(OrderBookNaiveImpl.getSlBuckets().values());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i += BUCKET_SIZE) {
            List<OrdersBucketNaive> chunk = buckets.subList(i, Math.min(i + BUCKET_SIZE, buckets.size()));
            threads.add(startNewThreadForChunk(chunk));
        }

        waitForThreadsToComplete(threads);
    }

    private Thread startNewThreadForChunk(List<OrdersBucketNaive> chunk) {
        Thread thread = new Thread(() -> processChunk(chunk));
        thread.start();
        return thread;
    }

    private void waitForThreadsToComplete(List<Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted: " + e.getMessage());
            }
        }
    }

    private void processChunk(List<OrdersBucketNaive> chunk) {
        for (OrdersBucketNaive bucket : chunk) {
            processBucketEntries(bucket);
        }
    }

    private void processBucketEntries(OrdersBucketNaive bucket) {

        bucket.getEntries().entrySet().stream()
                .filter(entry -> {
                    Order subBucket = entry.getValue();
                    long currentPrice = getCurrentPrice(subBucket.getSymbol());
                    return shouldActivateStopLoss(subBucket, currentPrice);
                })
                .forEach(entry -> {
                    bucket.getEntries().remove(entry.getKey());
                    submitStopLossOrderAsync(entry.getValue());
                });
    }

    private long getCurrentPrice(int symbol) {
        return Optional.ofNullable(MatcherTradeEvent.getSymbolPriceMap().get(symbol)).orElse(0L);
    }

    private boolean shouldActivateStopLoss(Order order, long currentPrice) {
        return order.getStopPrice() > currentPrice && currentPrice != 0;
    }

    private void submitStopLossOrderAsync(Order order) {
        CompletableFuture.runAsync(() -> {
            api.submitCommandAsync(ApiPlaceOrder.builder()
                    .uid(order.getUid())
                    .orderId(order.getOrderId())
                    .price(order.getPrice())
                    .size(order.getSize())
                    .action(OrderAction.ASK)
                    .orderType(OrderType.GTC)
                    .symbol(order.getSymbol())
                    .build());
        });
    }
}
