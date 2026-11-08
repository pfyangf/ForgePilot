package com.forgepilot.exec.model;

/** 保留占位名；正式 Bean 为 {@link InMemoryModelSessionProxy}。 */
public class StubModelSessionProxy implements ModelSessionProxy {

    @Override
    public String exposedWireApi() {
        return InMemoryModelSessionProxy.WIRE_API;
    }

    @Override
    public ModelSessionCredential issue(ModelSessionRequest request) {
        throw new UnsupportedOperationException("请使用 InMemoryModelSessionProxy");
    }

    @Override
    public void revoke(String sessionId) {
        throw new UnsupportedOperationException("请使用 InMemoryModelSessionProxy");
    }
}
