package com.nicholasp.ordermatchingengine.service;

import com.nicholasp.ordermatchingengine.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookServiceTest {

    @Test
    void placeOrderTest()
    {
        OrderBookService service = new OrderBookService();
        OrderBookService.PlaceOrderResult resting = service.placeOrder(OrderSide.SELL, new BigDecimal("100"), 50);
        OrderBookService.PlaceOrderResult matching = service.placeOrder(OrderSide.BUY, new BigDecimal("100"), 50);

        assertNotNull(resting.orderId());
        assertTrue(resting.trades().isEmpty());

        assertNotNull(matching.orderId());
        assertEquals(1, matching.trades().size());
    }
    

    @Test
    void invalidInputThrows()
    {
        assertThrows(IllegalArgumentException.class,() -> new OrderBookService().placeOrder(OrderSide.BUY, BigDecimal.ZERO, 10));
    }
}
