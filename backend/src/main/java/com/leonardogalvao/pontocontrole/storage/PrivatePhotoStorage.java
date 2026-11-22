package com.leonardogalvao.pontocontrole.storage;

import com.leonardogalvao.pontocontrole.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Component
public class PrivatePhotoStorage {

    private static final long MAX_PHOTO_BYTES = 5L * 1024L * 1024L;

    private final Path root;

    public PrivatePhotoStorage(PhotoStorageDirectory directory) {
        this.root = directory.root();
    }

    public StoredPhoto storeProfilePhoto(UUID employeeId, MultipartFile photo) {
        byte[] content = validatedJpeg(photo);
        String key = "profiles/" + employeeId + ".jpg";
        Path destination = resolveKey(key);

        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), ".profile-", ".tmp");
            try {
                Files.write(temporary, content);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredPhoto(key);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PHOTO_STORAGE_ERROR",
                    "Não foi possível armazenar a foto com segurança.");
        }
    }

    public byte[] read(String key) {
        Path path = resolveKey(key);
        try {
            if (!Files.isRegularFile(path)) {
                throw photoNotFound();
            }
            return Files.readAllBytes(path);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw photoNotFound();
        }
    }

    public void deleteIfExists(String key) {
        try {
            Files.deleteIfExists(resolveKey(key));
        } catch (IOException ignored) {
            // A falha fica restrita ao armazenamento privado e pode ser reconciliada com a API parada.
        }
    }

    private byte[] validatedJpeg(MultipartFile photo) {
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES) {
            throw invalidPhoto();
        }
        String contentType = photo.getContentType();
        if (contentType == null || !"image/jpeg".equals(contentType.toLowerCase(Locale.ROOT))) {
            throw invalidPhoto();
        }

        try {
            byte[] content = photo.getBytes();
            if (content.length == 0 || content.length > MAX_PHOTO_BYTES || !isDecodableJpeg(content)) {
                throw invalidPhoto();
            }
            return content;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPhoto();
        }
    }

    private boolean isDecodableJpeg(byte[] content) {
        if (content.length < 4
                || Byte.toUnsignedInt(content[0]) != 0xff
                || Byte.toUnsignedInt(content[1]) != 0xd8) {
            return false;
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                    return false;
                }
                reader.setInput(input, true, true);
                return reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private Path resolveKey(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw photoNotFound();
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private ApiException invalidPhoto() {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_PHOTO",
                "A foto deve ser um JPEG válido de até 5 MiB.");
    }

    private ApiException photoNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PHOTO_NOT_FOUND",
                "Foto não encontrada.");
    }

    public record StoredPhoto(String key) {
    }
}
