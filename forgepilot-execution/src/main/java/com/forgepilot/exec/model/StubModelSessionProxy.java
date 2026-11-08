package com.forgepilot.exec.model;

import org.springframework.stereotype.Component;

@Component
public class StubModelSessionProxy implements ModelSessionProxy {

    public static final String WIRE_API = "responses";

    @Override
    public String exposedWireApi() {
        return WIRE_API;
    }

    @Override
    public ModelSessionCredential issue(ModelSessionRequest request) {
        throw unsupported();
    }

    @Override
    public void revoke(String sessionId) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("模型会话代理尚未实现，不会签发令牌");
    }
}
