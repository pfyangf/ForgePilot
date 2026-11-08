package com.forgepilot.delivery.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiRepoWorkspaceCheckoutTest {

    @TempDir
    Path temp;

    @Test
    void checksOutFrozenReposUsingMirrorReferenceNotWorktree() throws Exception {
        Path remoteA = initBareWithCommit(temp.resolve("remote-a.git"), "a.txt", "A");
        Path remoteB = initBareWithCommit(temp.resolve("remote-b.git"), "b.txt", "B");
        LocalGitSupport git = new LocalGitSupport();
        MultiRepoWorkspaceCheckout checkout = new MultiRepoWorkspaceCheckout(git);

        var prepared = checkout.prepare(
                temp.resolve("storage").toString(),
                "task1",
                List.of(
                        new MultiRepoWorkspaceCheckout.FrozenRepo(
                                "repoA", "api", remoteA.toUri().toString(), "master", null, "GITLAB", null),
                        new MultiRepoWorkspaceCheckout.FrozenRepo(
                                "repoB", "web", remoteB.toUri().toString(), "master", null, "GITLAB", null)),
                null);

        assertThat(prepared.root().resolve("api/a.txt")).exists();
        assertThat(prepared.root().resolve("web/b.txt")).exists();
        assertThat(prepared.root().resolve(".agent-home")).exists();
        assertThat(Files.exists(temp.resolve("storage/mirrors/repoA.git"))).isTrue();
        assertThat(prepared.checkouts()).hasSize(2);
        assertThat(prepared.checkouts().get(0).baseSha()).isNotBlank();
        // 不是 worktree：.git 应是目录或文件，但 checkout 路径下不应有指向共享 worktree 的 gitdir 复用问题——至少是独立 clone
        assertThat(prepared.root().resolve("api/.git")).exists();
    }

    private Path initBareWithCommit(Path bare, String file, String content) throws Exception {
        Path seed = temp.resolve(bare.getFileName().toString() + "-seed");
        Files.createDirectories(seed);
        LocalGitSupport git = new LocalGitSupport();
        git.run(List.of("git", "init", "-b", "master"), seed, null);
        Files.writeString(seed.resolve(file), content);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "add", "."), seed, null);
        git.run(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "init"), seed, null);
        Files.createDirectories(bare.getParent());
        git.run(List.of("git", "clone", "--bare", seed.toString(), bare.toString()), null, null);
        return bare;
    }
}
