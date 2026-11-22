package com.leonardogalvao.pontocontrole.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI pontoControleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ponto Controle API")
                .version("v1")
                .description("API local do MVP de controle de ponto."));
    }
}
