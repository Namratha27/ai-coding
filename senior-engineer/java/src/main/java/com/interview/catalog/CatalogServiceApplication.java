package com.interview.catalog;

import com.interview.catalog.model.Product;
import com.interview.catalog.simulated.SimulatedBlobContainer;
import com.interview.catalog.simulated.SimulatedCosmosContainer;
import com.interview.catalog.simulated.SimulatedQueue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    @Bean
    public SimulatedCosmosContainer<Product> productsContainer() {
        return new SimulatedCosmosContainer<>();
    }

    @Bean
    public SimulatedBlobContainer imagesContainer() {
        return new SimulatedBlobContainer();
    }

    @Bean
    public SimulatedQueue eventsQueue() {
        return new SimulatedQueue();
    }
}