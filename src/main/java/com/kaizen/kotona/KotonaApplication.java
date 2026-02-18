package com.kaizen.kotona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// exclude setting
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class KotonaApplication {
    public static void main(String[] args) {
        SpringApplication.run(KotonaApplication.class, args);
    }
}