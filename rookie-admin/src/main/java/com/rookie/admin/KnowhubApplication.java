package com.rookie.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.rookie","com.knowhub"})
//@MapperScan("com.rookie.**.mapper")
public class KnowhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowhubApplication.class, args);
    }

}
