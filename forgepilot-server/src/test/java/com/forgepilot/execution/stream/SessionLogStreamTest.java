package com.forgepilot.execution.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SessionLogStreamTest {

    @Test
    void memoryFallbackAppendAndRead() {
        InMemorySessionLogStream memory = new InMemorySessionLogStream();
        ObjectProvider<RedisSessionLogStream> none = new ObjectProvider<>() {
            @Override
            public RedisSessionLogStream getObject() {
                return null;
            }

            @Override
            public RedisSessionLogStream getIfAvailable() {
                return null;
            }

            @Override
            public RedisSessionLogStream getIfUnique() {
                return null;
            }
        };
        SessionLogStreamFacade facade = new SessionLogStreamFacade(memory, none);
        long s1 = facade.append("sess", "hello");
        long s2 = facade.append("sess", "world");
        assertThat(s2).isGreaterThan(s1);
        assertThat(facade.readAfter("sess", 0, 10)).extracting(SessionLogStream.LogChunk::body)
                .containsExactly("hello", "world");
        assertThat(facade.readAfter("sess", s1, 10)).extracting(SessionLogStream.LogChunk::body)
                .containsExactly("world");
    }
}
