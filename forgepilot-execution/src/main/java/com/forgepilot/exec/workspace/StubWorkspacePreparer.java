package com.forgepilot.exec.workspace;

import org.springframework.stereotype.Component;

@Component
public class StubWorkspacePreparer implements WorkspacePreparer {

    @Override
    public void prepare(String taskId) {
        throw new UnsupportedOperationException("多仓库工作区准备尚未实现");
    }
}
