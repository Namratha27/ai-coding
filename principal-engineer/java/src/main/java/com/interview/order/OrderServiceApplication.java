package com.interview.order;

import com.interview.order.model.InventoryItem;
import com.interview.order.model.Order;
import com.interview.order.simulated.SimulatedCosmosContainer;
import com.interview.order.simulated.SimulatedQueue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean("ordersContainer")
    public SimulatedCosmosContainer<Order> ordersContainer() {
        return new SimulatedCosmosContainer<>();
    }

    @Bean("inventoryContainer")
    public SimulatedCosmosContainer<InventoryItem> inventoryContainer() {
        return new SimulatedCosmosContainer<>();
    }

    @Bean
    public SimulatedQueue orderEventsQueue() {
        return new SimulatedQueue("order-events");
    }
}
