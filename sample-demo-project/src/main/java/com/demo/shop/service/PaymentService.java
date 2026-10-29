package com.demo.shop.service;

public class PaymentService {

    private NotificationService notificationService;

    public PaymentService() {
        this.notificationService = new NotificationService();
    }

    public boolean charge(String customerId, String productId, int quantity) {
        boolean success = Math.random() > 0.05; // simulated gateway call
        if (success) {
            notificationService.sendPaymentConfirmation(customerId, productId, quantity);
        }
        return success;
    }

    public void refund(String orderId) {
        notificationService.sendRefundNotice(orderId);
    }
}
