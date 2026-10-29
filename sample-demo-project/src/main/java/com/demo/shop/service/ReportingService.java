package com.demo.shop.service;

/**
 * DELIBERATE ARCHITECTURAL PROBLEM (for demonstration): a "God Component" —
 * far too many responsibilities/methods crammed into one class, and it
 * reaches into every other service directly (high fan-out on top of being
 * oversized). The analyzer should flag this as GOD_COMPONENT and
 * EXCESSIVE_FAN_OUT.
 */
public class ReportingService {

    private OrderService orderService = new OrderService();
    private PaymentService paymentService = new PaymentService();
    private InventoryService inventoryService = new InventoryService();
    private UserService userService = new UserService();
    private NotificationService notificationService = new NotificationService();

    public String generateDailySalesReport() { return buildReport("daily-sales"); }
    public String generateWeeklySalesReport() { return buildReport("weekly-sales"); }
    public String generateMonthlySalesReport() { return buildReport("monthly-sales"); }
    public String generateQuarterlySalesReport() { return buildReport("quarterly-sales"); }
    public String generateAnnualSalesReport() { return buildReport("annual-sales"); }
    public String generateInventoryReport() { return buildReport("inventory"); }
    public String generateLowStockReport() { return buildReport("low-stock"); }
    public String generateCustomerActivityReport() { return buildReport("customer-activity"); }
    public String generateCustomerChurnReport() { return buildReport("customer-churn"); }
    public String generateRefundReport() { return buildReport("refunds"); }
    public String generatePaymentFailureReport() { return buildReport("payment-failures"); }
    public String generateNotificationDeliveryReport() { return buildReport("notification-delivery"); }
    public String generateTopProductsReport() { return buildReport("top-products"); }
    public String generateSlowMovingProductsReport() { return buildReport("slow-moving-products"); }
    public String generateRevenueByRegionReport() { return buildReport("revenue-by-region"); }
    public String generateRevenueByChannelReport() { return buildReport("revenue-by-channel"); }
    public String generateOrderCancellationReport() { return buildReport("order-cancellations"); }
    public String generateAverageOrderValueReport() { return buildReport("avg-order-value"); }
    public String generateCustomerLifetimeValueReport() { return buildReport("customer-ltv"); }
    public String generateFraudFlagReport() { return buildReport("fraud-flags"); }
    public String generateTaxSummaryReport() { return buildReport("tax-summary"); }
    public String generateShippingDelayReport() { return buildReport("shipping-delays"); }
    public String generateSupplierPerformanceReport() { return buildReport("supplier-performance"); }
    public String generateEmployeePerformanceReport() { return buildReport("employee-performance"); }
    public String generateExecutiveSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateDailySalesReport()).append("\n");
        sb.append(generateInventoryReport()).append("\n");
        sb.append(generateCustomerActivityReport()).append("\n");
        sb.append(generateRefundReport()).append("\n");
        sb.append(generateTopProductsReport()).append("\n");
        return sb.toString();
    }

    private String buildReport(String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("Report: ").append(type).append("\n");
        if (type.contains("sales")) {
            sb.append("Orders processed via OrderService and PaymentService\n");
        } else if (type.contains("inventory") || type.contains("stock")) {
            sb.append("Stock levels via InventoryService\n");
        } else if (type.contains("customer")) {
            sb.append("Customer data via UserService\n");
        } else if (type.contains("notification")) {
            sb.append("Delivery status via NotificationService\n");
        } else {
            sb.append("General aggregate data\n");
        }
        for (int i = 0; i < 5; i++) {
            sb.append("  line ").append(i).append(" of ").append(type).append("\n");
        }
        return sb.toString();
    }
}
