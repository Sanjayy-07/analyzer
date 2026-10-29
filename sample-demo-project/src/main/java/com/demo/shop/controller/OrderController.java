package com.demo.shop.controller;

import com.demo.shop.service.OrderService;

public class OrderController {

    private OrderService orderService;

    public OrderController() {
        this.orderService = new OrderService();
    }

    public String placeOrder(String customerId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return orderService.createOrder(customerId, productId, quantity);
    }

    public String cancelOrder(String orderId) {
        return orderService.cancelOrder(orderId);
    }
}
