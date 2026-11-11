package com.forgepilot.system.adapter;

import com.forgepilot.system.port.ModelProviderPort;

public class StubModelProvider implements ModelProviderPort {

    @Override
    public ModelProviderView get(String providerId) {
        throw unsupported();
    }

    @Override
    public String complete(PlatformModelRequest request) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("模型 Provider 端口尚未实现，不会读取或下发长期密钥");
    }
}
