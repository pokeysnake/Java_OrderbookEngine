package com.nicholasp.ordermatchingengine.engine;

import com.nicholasp.ordermatchingengine.model.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.filter.OrderedRequestContextFilter;

import java.math.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    void concurrentSubmitOrderIsThreadSafe() throws InterruptedException, ExecutionException
    {
        OrderBook book = new OrderBook();
        int orderCount = 100;

        for(int i = 0; i < orderCount; i++)
            book.submitOrder(new Order(OrderSide.SELL, new BigDecimal("100"), 1)); //create 100 orders of quantity 1

        ExecutorService executor = Executors.newFixedThreadPool(10); //creates new executor service that makes 10 reusable threads

        List<Callable<List<Trade>>> tasks = new ArrayList<>();//creates a list of "Callable" objects of type <T> which is a list of Trade. each element in this arrayList will be a trade that has been taken successfully (a match and update to orderbook via submit order)

        for(int i = 0; i < orderCount; i++)
        {
            Order buy = new Order(OrderSide.BUY, new BigDecimal("100"), 1); //create 100 orders of BUY to match to SELL
            tasks.add(() -> book.submitOrder(buy)); //lambda function that adds the result of submitOrder for each order that we want to create to the arrayList
        }

        List<Future<List<Trade>>>  futures = executor.invokeAll(tasks); //List of Future objects aka claimable tickets from tasks that is made of a List of Trade. we also invoke all tasks to the 10 threads
        executor.shutdown(); //shutdown the threads once we finish

        long totalFilled = 0;
        int totalTrades = 0;
        for(Future<List<Trade>> future : futures) //enhanced loop that takes each claimed ticket (a list of Trade) and each item in the list of Trade (future) and goes all the way thgrough all futures
        {
            List<Trade> trades = future.get(); //saves the trades made and uses future which is each list of Trade in the Future<T> list and waits until the tasks are done to show u the result 
            totalTrades+= trades.size(); //we add the size to our totalTrades counter because each BUY and SELL order should match successfully therefore optimally each iteration of this loop should add 1
            for(Trade trade : trades)
            {
                totalFilled += trade.getQuantity(); //sub forloop placing this loop at O(number of elements in futures * number of successful trades) that should optimally add 1 (each quantity that was successfully filled) each iteration
            }
        }

        assertEquals(orderCount, totalTrades); //assert that the orderCount is the same as the number of trades we made, im imagining this as 1 : 1 ratio where if we ideally have matches for every order per side we would get the totalTrades again in optimal conditions equal to the number of orders we created 
        assertEquals(orderCount, totalFilled);// assert that the orderCount is the same as the quantity filled which i imagine quantity filled ideally is QtyFilled = 0.5 * (numBuyOrders + numberSellOrders)
    }

    @Test
    void successfulCancel ()
    {
        OrderBook book = new OrderBook();

        Order cancel = new Order(OrderSide.SELL, new BigDecimal("100"),10);
        Order buy = new Order(OrderSide.BUY, new BigDecimal("100"),10);

        List<Trade> resting = book.submitOrder(cancel);
        assertEquals(Optional.of(cancel), book.cancelOrder(cancel.getId()));
        List<Trade> matching = book.submitOrder(buy);
        assertEquals(0, matching.size());

    }

    @Test
    void cancelNonexistantID()
    {
        OrderBook book = new OrderBook();
        Order order = new Order(OrderSide.SELL, new BigDecimal("100"), 10);
        List<Trade> trades = book.submitOrder(order);
        Optional<Order> cancelled = book.cancelOrder(UUID.randomUUID());
        assertEquals(0, trades.size());
        assertEquals(Optional.empty(), cancelled);

    }


    @Test
    void validBookDepth()
    {
        OrderBook book = new OrderBook();
        book.submitOrder(new Order(OrderSide.SELL, new BigDecimal("100"), 10));
        book.submitOrder(new Order(OrderSide.SELL, new BigDecimal("100"), 10));
        book.submitOrder(new Order(OrderSide.SELL, new BigDecimal("99"), 10));

        OrderBook.BookDepth depth = book.getBookDepth();
        assertEquals(2, depth.sellLevels().size());
        assertEquals(0, depth.sellLevels().get(1).price().compareTo(new BigDecimal("100")));
        assertEquals(20, depth.sellLevels().get(1).totalQuantity());

        
    }
}
