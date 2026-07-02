package com.interview.catalog.model;

public record CreateProductRequest(String name, String category, double price, int stock) {
}