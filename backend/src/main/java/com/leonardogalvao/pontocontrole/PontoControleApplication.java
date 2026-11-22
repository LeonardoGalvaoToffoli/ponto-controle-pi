package com.leonardogalvao.pontocontrole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PontoControleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PontoControleApplication.class, args);
    }
}
