package com.nicholasp.ordermatchingengine.service;

import com.nicholasp.ordermatchingengine.model.*;
import com.nicholasp.ordermatchingengine.repository.TradeEntity;
import com.nicholasp.ordermatchingengine.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @SuppressWarnings("unchecked")
    void failedFlushIsRetriedWithoutLosingTrades()
    {
        TradeRepository repo = mock(TradeRepository.class);
        when(repo.saveAll(any(Iterable.class)))
            .thenThrow(new RuntimeException("database down"))
            .thenAnswer(inv -> inv.getArgument(0));
        OrderBookService service = new OrderBookService(repo, mock(SimpMessagingTemplate.class));

        service.placeOrder(OrderSide.SELL, new BigDecimal("100"), 50);
        Trade trade = service.placeOrder(OrderSide.BUY, new BigDecimal("100"), 50).trades().get(0);

        service.flushTrades(); // saveAll throws: the trade must be re-queued, not dropped
        service.flushTrades(); // retry succeeds
        service.flushTrades(); // nothing left to write

        ArgumentCaptor<Iterable<TradeEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(repo, times(2)).saveAll(saved.capture());
        List<TradeEntity> retried = new ArrayList<>();
        saved.getAllValues().get(1).forEach(retried::add);
        assertEquals(1, retried.size());
        assertEquals(trade.getSequenceNum(), retried.get(0).getSequenceNum());
        assertEquals(trade.getTimestamp(), retried.get(0).getTimestamp());
        assertNull(retried.get(0).getID(), "retry should use a fresh entity, not the rolled-back one");
    }

    @Test
    void invalidInputThrows()
    {
        assertThrows(IllegalArgumentException.class,() -> new OrderBookService(mock(TradeRepository.class), mock(SimpMessagingTemplate.class)).placeOrder(OrderSide.BUY, BigDecimal.ZERO, 10));
    }
}
