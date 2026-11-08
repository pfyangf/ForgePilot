package com.forgepilot.exec.agent;

public class AgentNotAvailableException extends RuntimeException {

    public static final String CODE = "NOT_AVAILABLE";

    private final String code;

    public AgentNotAvailableException(String message) {
        super(message);
        this.code = CODE;
    }

    public String code() {
        return code;
    }
}
