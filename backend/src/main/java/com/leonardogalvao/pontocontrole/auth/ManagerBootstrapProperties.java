package com.leonardogalvao.pontocontrole.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-manager")
public final class ManagerBootstrapProperties {

    private final String email;
    private final String password;

    public ManagerBootstrapProperties(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }
}
