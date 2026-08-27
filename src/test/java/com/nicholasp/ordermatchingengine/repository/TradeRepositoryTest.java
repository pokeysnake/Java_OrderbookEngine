package com.nicholasp.ordermatchingengine.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradeRepositoryTest {

    @Autowired
    private TradeRepository tradeRepository;

    @Test
    void savedTradeCanBeFoundAgain() {
        TradeEntity entity = new TradeEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100"),
            10,
            Instant.now()
        );

        tradeRepository.save(entity);

        List<TradeEntity> allTrades = tradeRepository.findAll();

        assertEquals(1, allTrades.size());
        assertEquals(new BigDecimal("100"), allTrades.get(0).getPrice());
    }
}
