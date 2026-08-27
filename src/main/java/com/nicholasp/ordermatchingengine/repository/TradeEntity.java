package com.nicholasp.ordermatchingengine.repository;

import jakarta.persistence.*;
import java.util.*;
import java.math.*;
import java.time.*;

@Entity
@Table(name = "trades")
public class TradeEntity {
@Id
    @GeneratedValue
    private UUID id;

    // Simply stores the raw ID value in the database
    @Column(name = "buyOrderID", nullable = false)
    private UUID buyOrderID;

    @Column(name = "sellOrderID", nullable = false)
    private UUID sellOrderID;
    
    private BigDecimal price;
    private long quantity;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    //no args contstrutor 
    public TradeEntity() {}

    //all args constructor
    public TradeEntity(UUID buyOrderID, UUID sellOrderID, BigDecimal price, long quantity, Instant timestamp)
    {
         if(buyOrderID == null)
            throw new IllegalArgumentException("Buy order ID is required");

        if(sellOrderID == null)
            throw new IllegalArgumentException("Sell order ID is required");

        if(price == null || price.signum() <= 0)
            throw new IllegalArgumentException("Price must be greater than zero");

        if(quantity <= 0)
            throw new IllegalArgumentException("Trade quantity must be greater than zero");

        this.buyOrderID = buyOrderID;
        this.sellOrderID = sellOrderID;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = Instant.now();
    }

    //BUY ID
    public UUID getBuyOrderId()
    {
        return buyOrderID;
    }
    public void setBuyOrderId(UUID buyOrderID)
    {
        this.buyOrderID = buyOrderID;
    }

    //SELL ID
    public UUID getSellOrderId()
    {
        return sellOrderID;
    }
    public void setSellOrderId(UUID sellOrderID)
    {
        this.sellOrderID = sellOrderID;
    }

    //PRICE
    public BigDecimal getPrice()
    {
        return price;
    }
    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    //QUANTITY
    public long getQuantity()
    {
        return quantity;
    }
    public void setQuantity(long quantity)
    {
        this.quantity = quantity;
    }

    //TIMESTAMPS
    public Instant getTimestamp()
    {
        return timestamp;
    }
    public void setTimestamp(Instant timestamp)
    {
        this.timestamp = timestamp;
    }
}
