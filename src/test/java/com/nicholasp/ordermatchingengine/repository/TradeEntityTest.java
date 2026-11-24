package com.nicholasp.ordermatchingengine.repository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeEntityTest {

    @Test
    void storesTheTradeTimestampNotConstructionTime()
    {
        Instant executedAt = Instant.parse("2026-01-01T12:00:00Z");
        TradeEntity entity = new TradeEntity(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100"), 10, executedAt, 1);

        assertEquals(executedAt, entity.getTimestamp());
    }

    @Test
    void nullTimestampThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> new TradeEntity(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100"), 10, null, 1));
    }
}
