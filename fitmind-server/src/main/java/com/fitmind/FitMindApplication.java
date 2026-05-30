package com.fitmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableCaching
@MapperScan("com.fitmind.module.*.mapper")
public class FitMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitMindApplication.class, args);
    }

}
