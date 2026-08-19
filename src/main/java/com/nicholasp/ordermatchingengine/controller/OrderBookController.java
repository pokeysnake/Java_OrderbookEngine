package com.nicholasp.ordermatchingengine.controller;

import com.nicholasp.ordermatchingengine.model.OrderSide;
import com.nicholasp.ordermatchingengine.model.Trade;
import com.nicholasp.ordermatchingengine.service.OrderBookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderBookController {

    private final OrderBookService orderBookService; //constructor injection

    public OrderBookController(OrderBookService orderBookService)
    {
        this.orderBookService = orderBookService; //takes constructor as parameter -> never need "new ..()"
    }

    //data carrier no logic needed
    public record PlaceOrderRequest(OrderSide side, BigDecimal price, long quantity) {} 


    //how are we going to map on the POST aka the @PostMapping
    @PostMapping
    public ResponseEntity<List<Trade>> placeOrder(@RequestBody PlaceOrderRequest request)
    {
        //return the same type of orderBookService with parameters from auto-gen methods request.param()
        List<Trade> trades = orderBookService.placeOrder(request.side(), request.price(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(trades);
    }
}
