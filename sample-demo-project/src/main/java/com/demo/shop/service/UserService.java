package com.demo.shop.service;

import java.util.Set;

public class UserService {

    private final Set<String> knownCustomers = Set.of("cust-1", "cust-2", "cust-3");

    public boolean exists(String customerId) {
        return knownCustomers.contains(customerId);
    }
}
