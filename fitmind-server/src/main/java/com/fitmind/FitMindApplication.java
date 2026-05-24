package com.fitmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.fitmind.module.*.mapper")
public class FitMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitMindApplication.class, args);
    }

}
