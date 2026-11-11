package com.forgepilot.delivery.adapter;

import com.forgepilot.delivery.adapter.github.GitHubGitHostingAdapter;
import com.forgepilot.delivery.adapter.gitlab.GitLabGitHostingAdapter;
import com.forgepilot.delivery.git.GitRemoteUrl;
import com.forgepilot.delivery.port.GitHostingPort;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 按仓库地址和 {@code providerType} 把调用路由到 GitLab 或 GitHub 适配器。
 * github.com 的 https / git@ 地址始终走 GitHub，即使登记时写成了 GITLAB。
 */
@Component
@Primary
public class RoutingGitHostingAdapter implements GitHostingPort {

    private final GitLabGitHostingAdapter gitLab;
    private final GitHubGitHostingAdapter gitHub;

    public RoutingGitHostingAdapter(GitLabGitHostingAdapter gitLab, GitHubGitHostingAdapter gitHub) {
        this.gitLab = gitLab;
        this.gitHub = gitHub;
    }

    @Override
    public boolean supports(String providerType) {
        return gitLab.supports(providerType) || gitHub.supports(providerType);
    }

    public boolean supports(RepositoryRef ref) {
        try {
            adapterFor(ref);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public RepositoryView getRepository(RepositoryRef ref) {
        return adapterFor(ref).getRepository(ref);
    }

    @Override
    public List<BranchView> listBranches(RepositoryRef ref) {
        return adapterFor(ref).listBranches(ref);
    }

    @Override
    public DiffView compare(RepositoryRef ref, String from, String to) {
        return adapterFor(ref).compare(ref, from, to);
    }

    @Override
    public BranchView ensureBranch(EnsureBranch command) {
        return adapterFor(command.repository()).ensureBranch(command);
    }

    @Override
    public MergeRequestView createOrGetDraft(CreateDraft command) {
        return adapterFor(command.repository()).createOrGetDraft(command);
    }

    @Override
    public MergeRequestView getMergeRequest(MergeRequestRef ref) {
        throw new UnsupportedOperationException("getMergeRequest 需要完整 RepositoryRef，请使用对应适配器的重载方法");
    }

    GitHostingPort adapterFor(RepositoryRef ref) {
        if (ref == null) {
            throw new IllegalStateException("缺少仓库引用");
        }
        if (GitRemoteUrl.isGitHub(ref.cloneUrl()) || gitHub.supports(ref.providerType())) {
            return gitHub;
        }
        if (gitLab.supports(ref.providerType())
                || ref.providerType() == null
                || ref.providerType().isBlank()) {
            return gitLab;
        }
        throw new IllegalStateException("不支持的托管类型: " + ref.providerType());
    }
}
