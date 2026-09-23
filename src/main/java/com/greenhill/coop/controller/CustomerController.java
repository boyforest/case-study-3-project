package com.greenhill.coop.controller;

import com.greenhill.coop.entity.Customer;
import com.greenhill.coop.service.CustomerService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/customer")
public class CustomerController {

    @Resource
    private CustomerService customerService;

    @GetMapping
    public List<Customer> getAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public Customer getOne(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @PostMapping
    public int add(@RequestBody Customer customer) {
        return customerService.add(customer);
    }

    @PutMapping
    public int update(@RequestBody Customer customer) {
        return customerService.update(customer);
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable Long id) {
        return customerService.delete(id);
    }
}
