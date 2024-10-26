package com.exechange_test.core.my;

import com.exechange_test.core.ExchangeApi;
import com.exechange_test.core.common.MatcherTradeEvent;
import com.exechange_test.core.common.OrderAction;
import com.exechange_test.core.common.OrderType;
import com.exechange_test.core.common.api.ApiPlaceOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/order")
public class MyController {

    ExchangeApi api = AppConfig.getExchangeApi();

    @PostMapping("/submit")
    public Mono<ResponseEntity<String>> placeOrder(@RequestBody MyDto orderDto) {
        try {
            ApiPlaceOrder.ApiPlaceOrderBuilder builder = ApiPlaceOrder.builder()
                    .uid(orderDto.getUid())
                    .orderId(orderDto.getOrderId())
                    .price(orderDto.getPrice())
                    .size(orderDto.getSize())
                    .action(orderDto.getAction())
                    .orderType(orderDto.getOrderType())
                    .symbol(orderDto.getSymbol());

            if (orderDto.getOrderType() == OrderType.STOP_LOSS && orderDto.getAction() == OrderAction.ASK) {
                builder.stopPrice(orderDto.getStopPrice());
            } else if (orderDto.getAction() == OrderAction.BID) {
                builder.reservePrice(orderDto.getReservePrice());
            }

            return Mono.fromFuture(() -> api.submitCommandAsync(builder.build()))
                    .doOnSuccess(result -> System.out.println(MatcherTradeEvent.getSymbolPriceMap().get(241)))
                    .thenReturn(ResponseEntity.ok("Order placed successfully!"))
                    .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("Failed to place order: " + e.getMessage())));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(500).body("Failed to place order: " + e.getMessage()));
        }
    }
}
