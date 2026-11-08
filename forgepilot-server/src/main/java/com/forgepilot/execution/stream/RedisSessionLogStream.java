package com.forgepilot.execution.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisSessionLogStream {

    private final StringRedisTemplate redis;

    public RedisSessionLogStream(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long append(String sessionId, String chunk) {
        String key = key(sessionId);
        var id = redis.opsForStream().add(key, Map.of("body", chunk == null ? "" : chunk));
        redis.opsForStream().trim(key, 5000);
        return parseSeq(Objects.requireNonNull(id).getValue());
    }

    public List<SessionLogStream.LogChunk> readAfter(String sessionId, long afterSeq, int limit) {
        String key = key(sessionId);
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .read(
                        StreamReadOptions.empty().count(5000L),
                        StreamOffset.create(key, ReadOffset.from("0-0")));
        List<SessionLogStream.LogChunk> out = new ArrayList<>();
        if (records == null) {
            return out;
        }
        for (MapRecord<String, Object, Object> record : records) {
            long seq = parseSeq(record.getId().getValue());
            if (seq > afterSeq) {
                Object body = record.getValue().get("body");
                out.add(new SessionLogStream.LogChunk(seq, body == null ? "" : body.toString()));
                if (out.size() >= Math.max(1, limit)) {
                    break;
                }
            }
        }
        return out;
    }

    public boolean available() {
        try {
            var factory = redis.getConnectionFactory();
            if (factory == null) {
                return false;
            }
            String pong = factory.getConnection().ping();
            return pong != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String key(String sessionId) {
        return "fp:session:logs:" + sessionId;
    }

    private static long parseSeq(String id) {
        try {
            int dash = id.indexOf('-');
            if (dash > 0) {
                return Long.parseLong(id.substring(0, dash));
            }
            return Long.parseLong(id.replace("-", ""));
        } catch (Exception ex) {
            return Math.abs((long) id.hashCode());
        }
    }
}
