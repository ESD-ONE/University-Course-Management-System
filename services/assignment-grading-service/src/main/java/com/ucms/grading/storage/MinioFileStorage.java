package com.ucms.grading.storage;

import com.ucms.grading.exception.UpstreamException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO-backed storage (provisioned in docker-compose.infra.yml ahead of this service).
 * The bucket is created lazily on first write, so a fresh MinIO needs no manual setup.
 * Unreachable MinIO surfaces as 503, not 500.
 */
@Component
public class MinioFileStorage implements FileStorage {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioFileStorage(StorageProperties props) {
        this.client = MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey() == null ? "" : props.secretKey())
                .build();
        this.bucket = props.bucket();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(content, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception ex) {
            throw new UpstreamException("File storage is unavailable right now — try again shortly.");
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) throw new EntityNotFoundException("File not found");
            throw new UpstreamException("File storage returned an error.");
        } catch (Exception ex) {
            throw new UpstreamException("File storage is unavailable right now — try again shortly.");
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (bucketReady) return;
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        }
    }
}
