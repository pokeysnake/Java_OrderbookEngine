package com.nicholasp.ordermatchingengine.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    @Test
    void nullBuyOrderIDThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(null, UUID.randomUUID(), new BigDecimal("100"), 10,1));
    }

    @Test
    void nullSellOrderIDThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), null, new BigDecimal("100"), 10,1));
    }

    @Test
    void nullPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), null, 10,1));
    }

    @Test
    void zeroPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, 10,1));
    }

    @Test
    void negativePriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-100"), 10,1));
    }

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), 0,1));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Trade(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), -5,1));
    }

}
