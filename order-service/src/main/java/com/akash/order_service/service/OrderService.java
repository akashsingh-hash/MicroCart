package com.akash.order_service.service;

import com.akash.order_service.dto.InventoryResponse;
import com.akash.order_service.dto.OrderLineItemsDto;
import com.akash.order_service.dto.OrderRequest;
import com.akash.order_service.model.Order;
import com.akash.order_service.model.OrderLineItems;
import com.akash.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.stream;


@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final WebClient.Builder webClientBuilder;


    public void placeOrder(OrderRequest orderRequest) throws IllegalAccessException {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList().stream().map(this::mapToDto).toList();
        order.setOrderLineItemsList(orderLineItems);

        // Get alll sku Code
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory Service
        InventoryResponse[] result = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();


        Boolean allProductsInStock = Arrays.stream(result).allMatch(InventoryResponse::getIsInStock);

        if(allProductsInStock)
            orderRepository.save(order);
        else{
            throw  new IllegalAccessException("Product not in stock, please try again.");
        }
    }

    public List<Order> getAllOrders(){
        return orderRepository.findAll();
    }

    public OrderLineItems mapToDto(OrderLineItemsDto orderLineItems) {
        return OrderLineItems.builder()
                .id(orderLineItems.getId())
                .skuCode(orderLineItems.getSkuCode())
                .price(orderLineItems.getPrice())
                .quantity(orderLineItems.getQuantity())
                .build();
    }

}
