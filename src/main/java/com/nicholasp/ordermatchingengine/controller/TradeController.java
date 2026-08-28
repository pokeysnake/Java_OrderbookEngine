package com.nicholasp.ordermatchingengine.controller;

import com.nicholasp.ordermatchingengine.repository.TradeEntity;
import com.nicholasp.ordermatchingengine.service.OrderBookService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final OrderBookService orderBookService;

    public TradeController(OrderBookService orderBookService)
    {
        this.orderBookService = orderBookService;
    }

    @GetMapping
    public ResponseEntity<List<TradeEntity>> getAllTrades()
    {
        return ResponseEntity.ok(orderBookService.getAllTrades());
    }

}
