package net.essoktani.billingservice.web;

import net.essoktani.billingservice.dto.BillRequestDTO;
import net.essoktani.billingservice.entities.Bill;
import net.essoktani.billingservice.entities.ProductItem;
import net.essoktani.billingservice.feign.CustomerServiceRestClient;
import net.essoktani.billingservice.feign.InventoryServiceRestClient;
import net.essoktani.billingservice.model.Customer;
import net.essoktani.billingservice.model.Product;
import net.essoktani.billingservice.repository.BillRepository;
import net.essoktani.billingservice.repository.ProductItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping
public class BillRestController {
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private ProductItemRepository productItemRepository;
    @Autowired
    private CustomerServiceRestClient customerServiceRestClient;
    @Autowired
    private InventoryServiceRestClient inventoryServiceRestClient;
    @GetMapping("/bills/{id}")
    public Bill getBillById(@PathVariable Long id){
        Bill bill = billRepository.findById(id).get();
        Customer customer=customerServiceRestClient.findCustomerById(bill.getCustomerId());
        bill.setCustomer(customer);
        bill.getProductsItems().forEach(productItem->{
            productItem.setProduct(inventoryServiceRestClient.getProduct(productItem.getProductId()));
        });
        return bill;
    }
    @PostMapping("/bills/full")
    public Bill createBill(@RequestBody BillRequestDTO billRequestDTO) {
        Bill bill = new Bill();
        bill.setBillingDate(new Date());
        bill.setCustomerId(billRequestDTO.getCustomerId());
        bill.setProductsItems(new java.util.ArrayList<>());
        billRepository.save(bill);

        billRequestDTO.getProductItems().forEach(itemRequest -> {
            Product product = inventoryServiceRestClient.getProduct(itemRequest.getProductId());

            ProductItem productItem = new ProductItem();
            productItem.setProductId(itemRequest.getProductId());
            productItem.setQuantity(itemRequest.getQuantity());
            productItem.setPrice(product.getPrice());
            productItem.setBill(bill);
            productItemRepository.save(productItem);
        });

        return getBillById(bill.getId());
    }
}
