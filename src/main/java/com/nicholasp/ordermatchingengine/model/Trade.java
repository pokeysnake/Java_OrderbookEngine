package com.nicholasp.ordermatchingengine.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;




public class Trade {
    private final UUID buyOrderID;
    private final UUID sellOrderID;
    private final BigDecimal price;
    private final long quantity;
    private final Instant timestamp;

    public Trade(UUID buyOrderID, UUID sellOrderID, BigDecimal price, long quantity)
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

    public UUID getBuyOrderId() {
        return buyOrderID;
    }

    public UUID getSellOrderId() {
        return sellOrderID;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
