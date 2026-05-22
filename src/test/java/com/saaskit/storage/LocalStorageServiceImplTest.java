package com.tuganire.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceImplTest {

    private LocalStorageServiceImpl storage;
    private LocalStorageProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new LocalStorageProperties();
        properties.setDirectory(tempDir.toString());
        storage = new LocalStorageServiceImpl(properties);
    }

    @Test
    void isEnabledReturnsFalse() {
        assertThat(storage.isEnabled()).isFalse();
    }

    @Test
    void buildObjectKeyWithFolderAndExtension() {
        String key = storage.buildObjectKey("articles", "abc123", "png");
        assertThat(key).isEqualTo("articles/abc123.png");
    }

    @Test
    void buildObjectKeyWithoutFolder() {
        String key = storage.buildObjectKey(null, "abc123", "png");
        assertThat(key).isEqualTo("abc123.png");
    }

    @Test
    void buildObjectKeyWithoutExtension() {
        String key = storage.buildObjectKey("articles", "abc123", null);
        assertThat(key).isEqualTo("articles/abc123");
    }

    @Test
    void uploadFileFromPathRoundtripsBytes() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.txt"), "hello world");

        String returnedKey = storage.uploadFile(source, "uploads/hello.txt", "text/plain");

        Path expected = tempDir.resolve("uploads/hello.txt");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(returnedKey).isEqualTo(expected.toString());
        assertThat(storage.downloadFile("uploads/hello.txt")).isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadFileFromStreamCreatesNestedDirectories() {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        storage.uploadFile(new ByteArrayInputStream(payload), "deep/nested/key.bin", "application/octet-stream",
                payload.length);

        assertThat(storage.downloadFile("deep/nested/key.bin")).isEqualTo(payload);
    }

    @Test
    void downloadMissingFileThrows() {
        assertThatThrownBy(() -> storage.downloadFile("does/not/exist.bin")).isInstanceOf(StorageException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void deleteFileRemovesObject() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        storage.uploadFile(new ByteArrayInputStream(payload), "to-delete.bin", "application/octet-stream",
                payload.length);

        storage.deleteFile("to-delete.bin");

        assertThatThrownBy(() -> storage.downloadFile("to-delete.bin")).isInstanceOf(StorageException.class);
    }

    @Test
    void deleteFolderRemovesEntireSubtree() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        storage.uploadFile(new ByteArrayInputStream(payload), "tree/a.bin", "application/octet-stream", payload.length);
        storage.uploadFile(new ByteArrayInputStream(payload), "tree/sub/b.bin", "application/octet-stream",
                payload.length);

        storage.deleteFolder("tree");

        assertThat(Files.exists(tempDir.resolve("tree"))).isFalse();
    }

    @Test
    void deleteFileSilentlyIgnoresMissingObject() {
        storage.deleteFile("never-existed.bin");
    }
}
