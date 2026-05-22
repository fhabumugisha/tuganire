package com.tuganire.storage;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsS3BucketProperties.class)
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3StorageServiceImpl implements StorageService {

    private final S3Template s3Template;
    private final AwsS3BucketProperties s3Properties;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String uploadFile(Path localPath, String objectKey, String contentType) {
        String fullKey = buildFullKey(objectKey);
        String bucket = s3Properties.getBucketName();
        try (InputStream in = Files.newInputStream(localPath)) {
            ObjectMetadata metadata = ObjectMetadata.builder().contentType(contentType).build();
            S3Resource resource = s3Template.upload(bucket, fullKey, in, metadata);
            log.info("Uploaded to S3: {}", resource.getLocation());
            return fullKey;
        } catch (IOException e) {
            throw new StorageException("Failed to read local file for S3 upload: " + localPath, e);
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to S3: " + objectKey, e);
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectKey, String contentType, long contentLength) {
        String fullKey = buildFullKey(objectKey);
        String bucket = s3Properties.getBucketName();
        try {
            ObjectMetadata metadata = ObjectMetadata.builder().contentType(contentType)
                    .contentLength(contentLength > 0 ? contentLength : null).build();
            S3Resource resource = s3Template.upload(bucket, fullKey, inputStream, metadata);
            log.info("Uploaded stream to S3: {} ({} bytes)", resource.getLocation(), contentLength);
            return fullKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload stream to S3: " + objectKey, e);
        }
    }

    @Override
    public byte[] downloadFile(String objectKey) {
        String fullKey = ensurePrefixed(objectKey);
        String bucket = s3Properties.getBucketName();
        try {
            S3Resource resource = s3Template.download(bucket, fullKey);
            return resource.getContentAsByteArray();
        } catch (IOException e) {
            throw new StorageException("Failed to download file from S3: " + objectKey, e);
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        String fullKey = ensurePrefixed(objectKey);
        try {
            s3Template.deleteObject(s3Properties.getBucketName(), fullKey);
            log.info("Deleted S3 object: {}", fullKey);
        } catch (Exception e) {
            throw new StorageException("Failed to delete S3 object: " + objectKey, e);
        }
    }

    @Override
    public void deleteFolder(String folderKey) {
        String prefix = ensurePrefixed(folderKey);
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String bucket = s3Properties.getBucketName();
        try {
            int count = 0;
            for (S3Resource resource : s3Template.listObjects(bucket, prefix)) {
                try {
                    String key = resource.getLocation().getObject();
                    s3Template.deleteObject(bucket, key);
                    count++;
                } catch (Exception e) {
                    log.warn("Failed to delete S3 object {}", resource.getFilename(), e);
                }
            }
            log.info("Deleted {} S3 objects under {}", count, prefix);
        } catch (Exception e) {
            throw new StorageException("Failed to delete S3 folder: " + folderKey, e);
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

    private String buildFullKey(String objectKey) {
        String prefix = s3Properties.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return objectKey;
        }
        return prefix + "/" + objectKey;
    }

    private String ensurePrefixed(String objectKey) {
        String prefix = s3Properties.getPrefix();
        if (prefix == null || prefix.isBlank() || objectKey.startsWith(prefix + "/") || objectKey.equals(prefix)) {
            return objectKey;
        }
        return buildFullKey(objectKey);
    }
}
