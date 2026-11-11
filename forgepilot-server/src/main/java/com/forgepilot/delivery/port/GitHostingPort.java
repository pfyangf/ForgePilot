package com.forgepilot.delivery.port;

import java.util.List;

/**
 * 平台自己的代码托管端口。一次统一交付会对每个有改动的仓库各调用一次。
 * 这些类型不是 GitLab 或 GitHub SDK 类型。
 */
public interface GitHostingPort {

    boolean supports(String providerType);

    RepositoryView getRepository(RepositoryRef ref);

    List<BranchView> listBranches(RepositoryRef ref);

    DiffView compare(RepositoryRef ref, String from, String to);

    /** 远端尚无该分支时从起点创建；已存在则返回当前 head。 */
    BranchView ensureBranch(EnsureBranch command);

    MergeRequestView createOrGetDraft(CreateDraft command);

    MergeRequestView getMergeRequest(MergeRequestRef ref);

    /**
     * 把源分支合并进目标分支。发布时使用；代码审核通过不会调用。
     * 默认实现抛出未支持，便于测试替身不必全部改写。
     */
    default MergeResult mergeBranch(MergeBranch command) {
        throw new UnsupportedOperationException("当前适配器不支持合并分支");
    }

    record RepositoryRef(String repositoryId, String providerType, String externalId, String cloneUrl) {
    }

    record RepositoryView(String repositoryId, String providerType, String externalId, String defaultBranch) {
    }

    record BranchView(String name, String headSha) {
    }

    record DiffView(String from, String to, String summary, List<String> changedPaths) {
    }

    record EnsureBranch(RepositoryRef repository, String branchName, String startPointSha) {
    }

    record CreateDraft(
            String taskId,
            RepositoryRef repository,
            String branchName,
            String headSha,
            String targetBranch,
            String title,
            String idempotencyKey) {
    }

    record MergeRequestRef(String repositoryId, String externalId) {
    }

    record MergeRequestView(
            String repositoryId,
            String externalId,
            String url,
            String branchName,
            String headSha,
            String idempotencyKey,
            String state) {
    }

    record MergeBranch(
            RepositoryRef repository, String sourceBranch, String targetBranch, String commitMessage) {}

    record MergeResult(
            String sourceBranch,
            String targetBranch,
            String mergeSha,
            boolean alreadyMerged,
            String message) {}
}
