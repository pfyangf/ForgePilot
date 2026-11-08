package com.forgepilot.execution.stream;

import java.util.List;

/**
 * 高频 Session 日志流。优先 Redis Stream；不可用时内存回退。
 * 禁止把高频 token 写入 RabbitMQ；重要事件仍走 agent_event。
 */
public interface SessionLogStream {

    /** @return 单调序号 */
    long append(String sessionId, String chunk);

    List<LogChunk> readAfter(String sessionId, long afterSeq, int limit);

    record LogChunk(long seq, String body) {
    }
}
