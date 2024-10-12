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

import exchange.core2.collections.objpool.ObjectsPool;
import com.exechange_test.core.common.*;
import com.exechange_test.core.common.cmd.CommandResultCode;
import com.exechange_test.core.common.cmd.OrderCommand;
import com.exechange_test.core.common.cmd.OrderCommandType;
import com.exechange_test.core.common.config.LoggingConfiguration;
import com.exechange_test.core.utils.HashingUtils;
import lombok.Getter;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.WriteBytesMarshallable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public interface IOrderBook extends WriteBytesMarshallable, StateHash {

    void newOrder(OrderCommand cmd);

    CommandResultCode cancelOrder(OrderCommand cmd);

    CommandResultCode reduceOrder(OrderCommand cmd);

    CommandResultCode moveOrder(OrderCommand cmd);

    int getOrdersNum(OrderAction action);

    long getTotalOrdersVolume(OrderAction action);

    IOrder getOrderById(long orderId);

    void validateInternalState();

    OrderBookImplType getImplementationType();

    List<Order> findUserOrders(long uid);

    CoreSymbolSpecification getSymbolSpec();

    Stream<? extends IOrder> askOrdersStream(boolean sorted);

    Stream<? extends IOrder> bidOrdersStream(boolean sorted);

    @Override
    default int stateHash() {
        return Objects.hash(
                HashingUtils.stateHashStream(askOrdersStream(true)),
                HashingUtils.stateHashStream(bidOrdersStream(true)),
                getSymbolSpec().stateHash());
    }


    default L2MarketData getL2MarketDataSnapshot(final int size) {
        final int asksSize = getTotalAskBuckets(size);
        final int bidsSize = getTotalBidBuckets(size);
        final L2MarketData data = new L2MarketData(asksSize, bidsSize);
        fillAsks(asksSize, data);
        fillBids(bidsSize, data);
        return data;
    }

    default L2MarketData getL2MarketDataSnapshot() {
        return getL2MarketDataSnapshot(Integer.MAX_VALUE);
    }

    default void publishL2MarketDataSnapshot(L2MarketData data) {
        int size = L2MarketData.L2_SIZE;
        fillAsks(size, data);
        fillBids(size, data);
    }

    void fillAsks(int size, L2MarketData data);

    void fillBids(int size, L2MarketData data);

    int getTotalAskBuckets(int limit);

    int getTotalBidBuckets(int limit);


    static CommandResultCode processCommand(final IOrderBook orderBook, final OrderCommand cmd) {

        final OrderCommandType commandType = cmd.command;

        if (commandType == OrderCommandType.MOVE_ORDER) {

            return orderBook.moveOrder(cmd);

        } else if (commandType == OrderCommandType.CANCEL_ORDER) {

            return orderBook.cancelOrder(cmd);

        } else if (commandType == OrderCommandType.REDUCE_ORDER) {

            return orderBook.reduceOrder(cmd);

        } else if (commandType == OrderCommandType.PLACE_ORDER) {

            if (cmd.resultCode == CommandResultCode.VALID_FOR_MATCHING_ENGINE) {
                orderBook.newOrder(cmd);
                return CommandResultCode.SUCCESS;
            } else {
                return cmd.resultCode; // no change
            }

        } else if (commandType == OrderCommandType.ORDER_BOOK_REQUEST) {
            int size = (int) cmd.size;
            cmd.marketData = orderBook.getL2MarketDataSnapshot(size >= 0 ? size : Integer.MAX_VALUE);
            return CommandResultCode.SUCCESS;

        } else {
            return CommandResultCode.MATCHING_UNSUPPORTED_COMMAND;
        }

    }

    static IOrderBook create(BytesIn bytes, ObjectsPool objectsPool, OrderBookEventsHelper eventsHelper, LoggingConfiguration loggingCfg) {
        switch (OrderBookImplType.of(bytes.readByte())) {
            case NAIVE:
                return new OrderBookNaiveImpl(bytes, loggingCfg);
            case DIRECT:
                return new OrderBookDirectImpl(bytes, objectsPool, eventsHelper, loggingCfg);
            default:
                throw new IllegalArgumentException();
        }
    }

    @FunctionalInterface
    interface OrderBookFactory {

        IOrderBook create(CoreSymbolSpecification spec, ObjectsPool pool, OrderBookEventsHelper eventsHelper, LoggingConfiguration loggingCfg);
    }

    @Getter
    enum OrderBookImplType {
        NAIVE(0),
        DIRECT(2);

        private byte code;

        OrderBookImplType(int code) {
            this.code = (byte) code;
        }

        public static OrderBookImplType of(byte code) {
            switch (code) {
                case 0:
                    return NAIVE;
                case 2:
                    return DIRECT;
                default:
                    throw new IllegalArgumentException("unknown OrderBookImplType:" + code);
            }
        }
    }


}
