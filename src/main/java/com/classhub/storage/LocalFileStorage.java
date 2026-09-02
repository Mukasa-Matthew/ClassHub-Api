package com.classhub.storage;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;

    public LocalFileStorage(@Value("${classhub.storage.path}") String storagePath) {
        try {
            this.root = Path.of(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize local file storage", ex);
        }
    }

    @Override
    public void store(String storageKey, InputStream content, long contentLength) {
        Path target = resolveInsideRoot(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to store file key={}", storageKey, ex);
            throw storageError("Failed to store attachment");
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path target = resolveInsideRoot(storageKey);
        try {
            if (!Files.isRegularFile(target)) {
                throw new ApplicationException(
                        ErrorCodes.ATTACHMENT_NOT_FOUND,
                        "Attachment not found",
                        HttpStatus.NOT_FOUND);
            }
            return Files.newInputStream(target);
        } catch (ApplicationException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to open file key={}", storageKey, ex);
            throw storageError("Failed to read attachment");
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveInsideRoot(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.error("Failed to delete file key={}", storageKey, ex);
            throw storageError("Failed to delete attachment");
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path target = resolveInsideRoot(storageKey);
        return Files.isRegularFile(target);
    }

    public Path root() {
        return root;
    }

    private Path resolveInsideRoot(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw storageError("Invalid storage key");
        }
        String normalized = storageKey.replace('\\', '/').trim();
        if (normalized.isEmpty() || normalized.startsWith("/")) {
            throw storageError("Invalid storage key");
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw storageError("Invalid storage key");
        }
        return resolved;
    }

    private static ApplicationException storageError(String message) {
        return new ApplicationException(
                ErrorCodes.ATTACHMENT_STORAGE_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
