package com.forgepilot.system.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MinIO 实现。默认关闭（forgepilot.minio.enabled=false），单元测试走 {@link InMemoryObjectStorage}。
 */
@Component
@ConditionalOnProperty(name = "forgepilot.minio.enabled", havingValue = "true")
public class MinioObjectStorage implements ObjectStoragePort {

    private final MinioClient client;

    public MinioObjectStorage(
            @Value("${forgepilot.minio.endpoint:http://localhost:19000}") String endpoint,
            @Value("${forgepilot.minio.access-key:forgepilot}") String accessKey,
            @Value("${forgepilot.minio.secret-key:forgepilot-dev}") String secretKey) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Override
    public PutResult put(String bucket, String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket(bucket);
            byte[] data = content == null ? new byte[0] : content;
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return new PutResult(bucket, objectKey, true, data.length);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO put failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        try {
            try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                return Optional.of(stream.readAllBytes());
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return get(bucket, objectKey).isPresent();
    }

    @Override
    public List<String> listKeys(String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix == null ? "" : prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                keys.add(result.get().objectName());
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
