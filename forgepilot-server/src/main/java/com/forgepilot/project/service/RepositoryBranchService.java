package com.forgepilot.project.service;

import com.forgepilot.delivery.port.GitHostingPort;
import com.forgepilot.delivery.port.GitHostingPort.BranchView;
import com.forgepilot.delivery.port.GitHostingPort.RepositoryRef;
import com.forgepilot.project.domain.ProjectRepository;
import com.forgepilot.project.persistence.ProjectRepositoryMapper;
import com.forgepilot.system.auth.AuthUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepositoryBranchService {

    private final ProjectAccess projectAccess;
    private final ProjectRepositoryMapper projectRepositoryMapper;
    private final GitHostingPort gitHostingPort;

    public RepositoryBranchService(
            ProjectAccess projectAccess,
            ProjectRepositoryMapper projectRepositoryMapper,
            GitHostingPort gitHostingPort) {
        this.projectAccess = projectAccess;
        this.projectRepositoryMapper = projectRepositoryMapper;
        this.gitHostingPort = gitHostingPort;
    }

    public BranchListView list(AuthUser user, String projectId, String repositoryId) {
        projectAccess.requireProjectMember(user, projectId);
        ProjectRepository repo = requireRepo(projectId, repositoryId);
        return list(repo);
    }

    public BranchListView list(ProjectRepository repo) {
        String defaultBranch = repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank()
                ? "main"
                : repo.getDefaultBranch();
        try {
            List<BranchView> branches = gitHostingPort.listBranches(toRef(repo));
            return new BranchListView(
                    repo.getId(),
                    defaultBranch,
                    branches.stream().map(item -> new BranchItem(item.name(), item.headSha())).toList(),
                    null);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "无法列出远端分支，请手工填写分支名"
                    : "无法列出远端分支：" + ex.getMessage() + "。请手工填写分支名。";
            return new BranchListView(repo.getId(), defaultBranch, List.of(), message);
        }
    }

    public static RepositoryRef toRef(ProjectRepository repo) {
        return new RepositoryRef(repo.getId(), repo.getProviderType(), repo.getExternalId(), repo.getCloneUrl());
    }

    private ProjectRepository requireRepo(String projectId, String repositoryId) {
        ProjectRepository repo = projectRepositoryMapper.selectById(repositoryId);
        if (repo == null || !projectId.equals(repo.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在或不属于该项目");
        }
        return repo;
    }

    public record BranchItem(String name, String headSha) {}

    public record BranchListView(String projectRepositoryId, String defaultBranch, List<BranchItem> branches, String error) {}
}
