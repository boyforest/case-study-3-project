package com.greenhill.coop.service;

import com.greenhill.coop.entity.Customer;
import com.greenhill.coop.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class CustomerService {

    @Resource
    private CustomerMapper customerMapper;

    public List<Customer> findAll() {
        return customerMapper.selectAll();
    }

    public Customer findById(Long id) {
        return customerMapper.selectById(id);
    }

    public int add(Customer customer) {
        return customerMapper.insert(customer);
    }

    public int update(Customer customer) {
        return customerMapper.update(customer);
    }

    public int delete(Long id) {
        return customerMapper.deleteById(id);
    }
}
