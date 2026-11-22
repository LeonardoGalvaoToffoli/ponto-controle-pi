package com.leonardogalvao.pontocontrole.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("app.workplace")
public record WorkplaceProperties(
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal radiusMeters,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("50") BigDecimal maxAccuracyMeters) {
}
