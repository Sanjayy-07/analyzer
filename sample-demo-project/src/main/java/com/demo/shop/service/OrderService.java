package com.demo.shop.service;

/**
 * DELIBERATE ARCHITECTURAL PROBLEM (for demonstration):
 * OrderService -> PaymentService -> NotificationService -> OrderService
 * forms a circular dependency. It also directly depends on three other
 * services (high fan-out), which the analyzer should flag together with
 * the cycle.
 */
public class OrderService {

    private PaymentService paymentService;
    private InventoryService inventoryService;
    private UserService userService;

    public OrderService() {
        this.paymentService = new PaymentService();
        this.inventoryService = new InventoryService();
        this.userService = new UserService();
    }

    public String createOrder(String customerId, String productId, int quantity) {
        if (!userService.exists(customerId)) {
            throw new IllegalStateException("Unknown customer: " + customerId);
        }
        if (!inventoryService.reserve(productId, quantity)) {
            throw new IllegalStateException("Insufficient stock for: " + productId);
        }
        String orderId = "ORD-" + System.nanoTime();
        boolean paid = paymentService.charge(customerId, productId, quantity);
        if (!paid) {
            inventoryService.release(productId, quantity);
            throw new IllegalStateException("Payment failed for order: " + orderId);
        }
        return orderId;
    }

    public String cancelOrder(String orderId) {
        paymentService.refund(orderId);
        return "CANCELLED:" + orderId;
    }

    public boolean handleNotificationCallback(String orderId, String status) {
        // Called back from NotificationService, completing the cycle.
        return "DELIVERED".equals(status);
    }
}
