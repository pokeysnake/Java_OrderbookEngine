package com.nicholasp.ordermatchingengine.service;

import com.nicholasp.ordermatchingengine.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookServiceTest {

    @Test
    void placeOrderTest()
    {
        OrderBookService service = new OrderBookService();
        List<Trade> resting = service.placeOrder(OrderSide.SELL, new BigDecimal("100"), 50);
        List<Trade> matching = service.placeOrder(OrderSide.BUY,new BigDecimal("100"), 50);

        assertEquals(1, matching.size());
        assertTrue(resting.isEmpty());

    }
    

    @Test
    void invalidInputThrows()
    {
        assertThrows(IllegalArgumentException.class,() -> new OrderBookService().placeOrder(OrderSide.BUY, BigDecimal.ZERO, 10));
    }
}
