package com.interview.catalog.model;

public record UpdateProductRequest(String name, String category, double price, int stock) {
}