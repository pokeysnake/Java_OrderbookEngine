package com.nicholasp.ordermatchingengine.controller;

import com.nicholasp.ordermatchingengine.engine.OrderBook;
import com.nicholasp.ordermatchingengine.model.Order;
import com.nicholasp.ordermatchingengine.model.OrderSide;
import com.nicholasp.ordermatchingengine.repository.TradeEntity;
import com.nicholasp.ordermatchingengine.service.OrderBookService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderBookController {

    private final OrderBookService orderBookService; //constructor injection

    public OrderBookController(OrderBookService orderBookService)
    {
        this.orderBookService = orderBookService; //takes constructor as parameter -> never need "new ..()"
    }

    //data carrier no logic needed
    public record PlaceOrderRequest(OrderSide side, BigDecimal price, long quantity) {} 


    //how are we going to map on the POST aka the @PostMapping
    //would use the 201 created code for the request if it is valid via ResponseEntity
    @PostMapping
    public ResponseEntity<OrderBookService.PlaceOrderResult> placeOrder(@RequestBody PlaceOrderRequest request)
    {
        //return the same type of orderBookService with parameters from auto-gen methods request.param()
        OrderBookService.PlaceOrderResult result = orderBookService.placeOrder(request.side(), request.price(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Order> cancelOrder(@PathVariable UUID id)
    {
        //if present, turn into a 200 with the order, otherwise 404
        
        return orderBookService.cancelByID(id)
            .map(order -> ResponseEntity.ok(order))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/book")
    public ResponseEntity<OrderBook.BookDepth> getBookDepth()
    {
        return ResponseEntity.ok(orderBookService.getBookDepth());
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeEntity>> getAllTrades()
    {
        return ResponseEntity.ok(orderBookService.getAllTrades());
    }

}
