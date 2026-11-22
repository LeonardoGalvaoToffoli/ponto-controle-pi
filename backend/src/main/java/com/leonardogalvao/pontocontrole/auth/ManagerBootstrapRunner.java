package com.leonardogalvao.pontocontrole.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagerBootstrapRunner implements ApplicationRunner {

    private final ManagerBootstrapService managerBootstrapService;

    public ManagerBootstrapRunner(ManagerBootstrapService managerBootstrapService) {
        this.managerBootstrapService = managerBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        managerBootstrapService.bootstrap();
    }
}
