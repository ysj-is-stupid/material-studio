package com.example.materialstudio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.materialstudio.mapper")
@EnableScheduling
public class MaterialStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(MaterialStudioApplication.class, args);
    }
}
