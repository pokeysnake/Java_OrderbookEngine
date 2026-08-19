package com.nicholasp.ordermatchingengine.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void nullSideThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(null, new BigDecimal("100"), 10));
    }

    @Test
    void zeroPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(OrderSide.BUY, BigDecimal.ZERO, 10));
    }

    @Test
    void negativePriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(OrderSide.BUY, new BigDecimal("-100"), 10));
    }

    @Test
    void nullPriceThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(OrderSide.BUY, null, 10));
    }

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(OrderSide.BUY, new BigDecimal("100"), 0));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Order(OrderSide.BUY, new BigDecimal("100"), -5));
    }

}
