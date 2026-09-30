package net.essoktani.billingservice.entities;

import jakarta.persistence.*;
import lombok.*;
import net.essoktani.billingservice.model.Customer;

import java.util.Date;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bill {
    @Id @GeneratedValue
    private Long id;
    private Date billingDate;
    private Long customerId;
    @OneToMany(mappedBy="bill")
    private List<ProductItem> productsItems;
    @Transient
    private Customer customer;
}
