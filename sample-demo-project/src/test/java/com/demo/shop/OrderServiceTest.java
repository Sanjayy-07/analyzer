package com.demo.shop;

import com.demo.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OrderServiceTest {

    @Test
    public void createOrder_knownCustomer_returnsOrderId() {
        OrderService service = new OrderService();
        String orderId = service.createOrder("cust-1", "prod-1", 1);
        assertNotNull(orderId);
        assertTrue(orderId.startsWith("ORD-"));
    }

    @Test
    public void createOrder_unknownCustomer_throws() {
        OrderService service = new OrderService();
        assertThrows(IllegalStateException.class, () -> service.createOrder("nope", "prod-1", 1));
    }
}
