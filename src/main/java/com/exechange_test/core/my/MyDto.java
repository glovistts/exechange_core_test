package com.exechange_test.core.my;

import com.exechange_test.core.common.OrderAction;
import com.exechange_test.core.common.OrderType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyDto {
    private Long uid;
    private Long orderId;
    private Long price;
    private Long reservePrice;
    private long stopPrice;
    private Long size;
    private OrderAction action;
    private OrderType orderType;
    private int symbol;
}
