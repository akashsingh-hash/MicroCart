package com.akash.order_service.controller;

import com.akash.order_service.dto.OrderRequest;
import com.akash.order_service.model.Order;
import com.akash.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CompletableFuture<String>> placeOrder(@RequestBody OrderRequest orderRequest) throws IllegalAccessException {
        CompletableFuture<String> message = orderService.placeOrder(orderRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> getAllOrders(){
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.status(HttpStatus.OK).body(orders);
    }
}
