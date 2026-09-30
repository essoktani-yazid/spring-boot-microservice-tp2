package net.essoktani.billingservice.model;

import lombok.*;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class Customer {
    private String id;
    private String name;
    private String email;
}
