package com.exechange_test.core.my;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.MatcherTradeEvent;
import com.exechange_test.core.common.Order;
import com.exechange_test.core.common.OrderAction;
import com.exechange_test.core.common.OrderType;
import com.exechange_test.core.common.api.ApiPlaceOrder;
import com.exechange_test.core.orderbook.OrderBookNaiveImpl;
import com.exechange_test.core.orderbook.OrdersBucketNaive;

import java.util.*;

public class StopLossCheckThread implements Runnable {
    private volatile boolean running = true;
    ExchangeApi api = AppConfig.getExchangeApi();

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                checkSLBucket();
            }catch (Exception e){
                System.out.println(e);
            }
        }
    }

    private void checkSLBucket() {
        List<OrdersBucketNaive> buckets = new ArrayList<>(OrderBookNaiveImpl.getSlBuckets().values());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i += 1000) {
            int end = Math.min(i + 1000, buckets.size());
            List<OrdersBucketNaive> chunk = buckets.subList(i, end);

            Thread thread = new Thread(() -> processChunk(chunk));
            thread.start();
            threads.add(thread); // Store the thread reference
        }

        // Wait for all threads to finish
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                System.out.println("Thread was interrupted: " + e.getMessage());
            }
        }
    }


    private void processChunk(List<OrdersBucketNaive> chunk) {
        for (OrdersBucketNaive bucket : chunk) {
            Iterator<Map.Entry<Long, Order>> iterator = bucket.getEntries().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Order> entry = iterator.next();
                Order subBucket = entry.getValue();
                long currentPrice = Optional.ofNullable(MatcherTradeEvent.getSymbolPriceMap().get(subBucket.getSymbol())).orElse(0L);

                if (!(subBucket.getStopPrice() > currentPrice && currentPrice != 0)) continue;

                iterator.remove();
                    api.submitCommandAsync(ApiPlaceOrder.builder()
                            .uid(subBucket.getUid())
                            .orderId(subBucket.getOrderId())
                            .price(subBucket.getPrice())
                            .size(subBucket.getSize())
                            .action(OrderAction.ASK)
                            .orderType(OrderType.GTC)
                            .symbol(subBucket.getSymbol())
                            .build());

            }
        }
    }

}