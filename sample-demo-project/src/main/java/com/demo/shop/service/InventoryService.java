package com.demo.shop.service;

import java.util.HashMap;
import java.util.Map;

public class InventoryService {

    private final Map<String, Integer> stock = new HashMap<>();

    public boolean reserve(String productId, int quantity) {
        int current = stock.getOrDefault(productId, 100);
        if (current < quantity) return false;
        stock.put(productId, current - quantity);
        return true;
    }

    public void release(String productId, int quantity) {
        stock.merge(productId, quantity, Integer::sum);
    }
}
