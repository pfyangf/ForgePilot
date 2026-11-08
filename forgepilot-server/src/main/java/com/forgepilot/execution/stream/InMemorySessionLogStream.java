package com.forgepilot.execution.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class InMemorySessionLogStream implements SessionLogStream {

    private final ConcurrentHashMap<String, List<LogChunk>> streams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> seqs = new ConcurrentHashMap<>();

    @Override
    public long append(String sessionId, String chunk) {
        AtomicLong seq = seqs.computeIfAbsent(sessionId, id -> new AtomicLong());
        long next = seq.incrementAndGet();
        streams.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(new LogChunk(next, chunk == null ? "" : chunk));
        return next;
    }

    @Override
    public List<LogChunk> readAfter(String sessionId, long afterSeq, int limit) {
        List<LogChunk> all = streams.getOrDefault(sessionId, List.of());
        List<LogChunk> out = new ArrayList<>();
        for (LogChunk chunk : all) {
            if (chunk.seq() > afterSeq) {
                out.add(chunk);
                if (out.size() >= Math.max(1, limit)) {
                    break;
                }
            }
        }
        return out;
    }
}
