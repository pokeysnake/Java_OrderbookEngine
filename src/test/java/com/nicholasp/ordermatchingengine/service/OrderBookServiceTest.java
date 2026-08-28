package com.nicholasp.ordermatchingengine.service;

import com.nicholasp.ordermatchingengine.model.*;
import com.nicholasp.ordermatchingengine.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OrderBookServiceTest {

    @Test
    void placeOrderTest()
    {
        OrderBookService service = new OrderBookService(mock(TradeRepository.class), mock(SimpMessagingTemplate.class));
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
        assertThrows(IllegalArgumentException.class,() -> new OrderBookService(mock(TradeRepository.class), mock(SimpMessagingTemplate.class)).placeOrder(OrderSide.BUY, BigDecimal.ZERO, 10));
    }
}
