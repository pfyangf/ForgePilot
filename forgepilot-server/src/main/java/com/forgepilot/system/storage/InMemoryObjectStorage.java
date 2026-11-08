package com.forgepilot.system.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class InMemoryObjectStorage implements ObjectStoragePort {

    private final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();

    private static String k(String bucket, String key) {
        return bucket + "::" + key;
    }

    @Override
    public PutResult put(String bucket, String objectKey, byte[] content, String contentType) {
        byte[] data = content == null ? new byte[0] : content;
        objects.put(k(bucket, objectKey), data);
        return new PutResult(bucket, objectKey, true, data.length);
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        return Optional.ofNullable(objects.get(k(bucket, objectKey)));
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return objects.containsKey(k(bucket, objectKey));
    }

    @Override
    public List<String> listKeys(String bucket, String prefix) {
        String p = bucket + "::" + (prefix == null ? "" : prefix);
        List<String> keys = new ArrayList<>();
        for (String key : objects.keySet()) {
            if (key.startsWith(bucket + "::") && key.substring((bucket + "::").length()).startsWith(prefix == null ? "" : prefix)) {
                keys.add(key.substring((bucket + "::").length()));
            }
        }
        return keys;
    }

    public void clear() {
        objects.clear();
    }
}
