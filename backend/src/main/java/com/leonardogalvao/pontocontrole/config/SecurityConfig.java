package com.leonardogalvao.pontocontrole.config;

import com.leonardogalvao.pontocontrole.auth.AuthConfiguration;
import com.leonardogalvao.pontocontrole.web.JsonAccessDeniedHandler;
import com.leonardogalvao.pontocontrole.web.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            Converter<Jwt, ? extends AbstractOAuth2TokenAuthenticationToken<Jwt>> jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password")
                        .hasAuthority(AuthConfiguration.AUTHORITY_PASSWORD_CHANGE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/me")
                        .hasAuthority(AuthConfiguration.AUTHORITY_ACCESS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/employees")
                        .hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/employees")
                        .hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/employees/*/photo")
                        .hasAuthority(AuthConfiguration.AUTHORITY_ACCESS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/attendance")
                        .hasRole("FUNCIONARIO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/attendance", "/api/v1/attendance/*/photo")
                        .hasAuthority(AuthConfiguration.AUTHORITY_ACCESS)
                        .anyRequest()
                        .hasAuthority(AuthConfiguration.AUTHORITY_ACCESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
