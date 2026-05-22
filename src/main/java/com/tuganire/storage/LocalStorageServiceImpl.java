package com.tuganire.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Fallback implementation that stores files on the local filesystem. Active when spring.cloud.aws.s3.enabled is false
 * or missing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(LocalStorageProperties.class)
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private final LocalStorageProperties properties;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String uploadFile(Path localPath, String objectKey, String contentType) {
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(localPath, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved file to local storage: {}", target);
            return target.toString();
        } catch (IOException e) {
            throw new StorageException("Failed to copy file to local storage: " + objectKey, e);
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectKey, String contentType, long contentLength) {
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved stream to local storage: {}", target);
            return target.toString();
        } catch (IOException e) {
            throw new StorageException("Failed to save stream to local storage: " + objectKey, e);
        }
    }

    @Override
    public byte[] downloadFile(String objectKey) {
        try {
            Path direct = Path.of(objectKey);
            Path path = Files.exists(direct) ? direct : resolve(objectKey);
            if (!Files.exists(path)) {
                throw new StorageException("File not found: " + objectKey);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new StorageException("Failed to read file from local storage: " + objectKey, e);
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            Path direct = Path.of(objectKey);
            Path path = Files.exists(direct) ? direct : resolve(objectKey);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Deleted local file: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to delete local file {}", objectKey, e);
        }
    }

    @Override
    public void deleteFolder(String folderKey) {
        Path dir = resolve(folderKey);
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", p, e);
                }
            });
            log.info("Deleted local folder: {}", dir);
        } catch (IOException e) {
            log.warn("Failed to walk folder {}", dir, e);
        }
    }

    @Override
    public String buildObjectKey(String folder, String filename, String extension) {
        StringBuilder sb = new StringBuilder();
        if (folder != null && !folder.isBlank()) {
            sb.append(folder).append('/');
        }
        sb.append(filename);
        if (extension != null && !extension.isBlank()) {
            sb.append('.').append(extension);
        }
        return sb.toString();
    }

    private Path resolve(String objectKey) {
        return Path.of(properties.getDirectory(), objectKey);
    }
}
