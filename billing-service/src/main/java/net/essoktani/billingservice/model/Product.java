package net.essoktani.billingservice.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    private Long id;
    private String name;
    private double price;
    private int quantity;
}
