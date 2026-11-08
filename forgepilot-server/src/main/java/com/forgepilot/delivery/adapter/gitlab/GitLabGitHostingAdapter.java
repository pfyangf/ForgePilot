package com.forgepilot.delivery.adapter.gitlab;

import com.forgepilot.delivery.port.GitHostingPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * GitLab 适配器占位。不引入 GitLab SDK，也不在领域模型里保存厂商类型。
 */
@Component
public class GitLabGitHostingAdapter implements GitHostingPort {

    public static final String PROVIDER_TYPE = "gitlab";

    public String providerType() {
        return PROVIDER_TYPE;
    }

    @Override
    public RepositoryView getRepository(RepositoryRef ref) {
        throw unsupported();
    }

    @Override
    public List<BranchView> listBranches(RepositoryRef ref) {
        throw unsupported();
    }

    @Override
    public DiffView compare(RepositoryRef ref, String from, String to) {
        throw unsupported();
    }

    @Override
    public MergeRequestView createOrGetDraft(CreateDraft command) {
        throw unsupported();
    }

    @Override
    public MergeRequestView getMergeRequest(MergeRequestRef ref) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("GitLab 适配器尚未实现");
    }
}
