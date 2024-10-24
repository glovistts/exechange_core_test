/*
 * Copyright 2019 Maksim Zheravin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exechange_test.core.orderbook;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.api.ApiPlaceOrder;
import com.exechange_test.core.my.AppConfig;
import exchange.core2.collections.objpool.ObjectsPool;
import com.exechange_test.core.common.*;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.common.cmd.OrderCommand;
import com.exechange_test.core.common.config.LoggingConfiguration;
import com.exechange_test.core.utils.SerializationUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class OrderBookNaiveImpl implements IOrderBook {

    private final Boolean EnableSL = true;
    private final Long roundVal = 50L;
    private final ExchangeApi api;
    @Getter
    private static NavigableMap<Long, OrdersBucketNaive> askBuckets;
    @Getter
    private static NavigableMap<Long, OrdersBucketNaive> bidBuckets;
    @Getter
    private static NavigableMap<Long, OrdersBucketNaive> slBuckets;

    private final CoreSymbolSpecification symbolSpec;

    private final LongObjectHashMap<Order> idMap = new LongObjectHashMap<>();

    private final LongObjectHashMap<Order> askMapSL = new LongObjectHashMap<>();

    //private final ConcurrentHashMap<Long, List<Order>> bidMapSL = new ConcurrentHashMap<>();
    //private final ConcurrentHashMap<Long, List<Order>> askMapSL = new ConcurrentHashMap<>();


    private final ReentrantLock lock = new ReentrantLock();

    private final List<Long> bidRangeList = Collections.synchronizedList(new ArrayList<Long>());
    private final List<Long> askRangeList = Collections.synchronizedList(new ArrayList<Long>());
    private final OrderBookEventsHelper eventsHelper;

    private final boolean logDebug;

    public OrderBookNaiveImpl(final CoreSymbolSpecification symbolSpec,
                              final ObjectsPool pool,
                              final OrderBookEventsHelper eventsHelper,
                              final LoggingConfiguration loggingCfg) {
        this.api = AppConfig.getExchangeApi();
        this.symbolSpec = symbolSpec;
        this.askBuckets = new TreeMap<>();
        this.slBuckets=new TreeMap<>();
        this.bidBuckets = new TreeMap<>(Collections.reverseOrder());
        this.eventsHelper = eventsHelper;
        this.logDebug = loggingCfg.getLoggingLevels().contains(LoggingConfiguration.LoggingLevel.LOGGING_MATCHING_DEBUG);
    }



    public OrderBookNaiveImpl(final CoreSymbolSpecification symbolSpec,
                              final LoggingConfiguration loggingCfg) {
        this.api = AppConfig.getExchangeApi();
        this.symbolSpec = symbolSpec;
        this.askBuckets = new TreeMap<>();
        this.bidBuckets = new TreeMap<>(Collections.reverseOrder());
        this.slBuckets = new TreeMap<>();
        this.eventsHelper = OrderBookEventsHelper.NON_POOLED_EVENTS_HELPER;
        this.logDebug = loggingCfg.getLoggingLevels().contains(LoggingConfiguration.LoggingLevel.LOGGING_MATCHING_DEBUG);
    }

    public OrderBookNaiveImpl(final BytesIn bytes, final LoggingConfiguration loggingCfg) {
        this.api = AppConfig.getExchangeApi();
        this.symbolSpec = new CoreSymbolSpecification(bytes);
        this.askBuckets = SerializationUtils.readLongMap(bytes, TreeMap::new, OrdersBucketNaive::new);
        this.slBuckets = SerializationUtils.readLongMap(bytes, TreeMap::new, OrdersBucketNaive::new);

        this.bidBuckets = SerializationUtils.readLongMap(bytes, () -> new TreeMap<>(Collections.reverseOrder()), OrdersBucketNaive::new);

        this.eventsHelper = OrderBookEventsHelper.NON_POOLED_EVENTS_HELPER;
        askBuckets.values().forEach(bucket -> bucket.forEachOrder(order -> idMap.put(order.orderId, order)));
        bidBuckets.values().forEach(bucket -> bucket.forEachOrder(order -> idMap.put(order.orderId, order)));
        slBuckets.values().forEach(bucket -> bucket.forEachOrder(order -> idMap.put(order.orderId, order)));

        this.logDebug = loggingCfg.getLoggingLevels().contains(LoggingConfiguration.LoggingLevel.LOGGING_MATCHING_DEBUG);
    }

    @Override
    public void newOrder(final OrderCommand cmd) {

        switch (cmd.orderType) {
            case GTC:
                newOrderPlaceGtc(cmd);
                break;
            case IOC:
                newOrderMatchIoc(cmd);
                break;
            case FOK_BUDGET:
                newOrderMatchFokBudget(cmd);
                break;
            case STOP_LOSS:
                newOrderPlaceSL(cmd);
                break;
            default:
                log.warn("Unsupported order type: {}", cmd);
                eventsHelper.attachRejectEvent(cmd, cmd.size);
        }
    }

    private void newOrderPlaceSL(final OrderCommand cmd) {

        final OrderAction action = cmd.action;
        final OrderType order=cmd.orderType;
        final long price = cmd.price;
        final long size = cmd.size;
        final long stopPrice = cmd.stopPrice;
        final int symbol=cmd.symbol;
        final long orderId=cmd.orderId;
        OrderType orderTypeSL=cmd.orderType;

//        if(shouldActivateStopLoss(cmd.stopPrice,getCurrentPrice(cmd.symbol))) { orderTypeSL=OrderType.GTC;}
            final Order orderRecord = new Order(
                    orderId,
                    price,
                    symbol,
                    size,
                    0L,
                    cmd.reserveBidPrice,
                    stopPrice,
                    action,
                    orderTypeSL,
                    cmd.uid,
                    cmd.timestamp);

        getBucketsByOrderType(order).computeIfAbsent(price, p -> new OrdersBucketNaive(p, stopPrice)).put(orderRecord);

    }
    private long getCurrentPrice(int symbol) {
        return Optional.ofNullable(MatcherTradeEvent.getSymbolPriceMap().get(symbol)).orElse(0L);
    }
    private boolean shouldActivateStopLoss(long stopPrice, long currentPrice) {
        return stopPrice > currentPrice && currentPrice != 0;
    }

    private void newOrderPlaceGtc(final OrderCommand cmd) {

        final OrderAction action = cmd.action;
        final long price = cmd.price;
        final long size = cmd.size;
        final long stopPrice = cmd.stopPrice;
        final int symbol=cmd.symbol;
        final OrderType orderType=cmd.orderType;

        final long filledSize = tryMatchInstantly(cmd,
                subtreeForMatching(action, price),
                0, cmd);
        if (filledSize == size) {
            return;
        }

        long newOrderId = cmd.orderId;
        if (idMap.containsKey(newOrderId)) {
            eventsHelper.attachRejectEvent(cmd, cmd.size - filledSize);
            log.warn("duplicate order id: {}", cmd);
            return;
        }

        final Order orderRecord = new Order(
                newOrderId,
                price,
                symbol,
                size,
                filledSize,
                cmd.reserveBidPrice,
                stopPrice,
                action,
                orderType,
                cmd.uid,
                cmd.timestamp);

        getBucketsByAction(action).computeIfAbsent(price, p -> new OrdersBucketNaive(p, stopPrice)).put(orderRecord);

        idMap.put(newOrderId, orderRecord);
    }

    private void newOrderMatchIoc(final OrderCommand cmd) {

        final long filledSize = tryMatchInstantly(cmd,
                subtreeForMatching(cmd.action, cmd.price)
                , 0, cmd);

        final long rejectedSize = cmd.size - filledSize;

        if (rejectedSize != 0) {
            // was not matched completely - send reject for not-completed IoC order
            eventsHelper.attachRejectEvent(cmd, rejectedSize);
        }
    }

    private void newOrderMatchFokBudget(final OrderCommand cmd) {

        final long size = cmd.size;

        final SortedMap<Long, OrdersBucketNaive> subtreeForMatching =
                cmd.action == OrderAction.ASK ? bidBuckets : askBuckets;

        final Optional<Long> budget = checkBudgetToFill(size, subtreeForMatching);

        if (logDebug) log.debug("Budget calc: {} requested: {}", budget, cmd.price);

        if (budget.isPresent() && isBudgetLimitSatisfied(cmd.action, budget.get(), cmd.price)) {
            tryMatchInstantly(cmd, subtreeForMatching, 0, cmd);
        } else {
            eventsHelper.attachRejectEvent(cmd, size);
        }
    }

    private boolean isBudgetLimitSatisfied(final OrderAction orderAction, final long calculated, final long limit) {
        return calculated == limit || (orderAction == OrderAction.BID ^ calculated > limit);
    }

    private Optional<Long> checkBudgetToFill(
            long size,
            final SortedMap<Long, OrdersBucketNaive> matchingBuckets) {

        long budget = 0;

        for (final OrdersBucketNaive bucket : matchingBuckets.values()) {

            final long availableSize = bucket.getTotalVolume();
            final long price = bucket.getPrice();

            if (size > availableSize) {
                size -= availableSize;
                budget += availableSize * price;
                if (logDebug) log.debug("add    {} * {} -> {}", price, availableSize, budget);
            } else {
                final long result = budget + size * price;
                if (logDebug) log.debug("return {} * {} -> {}", price, size, result);
                return Optional.of(result);
            }
        }
        if (logDebug) log.debug("not enough liquidity to fill size={}", size);
        return Optional.empty();
    }
    private SortedMap<Long, OrdersBucketNaive> subtreeForMatching(final OrderAction action, final long price) {
        return (action == OrderAction.ASK ? bidBuckets : askBuckets)
                .headMap(price, true);
    }
    private long tryMatchInstantly(
            final IOrder activeOrder,
            final SortedMap<Long, OrdersBucketNaive> matchingBuckets,
            long filled,
            final OrderCommand triggerCmd) {
        if (matchingBuckets.size() == 0) {
            return filled;
        }
        final long orderSize = activeOrder.getSize();
        MatcherTradeEvent eventsTail = null;
        List<Long> emptyBuckets = new ArrayList<>();
        for (final OrdersBucketNaive bucket : matchingBuckets.values()) {
               final long sizeLeft = orderSize - filled;
                final OrdersBucketNaive.MatcherResult bucketMatchings = bucket.match(sizeLeft, activeOrder, eventsHelper);
                bucketMatchings.ordersToRemove.forEach(idMap::remove);
                filled += bucketMatchings.volume;
                if (eventsTail == null) {
                    triggerCmd.matcherEvent = bucketMatchings.eventsChainHead;
                } else {
                    eventsTail.nextEvent = bucketMatchings.eventsChainHead;
                }
                eventsTail = bucketMatchings.eventsChainTail;
                long price = bucket.getPrice();
                if (bucket.getTotalVolume() == 0) {
                    emptyBuckets.add(price);
                }
                if (filled == orderSize) {
                    break;
                }
        }

        // TODO can remove through iterator ??
        emptyBuckets.forEach(matchingBuckets::remove);
        return filled;
    }


    public static long roundUp(long actualValue, long nextRoundedValue) {
        long roundedUpValue = actualValue;

        if(actualValue%nextRoundedValue != 0)
            roundedUpValue = (((actualValue/nextRoundedValue)) * nextRoundedValue) + nextRoundedValue;
        return roundedUpValue;
    }
    /**
     * Remove an order.<p>
     *
     * @param cmd cancel command (orderId - order to remove)
     * @return true if order removed, false if not found (can be removed/matched earlier)
     */
    public CommandResultCode cancelOrder(OrderCommand cmd) {
        final long orderId = cmd.orderId;

        final Order order = idMap.get(orderId);
        if (order == null || order.uid != cmd.uid) {
            // order already matched and removed from order book previously
            return CommandResultCode.MATCHING_UNKNOWN_ORDER_ID;
        }

        // now can remove it
        idMap.remove(orderId);

        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.action);
        final long price = order.price;
        final OrdersBucketNaive ordersBucket = buckets.get(price);
        if (ordersBucket == null) {
            throw new IllegalStateException("Can not find bucket for order price=" + price + " for order " + order);
        }
        ordersBucket.remove(orderId, cmd.uid);
        if (ordersBucket.getTotalVolume() == 0) {
            buckets.remove(price);
        }
        cmd.matcherEvent = eventsHelper.sendReduceEvent(order, order.getSize() - order.getFilled(), true);
        cmd.action = order.getAction();

        return CommandResultCode.SUCCESS;
    }

    @Override
    public CommandResultCode reduceOrder(OrderCommand cmd) {
        final long orderId = cmd.orderId;
        final long requestedReduceSize = cmd.size;

        if (requestedReduceSize <= 0) {
            return CommandResultCode.MATCHING_REDUCE_FAILED_WRONG_SIZE;
        }

        final Order order = idMap.get(orderId);
        if (order == null || order.uid != cmd.uid) {
            return CommandResultCode.MATCHING_UNKNOWN_ORDER_ID;
        }

        final long remainingSize = order.size - order.filled;
        final long reduceBy = Math.min(remainingSize, requestedReduceSize);

        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.action);
        final OrdersBucketNaive ordersBucket = buckets.get(order.price);
        if (ordersBucket == null) {
            throw new IllegalStateException("Can not find bucket for order price=" + order.price + " for order " + order);
        }

        final boolean canRemove = (reduceBy == remainingSize);

        if (canRemove) {
            idMap.remove(orderId);
            ordersBucket.remove(orderId, cmd.uid);
            if (ordersBucket.getTotalVolume() == 0) {
                buckets.remove(order.price);
            }

        } else {

            order.size -= reduceBy;
            ordersBucket.reduceSize(reduceBy);
        }
        cmd.matcherEvent = eventsHelper.sendReduceEvent(order, reduceBy, canRemove);
        cmd.action = order.getAction();

        return CommandResultCode.SUCCESS;
    }

    @Override
    public CommandResultCode moveOrder(OrderCommand cmd) {

        final long orderId = cmd.orderId;
        final long newPrice = cmd.price;
        final long stopPrice = cmd.stopPrice;

        final Order order = idMap.get(orderId);
        if (order == null || order.uid != cmd.uid) {
            return CommandResultCode.MATCHING_UNKNOWN_ORDER_ID;
        }

        final long price = order.price;
        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.action);
        final OrdersBucketNaive bucket = buckets.get(price);

        cmd.action = order.getAction();
        if (symbolSpec.type == SymbolType.CURRENCY_EXCHANGE_PAIR
                && order.action == OrderAction.BID
                && cmd.price > order.reserveBidPrice) {
            return CommandResultCode.MATCHING_MOVE_FAILED_PRICE_OVER_RISK_LIMIT;
        }
        bucket.remove(orderId, cmd.uid);
        if (bucket.getTotalVolume() == 0) {buckets.remove(price);}
        order.price = newPrice;

        // try match with new price
        final SortedMap<Long, OrdersBucketNaive> matchingArea = subtreeForMatching(order.action, newPrice);
        final long filled = tryMatchInstantly(order, matchingArea, order.filled, cmd);
        if (filled == order.size) {
            // order was fully matched (100% marketable) - removing from order book
            idMap.remove(orderId);
            return CommandResultCode.SUCCESS;
        }
        order.filled = filled;

        // if not filled completely - put it into corresponding bucket
        final OrdersBucketNaive anotherBucket = buckets.computeIfAbsent(newPrice, p -> {
            OrdersBucketNaive b = new OrdersBucketNaive(p,stopPrice);
            return b;
        });
        anotherBucket.put(order);

        return CommandResultCode.SUCCESS;
    }

    /**
     * Get bucket by order action
     *
     * @param action - action
     * @return bucket - navigable map
     */
    private NavigableMap<Long, OrdersBucketNaive> getBucketsByAction(OrderAction action) {
        return action == OrderAction.ASK ? askBuckets : bidBuckets;
    }
    private NavigableMap<Long, OrdersBucketNaive> getBucketsByOrderType(OrderType type) {
        return type == OrderType.STOP_LOSS ? slBuckets : slBuckets;
    }


    /**
     * Get order from internal map
     *
     * @param orderId - order Id
     * @return order from map
     */
    @Override
    public IOrder getOrderById(long orderId) {
        return idMap.get(orderId);
    }

    @Override
    public void fillAsks(final int size, L2MarketData data) {
        if (size == 0) {
            data.askSize = 0;
            return;
        }

        int i = 0;
        for (OrdersBucketNaive bucket : askBuckets.values()) {
            data.askPrices[i] = bucket.getPrice();
            data.askVolumes[i] = bucket.getTotalVolume();
            data.askOrders[i] = bucket.getNumOrders();
            if (++i == size) {
                break;
            }
        }
        data.askSize = i;
    }

    @Override
    public void fillBids(final int size, L2MarketData data) {
        if (size == 0) {
            data.bidSize = 0;
            return;
        }

        int i = 0;
        for (OrdersBucketNaive bucket : bidBuckets.values()) {
            data.bidPrices[i] = bucket.getPrice();
            data.bidVolumes[i] = bucket.getTotalVolume();
            data.bidOrders[i] = bucket.getNumOrders();
            if (++i == size) {
                break;
            }
        }
        data.bidSize = i;
    }

    @Override
    public int getTotalAskBuckets(final int limit) {
        return Math.min(limit, askBuckets.size());
    }

    @Override
    public int getTotalBidBuckets(final int limit) {
        return Math.min(limit, bidBuckets.size());
    }

    @Override
    public void validateInternalState() {
        askBuckets.values().forEach(OrdersBucketNaive::validate);
        bidBuckets.values().forEach(OrdersBucketNaive::validate);
    }

    @Override
    public OrderBookImplType getImplementationType() {
        return OrderBookImplType.NAIVE;
    }

    @Override
    public List<Order> findUserOrders(final long uid) {
        List<Order> list = new ArrayList<>();
        Consumer<OrdersBucketNaive> bucketConsumer = bucket -> bucket.forEachOrder(order -> {
            if (order.uid == uid) {
                list.add(order);
            }
        });
        askBuckets.values().forEach(bucketConsumer);
        bidBuckets.values().forEach(bucketConsumer);
        return list;
    }

    @Override
    public CoreSymbolSpecification getSymbolSpec() {
        return symbolSpec;
    }

    @Override
    public Stream<IOrder> askOrdersStream(final boolean sorted) {
        return askBuckets.values().stream().flatMap(bucket -> bucket.getAllOrders().stream());
    }

    @Override
    public Stream<IOrder> bidOrdersStream(final boolean sorted) {
        return bidBuckets.values().stream().flatMap(bucket -> bucket.getAllOrders().stream());
    }

    // for testing only
    @Override
    public int getOrdersNum(OrderAction action) {
        final NavigableMap<Long, OrdersBucketNaive> buckets = action == OrderAction.ASK ? askBuckets : bidBuckets;
        return buckets.values().stream().mapToInt(OrdersBucketNaive::getNumOrders).sum();
//        int askOrders = askBuckets.values().stream().mapToInt(OrdersBucketNaive::getNumOrders).sum();
//        int bidOrders = bidBuckets.values().stream().mapToInt(OrdersBucketNaive::getNumOrders).sum();
        //log.debug("idMap:{} askOrders:{} bidOrders:{}", idMap.size(), askOrders, bidOrders);
//        int knownOrders = idMap.size();
//        assert knownOrders == askOrders + bidOrders : "inconsistent known orders";
    }

    @Override
    public long getTotalOrdersVolume(OrderAction action) {
        final NavigableMap<Long, OrdersBucketNaive> buckets = action == OrderAction.ASK ? askBuckets : bidBuckets;
        return buckets.values().stream().mapToLong(OrdersBucketNaive::getTotalVolume).sum();
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeByte(getImplementationType().getCode());
        symbolSpec.writeMarshallable(bytes);
        SerializationUtils.marshallLongMap(askBuckets, bytes);
        SerializationUtils.marshallLongMap(bidBuckets, bytes);
    }
}
