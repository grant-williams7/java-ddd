package com.example.marketplace.contract;

final class Environment {

    private Environment() {
    }

    static String get(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
