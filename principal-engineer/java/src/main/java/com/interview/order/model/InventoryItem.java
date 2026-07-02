package com.interview.order.model;

import com.interview.order.simulated.CosmosItem;

public class InventoryItem implements CosmosItem {
    private String sku;
    private int available;
    private int reserved;

    public InventoryItem() {
    }

    public InventoryItem(String sku, int available, int reserved) {
        this.sku = sku;
        this.available = available;
        this.reserved = reserved;
    }

    @Override
    public String getId() {
        return sku;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }
}
