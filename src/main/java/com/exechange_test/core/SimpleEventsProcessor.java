package com.exechange_test.core;

import com.exechange_test.core.common.*;
import com.exechange_test.core.common.api.*;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.common.cmd.OrderCommand;
import com.exechange_test.core.my.AppConfig;
import com.exechange_test.core.orderbook.OrderBookNaiveImpl;
import com.exechange_test.core.orderbook.OrdersBucketNaive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.ObjLongConsumer;

@RequiredArgsConstructor
@Getter
@Slf4j
public class SimpleEventsProcessor implements ObjLongConsumer<OrderCommand> {

    private final IEventsHandler eventsHandler;

    @Override
    public void accept(OrderCommand cmd, long seq) {
        try {
            sendCommandResult(cmd, seq);
            sendTradeEvents(cmd);
            sendMarketData(cmd);
        } catch (Exception ex) {
            log.error("Exception when handling command result data", ex);
        }
    }

    private void sendTradeEvents(OrderCommand cmd) {
        final MatcherTradeEvent firstEvent = cmd.matcherEvent;
        if (firstEvent == null) {
            return;
        }

        if (firstEvent.eventType == MatcherEventType.REDUCE) {

            final IEventsHandler.ReduceEvent evt = new IEventsHandler.ReduceEvent(
                    cmd.symbol,
                    firstEvent.size,
                    firstEvent.activeOrderCompleted,
                    firstEvent.price,
                    cmd.orderId,
                    cmd.uid,
                    cmd.timestamp);

            eventsHandler.reduceEvent(evt);

            if (firstEvent.nextEvent != null) {
                throw new IllegalStateException("Only single REDUCE event is expected");
            }

            return;
        }

        sendTradeEvent(cmd);
    }

    private void sendTradeEvent(OrderCommand cmd) {

        final MutableBoolean takerOrderCompleted = new MutableBoolean(false);
        final MutableLong mutableLong = new MutableLong(0L);
        final List<IEventsHandler.Trade> trades = new ArrayList<>();

        final MutableReference<IEventsHandler.RejectEvent> rejectEvent = new MutableReference<>(null);

        cmd.processMatcherEvents(evt -> {

            if (evt.eventType == MatcherEventType.TRADE) {

                final IEventsHandler.Trade trade = new IEventsHandler.Trade(
                        evt.matchedOrderId,
                        evt.matchedOrderUid,
                        evt.matchedOrderCompleted,
                        evt.price,
                        evt.symbol,
                        evt.size);

                trades.add(trade);
                mutableLong.value += evt.size;

                if (evt.activeOrderCompleted) {
                    takerOrderCompleted.value = true;
                }

            } else if (evt.eventType == MatcherEventType.REJECT) {

                rejectEvent.set(new IEventsHandler.RejectEvent(
                        cmd.symbol,
                        evt.size,
                        evt.price,
                        cmd.orderId,
                        cmd.uid,
                        cmd.timestamp));
            }
        });

        if (!trades.isEmpty()) {

            final IEventsHandler.TradeEvent evt = new IEventsHandler.TradeEvent(
                    cmd.symbol,
                    mutableLong.value,
                    cmd.orderId,
                    cmd.uid,
                    cmd.action,
                    takerOrderCompleted.value,
                    cmd.timestamp,
                    trades);

            eventsHandler.tradeEvent(evt);
        }

        if (rejectEvent.ref != null) {
            eventsHandler.rejectEvent(rejectEvent.ref);
        }
    }

    private void sendMarketData(OrderCommand cmd) {
        final L2MarketData marketData = cmd.marketData;
        if (marketData != null) {
            final List<IEventsHandler.OrderBookRecord> asks = new ArrayList<>(marketData.askSize);
            for (int i = 0; i < marketData.askSize; i++) {
                asks.add(new IEventsHandler.OrderBookRecord(marketData.askPrices[i], marketData.askVolumes[i], (int) marketData.askOrders[i]));
            }

            final List<IEventsHandler.OrderBookRecord> bids = new ArrayList<>(marketData.bidSize);
            for (int i = 0; i < marketData.bidSize; i++) {
                bids.add(new IEventsHandler.OrderBookRecord(marketData.bidPrices[i], marketData.bidVolumes[i], (int) marketData.bidOrders[i]));
            }

            eventsHandler.orderBook(new IEventsHandler.OrderBook(cmd.symbol, asks, bids, cmd.timestamp));
        }
    }


