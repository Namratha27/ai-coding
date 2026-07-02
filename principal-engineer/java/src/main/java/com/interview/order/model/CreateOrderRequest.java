package com.interview.order.model;

import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {
    private List<OrderLine> lines = new ArrayList<>();

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(List<OrderLine> lines) {
        this.lines = lines;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
    }
}
