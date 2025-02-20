package com.rookie.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.rookie"})
public class RookieApplication {

    public static void main(String[] args) {
        SpringApplication.run(RookieApplication.class, args);
    }

}
