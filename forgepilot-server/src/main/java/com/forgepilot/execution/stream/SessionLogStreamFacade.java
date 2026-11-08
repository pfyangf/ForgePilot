package com.forgepilot.execution.stream;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class SessionLogStreamFacade implements SessionLogStream {

    private static final Logger log = LoggerFactory.getLogger(SessionLogStreamFacade.class);

    private final InMemorySessionLogStream memory;
    private final ObjectProvider<RedisSessionLogStream> redisProvider;

    public SessionLogStreamFacade(InMemorySessionLogStream memory, ObjectProvider<RedisSessionLogStream> redisProvider) {
        this.memory = memory;
        this.redisProvider = redisProvider;
    }

    @Override
    public long append(String sessionId, String chunk) {
        Optional<RedisSessionLogStream> redis = Optional.ofNullable(redisProvider.getIfAvailable());
        if (redis.isPresent() && redis.get().available()) {
            try {
                return redis.get().append(sessionId, chunk);
            } catch (RuntimeException ex) {
                log.debug("Redis Stream append failed, falling back to memory: {}", ex.getMessage());
            }
        }
        return memory.append(sessionId, chunk);
    }

    @Override
    public List<LogChunk> readAfter(String sessionId, long afterSeq, int limit) {
        Optional<RedisSessionLogStream> redis = Optional.ofNullable(redisProvider.getIfAvailable());
        if (redis.isPresent() && redis.get().available()) {
            try {
                return redis.get().readAfter(sessionId, afterSeq, limit);
            } catch (RuntimeException ex) {
                log.debug("Redis Stream read failed, falling back to memory: {}", ex.getMessage());
            }
        }
        return memory.readAfter(sessionId, afterSeq, limit);
    }
}
