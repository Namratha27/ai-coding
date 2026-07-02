package com.interview.order.web;

import com.interview.order.model.CreateOrderRequest;
import com.interview.order.model.Order;
import com.interview.order.service.OrderService;
import com.interview.order.service.ServiceResult;
import com.interview.order.simulated.SimulatedQueue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping
public class OrderController {
    private final OrderService service;
    private final SimulatedQueue queue;

    public OrderController(OrderService service, SimulatedQueue queue) {
        this.service = service;
        this.queue = queue;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        return toHttp(service.placeOrder(key, request));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        return toHttp(service.getOrder(id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String id) {
        return toHttp(service.cancelOrder(id));
    }

    @GetMapping("/inventory/{sku}")
    public ResponseEntity<?> getInventory(@PathVariable String sku) {
        return toHttp(service.getInventory(sku));
    }

    @PostMapping("/internal/drain-events")
    public ResponseEntity<?> drainEvents() {
        ServiceResult<Integer> result = service.drainAndFulfill();
        return result.getStatus() == 200
                ? ResponseEntity.ok(Map.of("processed", result.getValue()))
                : toHttp(result);
    }

    @GetMapping("/internal/events")
    public ResponseEntity<?> events() {
        return ResponseEntity.ok(queue.messages());
    }

    private static ResponseEntity<?> toHttp(ServiceResult<?> result) {
        return switch (result.getStatus()) {
            case 200 -> ResponseEntity.ok(result.getValue());
            case 201 -> {
                Object value = result.getValue();
                URI location = value instanceof Order order
                        ? ServletUriComponentsBuilder.fromCurrentContextPath().path("/orders/{id}").build(order.getId())
                        : URI.create("");
                yield ResponseEntity.created(location).body(value);
            }
            case 400 -> ResponseEntity.badRequest().body(Map.of("error", result.getError()));
            case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", result.getError()));
            case 409 -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", result.getError()));
            default -> ResponseEntity.status(result.getStatus()).build();
        };
    }
}
