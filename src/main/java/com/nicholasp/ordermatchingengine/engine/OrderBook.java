package com.nicholasp.ordermatchingengine.engine;

import com.nicholasp.ordermatchingengine.model.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A single-instrument limit order book that matches incoming orders against
 * resting orders using price-time priority.
 * <p>
 * Buy and sell orders are each kept in a {@link NavigableMap} from price to a
 * {@link Level}: a FIFO {@link Deque} of orders resting at that price plus a
 * running total of their remaining quantity. Buy prices are sorted
 * highest-first and sell prices lowest-first, so the best available price on
 * either side is always the map's first entry. A match exists when the buy
 * price is greater than or equal to the sell price; the trade executes at
 * the resting order's price, since that price was already committed to the
 * book before the incoming order arrived.
 * <p>
 * Performance changes (2026-11-24):
 * <ul>
 *   <li>Each level keeps {@code totalQuantity} up to date on add, fill and
 *   cancel, so depth is O(levels) instead of walking every resting order.</li>
 *   <li>{@link #getBookDepth(int)} returns only the top N levels per side.</li>
 *   <li>A {@code UUID -> Order} index lets cancel jump straight to the order's
 *   price level instead of scanning both sides under the write lock.</li>
 * </ul>
 */
public class OrderBook {

    /**
     * One row of the book's depth view: a price and the total quantity
     * resting at that price, with no detail about which orders make it up.
     */
    public record PriceLevel(BigDecimal price, long totalQuantity) {
    }

    /**
     * A snapshot of the whole book's depth, both sides at once.
     */
    public record BookDepth(List<PriceLevel> buyLevels, List<PriceLevel> sellLevels) {
    }

    /**
     * All orders resting at one price, oldest first, plus the sum of their
     * remaining quantities (kept up to date so depth never iterates orders).
     */
    private static final class Level {
        final Deque<Order> orders = new ArrayDeque<>();
        long totalQuantity;
    }

    // PREVIOUS: private final NavigableMap<BigDecimal, Deque<Order>> buyOrders;
    // PREVIOUS: private final NavigableMap<BigDecimal, Deque<Order>> sellOrders;
    private final NavigableMap<BigDecimal, Level> buyOrders;
    private final NavigableMap<BigDecimal, Level> sellOrders;

    // NEW: every resting order by id, so cancel doesn't have to search the book.
    // Updated on rest, full fill and cancel.
    private final Map<UUID, Order> restingById = new HashMap<>();

    // Multiple readers (getBookDepth) may run concurrently; submitOrder/cancelOrder
    // need exclusive access since they mutate the same maps/deques.
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Assigns each Trade a position in true match order. Only ever touched
    // while holding the write lock, so its ordering is as reliable as the
    // lock itself.
    private long sequenceNum = 0;

    public OrderBook() {
        this.buyOrders = new TreeMap<>(Comparator.reverseOrder());
        this.sellOrders = new TreeMap<>();
    }

    /**
     * Returns the book (buy or sell side) corresponding to the given side.
     */
    private NavigableMap<BigDecimal, Level> getBookFor(OrderSide side) {
        if (side == OrderSide.BUY) {
            return buyOrders;
        } else {
            return sellOrders;
        }
    }

    /**
     * Determines whether the incoming order's price is compatible with the
     * best resting price on the opposite side of the book.
     */
    private boolean isMatch(Order incomingOrder, BigDecimal oppositePrice) {
        if (incomingOrder.getSide() == OrderSide.BUY) {
            return incomingOrder.getPrice().compareTo(oppositePrice) >= 0;
        } else {
            return incomingOrder.getPrice().compareTo(oppositePrice) <= 0;
        }
    }

    /**
     * Adds an order to the back of its price level's queue, creating the
     * level first if this is the first order at that price.
     */
    private void addToBook(NavigableMap<BigDecimal, Level> book, Order order) {
        Level level = book.computeIfAbsent(order.getPrice(), price -> new Level());
        level.orders.addLast(order);
        level.totalQuantity += order.getRemainingQuantity();
        restingById.put(order.getId(), order);
    }

    /* PREVIOUS:
    private Deque<Order> getFirstOrderDeque(NavigableMap<BigDecimal, Deque<Order>> book) {
        return book.firstEntry().getValue();
    }

    private void addToBook(NavigableMap<BigDecimal, Deque<Order>> book, Order order) {
        book.computeIfAbsent(order.getPrice(), price -> new ArrayDeque<>()).addLast(order);
    }
    */

    /**
     * Submits an order to the book, matching it against resting orders on
     * the opposite side while a valid price match exists and quantity
     * remains. Any unfilled quantity is left resting on the order's own
     * side of the book.
     *
     * @param incomingOrder the order to submit
     * @return the trades generated by this submission, in execution order
     */
    public List<Trade> submitOrder(Order incomingOrder) {
        lock.writeLock().lock();
        try {
            List<Trade> trades = new ArrayList<>();

            OrderSide own = incomingOrder.getSide();
            NavigableMap<BigDecimal, Level> oppositeBook = getBookFor(own.opposite());

            // Keep matching against the opposite side's best price level as long as:
            // the incoming order still has quantity left, there's something to match
            // against, and the best opposite price is still compatible with our price.
            while (incomingOrder.getRemainingQuantity() > 0
                    && !oppositeBook.isEmpty()
                    && isMatch(incomingOrder, oppositeBook.firstKey())) {
                Level bestLevel = oppositeBook.firstEntry().getValue();
                // Oldest order at the opposite side's best price level (price-time priority).
                Order restingOrder = bestLevel.orders.getFirst();

                // Fill as much as both orders can support; whichever runs out first caps the
                // trade.
                long fillQty = Math.min(incomingOrder.getRemainingQuantity(), restingOrder.getRemainingQuantity());
                incomingOrder.fill(fillQty);
                restingOrder.fill(fillQty);
                bestLevel.totalQuantity -= fillQty;

                // Route each order's ID to the correct slot in Trade's constructor,
                // since the incoming order isn't always the buyer.
                UUID buySideID;
                UUID sellSideID;
                if (own == OrderSide.BUY) {
                    buySideID = incomingOrder.getId();
                    sellSideID = restingOrder.getId();
                } else {
                    buySideID = restingOrder.getId();
                    sellSideID = incomingOrder.getId();
                }

                // Trade executes at the resting order's price - it was already committed
                // to the book, so the incoming (taker) order gets that price, not its own.
                sequenceNum++;
                Trade trade = new Trade(buySideID, sellSideID, restingOrder.getPrice(), fillQty, sequenceNum);
                trades.add(trade);

                // Resting order fully consumed - pop it off the front of its queue.
                if (restingOrder.getRemainingQuantity() == 0) {
                    bestLevel.orders.pollFirst();
                    restingById.remove(restingOrder.getId());
                }

                // That was the last order at this price level - drop the level from the map,
                // otherwise firstEntry() on the next loop would return an empty queue.
                if (bestLevel.orders.isEmpty()) {
                    oppositeBook.pollFirstEntry();
                }
            }

            // Anything left unfilled (no match, or ran out of opposite orders) rests
            // on the incoming order's own side, waiting for a future counter-order.
            if (incomingOrder.getRemainingQuantity() > 0) {
                addToBook(getBookFor(own), incomingOrder);
            }

            return trades;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /* PREVIOUS submitOrder (same matching logic, without level totals or the id index):

    public List<Trade> submitOrder(Order incomingOrder) {
        lock.writeLock().lock();
        try {
            List<Trade> trades = new ArrayList<>();

            OrderSide own = incomingOrder.getSide();
            OrderSide opposite = own.opposite();

            while (incomingOrder.getRemainingQuantity() > 0
                    && !getBookFor(opposite).isEmpty()
                    && isMatch(incomingOrder, getBookFor(opposite).firstEntry().getKey())) {
                Order restingOrder = getFirstOrderDeque(getBookFor(opposite)).getFirst();

                long fillQty = Math.min(incomingOrder.getRemainingQuantity(), restingOrder.getRemainingQuantity());
                incomingOrder.fill(fillQty);
                restingOrder.fill(fillQty);

                UUID buySideID;
                UUID sellSideID;
                if (own == OrderSide.BUY) {
                    buySideID = incomingOrder.getId();
                    sellSideID = restingOrder.getId();
                } else {
                    buySideID = restingOrder.getId();
                    sellSideID = incomingOrder.getId();
                }

                sequenceNum++;
                Trade trade = new Trade(buySideID, sellSideID, restingOrder.getPrice(), fillQty, sequenceNum);
                trades.add(trade);

                if (restingOrder.getRemainingQuantity() == 0) {
                    getFirstOrderDeque(getBookFor(opposite)).pollFirst();
                }

                if (getFirstOrderDeque(getBookFor(opposite)).isEmpty()) {
                    getBookFor(opposite).pollFirstEntry();
                }
            }

            if (incomingOrder.getRemainingQuantity() > 0) {
                addToBook(getBookFor(own), incomingOrder);
            }

            return trades;
        } finally {
            lock.writeLock().unlock();
        }
    }
    */

    /**
     * Cancels a resting order. Looks the order up by id (O(1)), goes straight
     * to its price level (O(log levels)) and removes it from that level's
     * queue (O(orders at that price)).
     */
    public Optional<Order> cancelOrder(UUID cancelID) {
        lock.writeLock().lock();
        try {
            Order order = restingById.remove(cancelID);
            if (order == null) {
                return Optional.empty(); // never rested, already filled, or already cancelled
            }

            NavigableMap<BigDecimal, Level> book = getBookFor(order.getSide());
            Level level = book.get(order.getPrice());
            level.orders.remove(order); // Order doesn't override equals, so this matches by identity
            level.totalQuantity -= order.getRemainingQuantity();
            if (level.orders.isEmpty()) {
                book.remove(order.getPrice());
            }
            return Optional.of(order);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /* PREVIOUS cancelOrder: linear scan over every level and every order on both
       sides while holding the write lock.

    public Optional<Order> cancelOrder(UUID cancelID) {
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<BigDecimal, Deque<Order>>> buyLevels = buyOrders.entrySet().iterator();
            Iterator<Map.Entry<BigDecimal, Deque<Order>>> sellLevels = sellOrders.entrySet().iterator();
            while (buyLevels.hasNext())// while the buyOrders has values (price levels) to iterator through
            {
                Map.Entry<BigDecimal, Deque<Order>> level = buyLevels.next(); // we get the level that we are traversing
                Iterator<Order> orders = level.getValue().iterator(); // iterator for orders
                while (orders.hasNext()) // while we have elements/orders to traverse
                {
                    Order order = orders.next(); // save the current one to a var to check parameter values
                    if (order.getId().equals(cancelID)) // if the current order's id is the same as the id that we want
                                                        // to cancel
                    {
                        orders.remove(); // we can safely remove that order
                        if (level.getValue().isEmpty()) // if the level that we are traversing no longer has elements in
                                                        // the deque of orders
                        {
                            buyLevels.remove(); // remove that level from the orderbook's respective side (in this case
                                                // buyorders)
                        }
                        return Optional.of(order); // return an Optional wrapped order
                    }
                }
            }

            while (sellLevels.hasNext()) {
                Map.Entry<BigDecimal, Deque<Order>> level = sellLevels.next();
                Iterator<Order> orders = level.getValue().iterator();
                while (orders.hasNext()) {
                    Order order = orders.next();
                    if (order.getId().equals(cancelID)) {
                        orders.remove();
                        if (level.getValue().isEmpty()) {
                            sellLevels.remove();
                        }
                        return Optional.of(order);
                    }
                }
            }

            return Optional.empty(); // return an empty Optional instance directly and handles the not found case

        } finally {
            lock.writeLock().unlock();
        }

    }
    */

    /**
     * Full depth of both sides (every price level). O(levels): uses each
     * level's running total instead of summing its orders.
     */
    public BookDepth getBookDepth() {
        return getBookDepth(Integer.MAX_VALUE);
    }

    /**
     * Depth of the best {@code maxLevels} price levels on each side.
     */
    public BookDepth getBookDepth(int maxLevels) {
        lock.readLock().lock();
        try {
            return new BookDepth(topLevels(buyOrders, maxLevels), topLevels(sellOrders, maxLevels));
        } finally {
            lock.readLock().unlock();
        }
    }

    private static List<PriceLevel> topLevels(NavigableMap<BigDecimal, Level> book, int maxLevels) {
        List<PriceLevel> levels = new ArrayList<>(Math.min(book.size(), maxLevels));
        for (Map.Entry<BigDecimal, Level> entry : book.entrySet()) {
            if (levels.size() == maxLevels) {
                break;
            }
            levels.add(new PriceLevel(entry.getKey(), entry.getValue().totalQuantity));
        }
        return levels;
    }

    /* PREVIOUS getBookDepth: walked every resting order on both sides to sum
       each level's quantity.

    public BookDepth getBookDepth() {
        lock.readLock().lock();
        try{
            List<PriceLevel> buyLevels = new ArrayList<>();
            List<PriceLevel> sellLevels = new ArrayList<>();
            Iterator<Map.Entry<BigDecimal, Deque<Order>>> buy = buyOrders.entrySet().iterator();
            Iterator<Map.Entry<BigDecimal, Deque<Order>>> sell = sellOrders.entrySet().iterator();

        while (buy.hasNext()) {
            Map.Entry<BigDecimal, Deque<Order>> level = buy.next();
            Iterator<Order> orders = level.getValue().iterator();
            long buyQuantity = 0;

            while (orders.hasNext()) {
                Order order = orders.next();
                buyQuantity += order.getRemainingQuantity();
            }
            buyLevels.add(new PriceLevel(level.getKey(), buyQuantity));
        }

        while (sell.hasNext()) {
            Map.Entry<BigDecimal, Deque<Order>> level = sell.next();
            Iterator<Order> orders = level.getValue().iterator();
            long sellQuantity = 0;
            while (orders.hasNext()) {
                Order order = orders.next();
                sellQuantity += order.getRemainingQuantity();
            }
            sellLevels.add(new PriceLevel(level.getKey(), sellQuantity));
        }

        return new BookDepth(buyLevels, sellLevels);

        } finally {
            lock.readLock().unlock();
        }


    }
    */
}
