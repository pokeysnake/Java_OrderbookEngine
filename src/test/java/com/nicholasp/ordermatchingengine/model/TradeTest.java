package com.nicholasp.ordermatchingengine.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    @Test
    void nullBuyOrderIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(null, UUID.randomUUID(), new BigDecimal("100"), 10));
    }

    @Test
    void nullSellOrderIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), null, new BigDecimal("100"), 10));
    }

    @Test
    void nullPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), null, 10));
    }

    @Test
    void zeroPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, 10));
    }

    @Test
    void negativePriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-100"), 10));
    }

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), 0));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), -5));
    }

}
