package com.leonardogalvao.pontocontrole.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component("photoStorage")
public class PhotoStorageHealthIndicator implements HealthIndicator {

    private final PhotoStorageDirectory storageDirectory;

    public PhotoStorageHealthIndicator(PhotoStorageDirectory storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    @Override
    public Health health() {
        Path probe = null;
        try {
            Path root = storageDirectory.root();
            if (!Files.isDirectory(root) || !Files.isReadable(root) || !Files.isWritable(root)) {
                return Health.down().withDetail("writable", false).build();
            }

            probe = Files.createTempFile(root, ".health-", ".tmp");
            return Health.up().withDetail("writable", true).build();
        } catch (Exception exception) {
            return Health.down().withException(exception).build();
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (Exception ignored) {
                    // A próxima verificação continuará reportando a capacidade real do volume.
                }
            }
        }
    }
}
