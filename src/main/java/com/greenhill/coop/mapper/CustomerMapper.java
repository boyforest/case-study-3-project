package com.greenhill.coop.mapper;

import com.greenhill.coop.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CustomerMapper {
    List<Customer> selectAll();
    Customer selectById(Long id);
    int insert(Customer customer);
    int update(Customer customer);
    int deleteById(Long id);
}
