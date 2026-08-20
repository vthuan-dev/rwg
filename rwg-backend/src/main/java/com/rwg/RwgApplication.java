package com.rwg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RWG Casino Platform backend - modular monolith.
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RwgApplication {

    public static void main(String[] args) {
        SpringApplication.run(RwgApplication.class, args);
    }
}
