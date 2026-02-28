package com.powergrid.forecasting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PowerGridForecastingApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerGridForecastingApplication.class, args);
    }
}
