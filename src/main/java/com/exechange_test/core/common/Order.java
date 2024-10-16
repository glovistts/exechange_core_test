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
package com.exechange_test.core.common;

import lombok.*;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.WriteBytesMarshallable;

import java.util.Objects;

/**
 * Extending OrderCommand allows to avoid creating new objects
 * for instantly matching orders (MARKET or marketable LIMIT orders)
 * as well as use same code for matching moved orders
 * <p>
 * No external references allowed to such object - order objects only live inside OrderBook.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class Order implements WriteBytesMarshallable, IOrder {

    @Getter
    public long orderId;

    @Getter
    public long price;

    @Getter
    public int symbol;

    @Getter
    public long size;


    @Getter
    public long filled;

    @Getter
    public long reserveBidPrice;

    @Getter
    public long stopPrice;
    // new orders - reserved price for fast moves of GTC bid orders in exchange mode

    // required for PLACE_ORDER only;
    @Getter
    public OrderAction action;

    @Getter
    @Setter
    public OrderType orderType;

    @Getter
    public long uid;

    @Getter
    public long timestamp;

//    public int userCookie;

    public Order(BytesIn bytes) {


        this.orderId = bytes.readLong(); // orderId
        this.price = bytes.readLong();  // price
        this.symbol=bytes.readInt();
        this.size = bytes.readLong(); // size
        this.filled = bytes.readLong(); // filled
        this.reserveBidPrice = bytes.readLong(); // price2
        this.stopPrice = bytes.readLong(); // price2
        this.action = OrderAction.of(bytes.readByte());
        this.orderType=OrderType.of(bytes.readByte());
        this.uid = bytes.readLong(); // uid
        this.timestamp = bytes.readLong(); // timestamp
//        this.userCookie = bytes.readInt();  // userCookie

    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeLong(orderId);
        bytes.writeLong(price);
        bytes.writeLong(size);
        bytes.writeLong(filled);
        bytes.writeLong(reserveBidPrice);
        bytes.writeLong(stopPrice);
        bytes.writeByte(action.getCode());
        bytes.writeByte(orderType.getCode());
        bytes.writeLong(uid);
        bytes.writeLong(timestamp);
//        bytes.writeInt(userCookie);
    }

    @Override
    public String toString() {
        return "[" + orderId + " " + (action == OrderAction.ASK ? 'A' : 'B')
                + price + ":" + size + "F" + filled
                // + " C" + userCookie
                + " U" + uid + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, action, price, size, reserveBidPrice, stopPrice, filled,
                //userCookie, timestamp
                uid);
    }


    /**
     * timestamp is not included into hashCode() and equals() for repeatable results
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof Order)) return false;

        Order other = (Order) o;

        // ignore timestamp and userCookie
        return orderId == other.orderId
                && action == other.action
                && price == other.price
                && size == other.size
                && stopPrice == other.stopPrice
                && reserveBidPrice == other.reserveBidPrice
                && filled == other.filled
                && uid == other.uid;
    }

    @Override
    public int stateHash() {
        return hashCode();
    }
}
