package com.tuganire.storage;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Object storage abstraction. Implemented by S3StorageServiceImpl (when spring.cloud.aws.s3.enabled=true) and
 * LocalStorageServiceImpl (fallback).
 */
public interface StorageService {

    /**
     * @return true when backed by S3, false when backed by local disk.
     */
    boolean isEnabled();

    /**
     * Upload from a local file path. Returns the storage key/path of the stored object.
     */
    String uploadFile(Path localPath, String objectKey, String contentType);

    /**
     * Upload from an input stream. Returns the storage key/path of the stored object.
     */
    String uploadFile(InputStream inputStream, String objectKey, String contentType, long contentLength);

    /**
     * Download an object's bytes by key.
     */
    byte[] downloadFile(String objectKey);

    /**
     * Delete a single object.
     */
    void deleteFile(String objectKey);

    /**
     * Delete every object under the given logical folder/prefix.
     */
    void deleteFolder(String folderKey);

    /**
     * Build a key under the prefix: {prefix}/{folder}/{filename}.{extension} Pass null/empty folder to skip the folder
     * segment.
     */
    String buildObjectKey(String folder, String filename, String extension);
}
