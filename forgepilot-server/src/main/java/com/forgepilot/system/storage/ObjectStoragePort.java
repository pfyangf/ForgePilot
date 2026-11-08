package com.forgepilot.system.storage;

import java.util.List;
import java.util.Optional;

/** MinIO / S3 兼容对象存储端口。 */
public interface ObjectStoragePort {

    PutResult put(String bucket, String objectKey, byte[] content, String contentType);

    Optional<byte[]> get(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);

    List<String> listKeys(String bucket, String prefix);

    record PutResult(String bucket, String objectKey, boolean uploaded, long byteSize) {
    }
}