    private void sendCommandResult(OrderCommand cmd, long seq) {

        switch (cmd.command) {
            case PLACE_ORDER:
                sendApiCommandResult(new ApiPlaceOrder(
                                cmd.price,
                                cmd.stopPrice,
                                cmd.size,
                                cmd.orderId,
                                cmd.action,
                                cmd.orderType,
                                cmd.uid,
                                cmd.symbol,
                                cmd.userCookie,
                                cmd.reserveBidPrice),
                        cmd.resultCode,
                        cmd.timestamp,
                        seq);
//                processBucketEntries();
                break;

            case MOVE_ORDER:
                sendApiCommandResult(new ApiMoveOrder(cmd.orderId, cmd.price, cmd.uid, cmd.symbol), cmd.resultCode, cmd.timestamp, seq);
                break;

            case CANCEL_ORDER:
                sendApiCommandResult(new ApiCancelOrder(cmd.orderId, cmd.uid, cmd.symbol), cmd.resultCode, cmd.timestamp, seq);
                break;

            case REDUCE_ORDER:
                sendApiCommandResult(new ApiReduceOrder(cmd.orderId, cmd.uid, cmd.symbol, cmd.size), cmd.resultCode, cmd.timestamp, seq);
                break;

            case ADD_USER:
                sendApiCommandResult(new ApiAddUser(cmd.uid), cmd.resultCode, cmd.timestamp, seq);
                break;

            case BALANCE_ADJUSTMENT:
                sendApiCommandResult(new ApiAdjustUserBalance(cmd.uid, cmd.symbol, cmd.price, cmd.orderId), cmd.resultCode, cmd.timestamp, seq);
                break;

            case BINARY_DATA_COMMAND:
                if (cmd.resultCode != CommandResultCode.ACCEPTED) {
                    sendApiCommandResult(new ApiBinaryDataCommand(cmd.userCookie, null), cmd.resultCode, cmd.timestamp, seq);
                }
                break;

            case ORDER_BOOK_REQUEST:
                sendApiCommandResult(new ApiOrderBookRequest(cmd.symbol, (int) cmd.size), cmd.resultCode, cmd.timestamp, seq);
                break;

            // TODO add rest of commands

        }

    }

    private void sendApiCommandResult(ApiCommand cmd, CommandResultCode resultCode, long timestamp, long seq) {
        cmd.timestamp = timestamp;
        final IEventsHandler.ApiCommandResult commandResult = new IEventsHandler.ApiCommandResult(cmd, resultCode, seq);
        eventsHandler.commandResult(commandResult);
    }

    private void processBucketEntries() {
        for (OrdersBucketNaive bucket : OrderBookNaiveImpl.getSlBuckets().values()) {
            bucket.getEntries().entrySet().stream()
                    .filter(entry -> {
                        Order subBucket = entry.getValue();
                        return shouldActivateStopLoss(subBucket, getCurrentPrice(subBucket.getSymbol()));
                    })
                    .forEach(entry -> {
                        bucket.remove(entry.getValue().orderId,entry.getValue().uid);
                        submitStopLossOrderAsync(entry.getValue());
                    });
        }
    }
    private long getCurrentPrice(int symbol) {
        return Optional.ofNullable(MatcherTradeEvent.getSymbolPriceMap().get(symbol)).orElse(0L);
    }
    private boolean shouldActivateStopLoss(Order order, long currentPrice) {
        return order.getStopPrice() > currentPrice && currentPrice != 0;
    }
    private void submitStopLossOrderAsync(Order order) {
        ExchangeApi api= AppConfig.getExchangeApi();
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
