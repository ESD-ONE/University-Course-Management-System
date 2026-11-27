package com.ucms.grading.storage;

import com.ucms.grading.exception.UpstreamException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The storage adapter against a real MinIO (the image production runs). Every other
 * test mocks FileStorage, so this is the only place a file is really stored and read
 * back. No Spring context: the adapter is built directly.
 */
@Testcontainers
class MinioFileStorageIT {

    private static final String ACCESS_KEY = "ucms_admin";
    private static final String SECRET_KEY = "it-only-secret";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            "cgr.dev/chainguard/minio@sha256:268d68a2a3b6fc65f905a61f84d7eab6345ce6bff14f551e2b028ec31e6a1341")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    private static MinioFileStorage storage(String endpoint, String bucket) {
        return new MinioFileStorage(new StorageProperties(endpoint, ACCESS_KEY, SECRET_KEY, bucket));
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String newBucket() {
        return "it-" + UUID.randomUUID();
    }

    @Test
    void aStoredFileComesBackByteForByte() throws Exception {
        MinioFileStorage storage = storage(endpoint(), newBucket());
        byte[] content = "PK... a student's submission".getBytes(StandardCharsets.UTF_8);

        storage.put("submissions/essay.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        try (InputStream stored = storage.get("submissions/essay.zip")) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void theBucketIsCreatedOnTheFirstWrite() throws Exception {
        // A fresh MinIO has no buckets at all, and this name has never been used.
        MinioFileStorage storage = storage(endpoint(), newBucket());
        byte[] content = {1, 2, 3};

        storage.put("first", new ByteArrayInputStream(content), content.length, null);

        try (InputStream stored = storage.get("first")) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void aMissingFile_isNotFound() {
        MinioFileStorage storage = storage(endpoint(), newBucket());
        storage.put("exists", new ByteArrayInputStream(new byte[] {1}), 1, null);

        assertThatThrownBy(() -> storage.get("does-not-exist")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void anUnreachableStore_isReportedAsUnavailable() {
        MinioFileStorage storage = storage("http://localhost:1", newBucket());

        assertThatThrownBy(() -> storage.put("any", new ByteArrayInputStream(new byte[] {1}), 1, null))
                .isInstanceOf(UpstreamException.class);
    }
}
