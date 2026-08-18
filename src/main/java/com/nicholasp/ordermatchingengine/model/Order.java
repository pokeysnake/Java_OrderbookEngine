package com.nicholasp.ordermatchingengine.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

//declares a class named Order, Defines the info and behavior every object will have
public class Order {
    private final UUID id;              //unique ID per order
    private final OrderSide side;       //either BUY or SELL
    private final BigDecimal price;     //order price
    private final long quantity;        //how many units requested
    private long remainingQuantity;     //how many units still need to be filled
    private final Instant timestamp;    //what time the order arrived, used to find which order is first
    

    //constructor for the class
    public Order(OrderSide side, BigDecimal price, long quantity)
    {
        if(side == null)
        {
            throw new IllegalArgumentException("Order side is required");
        }

        if( price == null || price.signum() <= 0)
        {
            throw new IllegalArgumentException("Price must be greater than zero");
        }

        if(quantity <= 0)
        {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        this.id = UUID.randomUUID();
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = Instant.now();
    }

    //getter methods
    public UUID getId()
    {
        return id;
    }

    public OrderSide getSide()
    {
        return side;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public long getQuantity()
    {
        return quantity;
    }

    public long getRemainingQuantity()
    {
        return remainingQuantity;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public void fill(long filledQuantity)
    {
        if(filledQuantity <= 0){
            throw new IllegalArgumentException("Filled quantity must be positive");
        }

        if(filledQuantity > remainingQuantity) {
            throw new IllegalArgumentException("Filled quantity exceeds remaining quantity");
        }

        remainingQuantity-= filledQuantity;
    }

    /** 
     * can create a new order like this 
     * 
     * Order order = new Order(
     *      OrderSide.BUY,
     *      new BigDecimal("105.50"),
     *      100
     * );
    */

}
