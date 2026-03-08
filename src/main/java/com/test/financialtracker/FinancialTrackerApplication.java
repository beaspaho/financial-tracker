package com.test.financialtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinancialTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialTrackerApplication.class, args);
    }

}
