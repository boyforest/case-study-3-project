package com.greenhill.coop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.greenhill.coop.mapper")
public class CoopApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoopApplication.class, args);
    }
}
