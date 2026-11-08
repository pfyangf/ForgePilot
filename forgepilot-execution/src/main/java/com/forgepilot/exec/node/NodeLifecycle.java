package com.forgepilot.exec.node;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NodeLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NodeLifecycle.class);

    private final NodeRegistrationPort registrationPort;
    private final String nodeKey;
    private final int capacity;
    private final AtomicInteger activeSessions = new AtomicInteger();

    public NodeLifecycle(
            NodeRegistrationPort registrationPort,
            @Value("${forgepilot.node.key:local-exec-1}") String nodeKey,
            @Value("${forgepilot.node.capacity:4}") int capacity) {
        this.registrationPort = registrationPort;
        this.nodeKey = nodeKey;
        this.capacity = capacity;
    }

    @PostConstruct
    public void start() {
        try {
            registrationPort.register(nodeKey, capacity);
            Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "node-heartbeat");
                        t.setDaemon(true);
                        return t;
                    })
                    .scheduleAtFixedRate(this::beat, 5, 15, TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            log.warn("节点注册失败（控制面可能未启动）: {}", ex.getMessage());
        }
    }

    private void beat() {
        try {
            registrationPort.heartbeat(nodeKey, activeSessions.get());
        } catch (RuntimeException ex) {
            log.debug("心跳失败: {}", ex.getMessage());
        }
    }

    public void sessionStarted() {
        activeSessions.incrementAndGet();
    }

    public void sessionEnded() {
        activeSessions.updateAndGet(v -> Math.max(0, v - 1));
    }

    public String nodeKey() {
        return nodeKey;
    }
}
