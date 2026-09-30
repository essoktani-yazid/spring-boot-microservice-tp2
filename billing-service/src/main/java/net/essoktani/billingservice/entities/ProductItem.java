package net.essoktani.billingservice.entities;

import jakarta.persistence.*;
import lombok.*;
import net.essoktani.billingservice.model.Product;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductItem {
    @Id @GeneratedValue
    private Long id;
    private Long productId;
    private int quantity;
    private double price;
    @ManyToOne
    private Bill bill;
    @Transient
    private Product product;
}
