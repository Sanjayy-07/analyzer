package com.demo.shop.service;

public class NotificationService {

    private OrderService orderService; // closes the cycle: Order -> Payment -> Notification -> Order

    public NotificationService() {
        this.orderService = new OrderService();
    }

    public void sendPaymentConfirmation(String customerId, String productId, int quantity) {
        System.out.println("Notifying " + customerId + " payment confirmed for " + productId);
        orderService.handleNotificationCallback("ORD-UNKNOWN", "CONFIRMED");
    }

    public void sendRefundNotice(String orderId) {
        System.out.println("Notifying refund for order " + orderId);
        orderService.handleNotificationCallback(orderId, "REFUNDED");
    }
}
