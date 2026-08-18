package com.nicholasp.ordermatchingengine.engine;

import com.nicholasp.ordermatchingengine.model.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.filter.OrderedRequestContextFilter;

import java.math.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    @Test
    void fullyMatchesAgainstRestingOrderAtSamePrice() {
        OrderBook book = new OrderBook(); //create new order book
        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"), 50); //create new order for buy
        Order sell = new Order(OrderSide.SELL, new BigDecimal("100"), 50); //create new order for sell
        List<Trade> restingResult = book.submitOrder(sell); //create a list of Trade with the resting order (sell)
        List<Trade> matchResult = book.submitOrder(buy); //create a list of Trade with the matchResult (buy)

        assertTrue(restingResult.isEmpty()); //assert the resting result is empty
        assertEquals(1, matchResult.size()); //assert that the match result is size 1

        Trade trade = matchResult.get(0); //get the first element of match result and store it as a Trade
        assertEquals(50, trade.getQuantity()); //asserting quantity is 50
        assertEquals(0, trade.getPrice().compareTo(new BigDecimal("100"))); //assert the trade's price is equal to the value we created ("100")
        assertEquals(buy.getId(), trade.getBuyOrderId()); //assert the trade's id is the same as our stored variable
        assertEquals(sell.getId(), trade.getSellOrderId());//assert the trade's id is the same as our stored variable
    }

    @Test
    void buySidePartiallyFilled()
    {
        OrderBook book = new OrderBook();
        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"), 150);
        Order sell = new Order(OrderSide.SELL, new BigDecimal("100"), 50);

        List<Trade> restingResult = book.submitOrder(buy); //size should be equal to 0 aka empty
        List<Trade> matchingResult = book.submitOrder(sell); //size should equal 1 bc we have a match

        assertTrue(restingResult.isEmpty());
        assertEquals(1, matchingResult.size());

        Trade trade = matchingResult.get(0);//
        assertEquals(100, buy.getRemainingQuantity());
    }

    @Test
    void NoMatch()
    {
        OrderBook book = new OrderBook();
        Order buy = new Order(OrderSide.BUY, new BigDecimal("10"), 10);
        Order sell = new Order(OrderSide.SELL, new BigDecimal("100"), 100);

        List<Trade> resting = book.submitOrder(sell);
        List<Trade> matching = book.submitOrder(buy);

        assertEquals(0, matching.size()); //no trades => 0
        assertEquals(0, resting.size()); //^

        assertEquals(10, buy.getRemainingQuantity());
        assertEquals(100, sell.getRemainingQuantity());

        assertEquals(0, buy.getPrice().compareTo(new BigDecimal("10")));
        assertEquals(0, sell.getPrice().compareTo(new BigDecimal("100")));
    }

    @Test
    void sellSidePartiallyFilled()
    {
        OrderBook book = new OrderBook();
        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"), 50);
        Order sell = new Order(OrderSide.SELL, new BigDecimal("100"), 150);

        List<Trade> resting = book.submitOrder(buy);
        List<Trade> matching = book.submitOrder(sell);

        assertTrue(resting.isEmpty());

        assertEquals(1, matching.size());
        assertEquals(100, sell.getRemainingQuantity());
    }

    @Test
    void PriceImprovement()
    {
        OrderBook book = new OrderBook();

        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"),50 );
        Order sell = new Order(OrderSide.SELL, new BigDecimal("95"), 50);

        List<Trade> resting = book.submitOrder(sell);
        List<Trade> matching = book.submitOrder(buy);

        assertTrue(resting.isEmpty());

        assertEquals(1, matching.size());


        /**
         * assert 0 (equal) that the trade executed in the OrderBook at price ==  95
         * buy order came in willing to pay up to 100
         * sell resting order said we can charge 95 which is still within the buyer's constraints
         * execute trade at 95 instead of 100 => price improvement
         * 
         */
        Trade trade = matching.get(0);
        assertEquals(0, new BigDecimal("95").compareTo(trade.getPrice())); 

    }


    @Test
    void MatchAcrossDifferentPriceLevels()
    {
        OrderBook book = new OrderBook();
        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"), 100);
        Order sell1 = new Order(OrderSide.SELL, new BigDecimal("95"), 50);
        Order sell2 = new Order(OrderSide.SELL, new BigDecimal("99"), 50);

        List<Trade> resting = book.submitOrder(sell2);
        List<Trade> resting2 = book.submitOrder(sell1);
        List<Trade> matching = book.submitOrder(buy);

        assertTrue(resting.isEmpty());
        assertTrue(resting2.isEmpty());
        assertEquals(2,matching.size());

        Trade trade1 = matching.get(0);
        Trade trade2 = matching.get(1);

        assertEquals(0, new BigDecimal("95").compareTo(trade1.getPrice()));
        assertEquals(0, new BigDecimal("99").compareTo(trade2.getPrice()));

    }

    @Test
    void FIFOatSamePriceLevel()
    {
        OrderBook book = new OrderBook();
        Order buy = new Order(OrderSide.BUY,new BigDecimal("100"), 100);
        Order sell1 = new Order(OrderSide.SELL,new BigDecimal("100"), 50);
        Order sell2 = new Order(OrderSide.SELL,new BigDecimal("100"), 50);

        List<Trade> resting1 = book.submitOrder(sell1);
        List<Trade> resting2 = book.submitOrder(sell2);
        List<Trade> matching = book.submitOrder(buy); //populated with 2 successful trades
        UUID trade1_ID = matching.get(0).getSellOrderId();
        UUID trade2_ID = matching.get(1).getSellOrderId();

        assertEquals(0,resting1.size());
        assertEquals(0,resting2.size());
        assertEquals(2, matching.size());
        assertEquals(trade1_ID, sell1.getId());
        assertEquals(trade2_ID, sell2.getId());
    }


    @Test
    void ZeroPriceException()
    {  
        assertThrows(IllegalArgumentException.class,() -> new Order(OrderSide.BUY, BigDecimal.ZERO, 10));
    }

    @Test 
    void NegativeQuantity()
    {
        assertThrows(IllegalArgumentException.class,() -> new Order(OrderSide.BUY, new BigDecimal("-100"), 10));
    }

     @Test 
    void ZeroQuantity()
    {
        assertThrows(IllegalArgumentException.class,() -> new Order(OrderSide.BUY, new BigDecimal("100"), 0));
    }

}
