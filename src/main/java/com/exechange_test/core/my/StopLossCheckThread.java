package com.exechange_test.core.my;

import com.exechange_test.core.common.MatcherTradeEvent;
import com.exechange_test.core.common.Order;
import com.exechange_test.core.orderbook.OrderBookNaiveImpl;
import com.exechange_test.core.orderbook.OrdersBucketNaive;

import java.util.LinkedHashMap;
import java.util.Optional;

public class StopLossCheckThread implements Runnable {
    private volatile boolean running = true;

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
                Thread.sleep(30000);
            }catch (Exception e){
                System.out.println(e);
            }
        }
    }

    private synchronized void checkSLBucket() {
        for (final OrdersBucketNaive bucket : OrderBookNaiveImpl.getSlBuckets().values()) {
            for (final Order subBucket : bucket.getEntries().values()) {
                Long currentPrice = Optional.ofNullable(MatcherTradeEvent.getSymbolPriceMap().get(subBucket.getSymbol())).orElse(0L);
                System.out.println(subBucket);
                System.out.println(subBucket.getSymbol());
                System.out.println(currentPrice);
            }
        }
    }
}