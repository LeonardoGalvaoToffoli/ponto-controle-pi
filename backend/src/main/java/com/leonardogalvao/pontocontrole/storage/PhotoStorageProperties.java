package com.leonardogalvao.pontocontrole.storage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties("app.photo-storage")
public record PhotoStorageProperties(@NotNull Path path) {
}
