package net.essoktani.billingservice.repository;

import net.essoktani.billingservice.entities.ProductItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductItemRepository extends JpaRepository<ProductItem,Long> {
   List<ProductItem> findByBillId(Long billId);
}
