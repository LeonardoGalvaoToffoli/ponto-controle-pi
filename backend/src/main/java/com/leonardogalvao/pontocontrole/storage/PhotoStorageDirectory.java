package com.leonardogalvao.pontocontrole.storage;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PhotoStorageDirectory implements InitializingBean {

    private final Path root;

    public PhotoStorageDirectory(PhotoStorageProperties properties) {
        this.root = properties.path().toAbsolutePath().normalize();
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        Files.createDirectories(root);
        if (!Files.isDirectory(root) || !Files.isReadable(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("O diretório privado de fotos não está acessível para leitura e escrita");
        }
    }

    public Path root() {
        return root;
    }
}
