package com.forgepilot.delivery.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 按任务冻结的仓库集合检出到同一工作区。不使用实时项目仓库列表，也不使用 Git Worktree。
 */
@Component
public class MultiRepoWorkspaceCheckout {

    private final LocalGitSupport git;
    private final TaskWorkspaceLayout layout = new TaskWorkspaceLayout();

    public MultiRepoWorkspaceCheckout(LocalGitSupport git) {
        this.git = git;
    }

    public PreparedWorkspace prepare(
            String storageRoot, String taskId, List<FrozenRepo> frozenRepos, Function<FrozenRepo, String> askPassTokens) {
        String taskRoot = layout.taskRoot(storageRoot, taskId);
        Path root = Path.of(taskRoot);
        try {
            Files.createDirectories(root);
            Files.createDirectories(Path.of(layout.agentHome(taskRoot)));
        } catch (Exception ex) {
            throw new IllegalStateException("创建任务工作区失败", ex);
        }
        List<PreparedCheckout> checkouts = new ArrayList<>();
        for (FrozenRepo repo : frozenRepos) {
            String askPassToken = askPassTokens == null ? null : askPassTokens.apply(repo);
            Path mirror = Path.of(layout.mirror(storageRoot, repo.projectRepositoryId()));
            git.mirrorFetch(mirror, repo.cloneUrl(), askPassToken);
            Path checkout = Path.of(layout.checkout(taskRoot, repo.localDirectoryName()));
            git.cloneCheckout(mirror, checkout, repo.cloneUrl(), repo.baseBranch(), askPassToken);
            String baseSha = repo.baseSha();
            if (baseSha == null || baseSha.isBlank()) {
                baseSha = git.revParse(checkout, "HEAD");
            }
            checkouts.add(new PreparedCheckout(
                    repo.projectRepositoryId(),
                    repo.localDirectoryName(),
                    repo.cloneUrl(),
                    repo.baseBranch(),
                    baseSha,
                    checkout,
                    repo.providerType(),
                    repo.externalId()));
        }
        return new PreparedWorkspace(taskId, root, checkouts);
    }

    public record FrozenRepo(
            String projectRepositoryId,
            String localDirectoryName,
            String cloneUrl,
            String baseBranch,
            String baseSha,
            String providerType,
            String externalId) {
    }

    public record PreparedCheckout(
            String projectRepositoryId,
            String localDirectoryName,
            String cloneUrl,
            String baseBranch,
            String baseSha,
            Path path,
            String providerType,
            String externalId) {
    }

    public record PreparedWorkspace(String taskId, Path root, List<PreparedCheckout> checkouts) {
    }
}
