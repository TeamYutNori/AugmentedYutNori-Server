package com.teamyutnori.yutnori;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AugmentedYutNoriServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AugmentedYutNoriServerApplication.class, args);
    }

}
